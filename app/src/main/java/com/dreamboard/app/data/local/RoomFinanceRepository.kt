package com.dreamboard.app.data.local

import androidx.room.withTransaction
import com.dreamboard.app.core.finance.GoalAssumptions
import com.dreamboard.app.core.importer.Dedupe
import com.dreamboard.app.core.money.kes
import com.dreamboard.app.data.demo.DemoFinanceRepository
import com.dreamboard.app.di.ApplicationScope
import com.dreamboard.app.domain.model.Account
import com.dreamboard.app.domain.model.AccountKind
import com.dreamboard.app.domain.model.Asset
import com.dreamboard.app.domain.model.AssetCategory
import com.dreamboard.app.domain.model.BudgetCategory
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.DebtCategory
import com.dreamboard.app.domain.model.DebtKind
import com.dreamboard.app.domain.model.DebtPayment
import com.dreamboard.app.domain.model.ImportBatch
import com.dreamboard.app.domain.model.Milestone
import com.dreamboard.app.domain.model.MilestoneStatus
import com.dreamboard.app.domain.model.MonthSummary
import com.dreamboard.app.domain.model.NetWorthSnapshot
import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.TransactionSource
import com.dreamboard.app.domain.model.TransactionSplit
import com.dreamboard.app.domain.model.TransactionType
import com.dreamboard.app.domain.model.ValueType
import com.dreamboard.app.domain.repository.FinanceRepository
import com.dreamboard.app.domain.repository.SnapshotMethod
import com.dreamboard.app.domain.repository.ValueHistoryLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomFinanceRepository @Inject constructor(
    private val db: DreamboardDatabase,
    @ApplicationScope private val scope: CoroutineScope,
) : FinanceRepository {

    private val demo = DemoFinanceRepository()

    override val isDemoData: Boolean = false
    override val asOf: LocalDate get() = LocalDate.now()

    init {
        scope.launch(Dispatchers.IO) { seedDefaultsIfEmpty() }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(scope, SharingStarted.Eagerly, initial)

    override val accounts: StateFlow<List<Account>> = db.accounts().observe().map { l -> l.map { it.toDomain() } }.state(emptyList())

    override val assets: StateFlow<List<Asset>> = db.assets().observe().map { l -> l.map { it.toDomain() } }.state(emptyList())

    override val netWorthHistory: StateFlow<List<NetWorthSnapshot>> = db.snapshots().observe()
        .map { l -> l.map { NetWorthSnapshot(it.date, it.assetsMinor, it.liabilitiesMinor) } }.state(emptyList())

    override val debts: StateFlow<List<Debt>> = combine(db.liabilities().observe(), db.liabilities().observePayments()) { ls, ps ->
        val byDebt = ps.groupBy { it.liabilityId }
        ls.map { it.toDomain(byDebt[it.id].orEmpty()) }
    }.state(emptyList())

    override val goalAssumptions: StateFlow<GoalAssumptions> = db.plan().observeGoal(GOAL_ID)
        .map { it?.toDomain() ?: DEFAULT_GOAL }.state(DEFAULT_GOAL)

    override val milestones: StateFlow<List<Milestone>> = db.plan().observeMilestones()
        .map { l -> l.map { Milestone(it.id, it.year, it.title, it.description, enumOr(it.status, MilestoneStatus.UPCOMING)) } }
        .state(emptyList())

    override val budgetCategories: StateFlow<List<BudgetCategory>> = db.categories().observe()
        .map { l -> l.map { BudgetCategory(it.id, it.name, it.monthlyBudgetMinor) } }.state(emptyList())

    override val transactions: StateFlow<List<Transaction>> = combine(
        db.transactions().observe(), db.transactions().observeSplits(), db.accounts().observe(),
    ) { txs, splits, accts ->
        val names = accts.associate { it.id to it.name }
        val byTx = splits.groupBy { it.transactionId }
        txs.map { t ->
            Transaction(
                id = t.id, date = t.date, merchant = t.merchant, amountMinor = t.amountMinor,
                type = enumOr(t.type, TransactionType.EXPENSE), categoryId = t.categoryId,
                account = names[t.accountId] ?: "Unknown account",
                source = enumOr(t.source, TransactionSource.MANUAL), note = t.note,
                paidByCreditCard = t.paidByCreditCard, accountId = t.accountId,
                splits = byTx[t.id].orEmpty().map { TransactionSplit(it.categoryId, it.amountMinor) },
                importBatchId = t.importBatchId,
            )
        }
    }.state(emptyList())

    /** Completed months before the current one, from the first month with any transaction (max 5). */
    override val monthHistory: StateFlow<List<MonthSummary>> = combine(transactions, budgetCategories) { txs, cats ->
        val budget = cats.sumOf { it.monthlyBudgetMinor }
        val current = YearMonth.now()
        val first = txs.minOfOrNull { YearMonth.from(it.date) } ?: return@combine emptyList<MonthSummary>()
        (5 downTo 1).map { current.minusMonths(it.toLong()) }.filter { !it.isBefore(first) }.map { m ->
            val inMonth = txs.filter { YearMonth.from(it.date) == m }
            MonthSummary(m, budget, inMonth.sumOf { it.spendingEffect() }, inMonth.filter { it.type.countsAsIncome }.sumOf { it.amountMinor })
        }
    }.state(emptyList())

    override val monthlyNetIncomeMinor: StateFlow<Long> = transactions.map { txs ->
        val current = YearMonth.now()
        val months = (1..3).map { current.minusMonths(it.toLong()) }
        val totals = months.map { m -> txs.filter { it.type.countsAsIncome && YearMonth.from(it.date) == m }.sumOf { it.amountMinor } }
            .filter { it > 0 }
        if (totals.isEmpty()) 0L else totals.sum() / totals.size
    }.state(0L)

    override val importBatches: StateFlow<List<ImportBatch>> = db.transactions().observeBatches().map { l ->
        l.map { ImportBatch(it.id, it.fileName, it.importedAt, it.rowCount, it.importedCount, it.duplicateCount, it.errorCount, it.errorReport) }
    }.state(emptyList())

    // Ignite: labelled demonstration data until Phase 4.
    override val igniteIsDemo: Boolean = true
    override val igniteAsOf: LocalDate = DemoFinanceRepository.DEMO_AS_OF
    override val vehicles = demo.vehicles
    override val arrangements = demo.arrangements
    override val igniteMonths = demo.igniteMonths
    override val igniteAvailableCashMinor = demo.igniteAvailableCashMinor

    // ---- Writes ----

    override fun updateGoalAssumptions(transform: (GoalAssumptions) -> GoalAssumptions) {
        val next = transform(goalAssumptions.value)
        scope.launch(Dispatchers.IO) { db.plan().upsertGoal(next.toEntity()) }
    }

    override fun updateMilestone(milestone: Milestone) {
        scope.launch(Dispatchers.IO) {
            db.plan().upsertMilestones(listOf(MilestoneEntity(milestone.id, milestone.year, milestone.title, milestone.description, milestone.status.name, now())))
        }
    }

    override suspend fun saveAsset(asset: Asset, reason: String) = io {
        db.withTransaction {
            val old = db.assets().get(asset.id)
            val ts = now()
            db.assets().upsert(
                AssetEntity(asset.id, asset.name.trim(), asset.category.name, asset.institution.trim(), asset.valueMinor,
                    asset.valueType.name, asset.isGoalFund, old?.createdAt ?: ts, ts),
            )
            if (old == null || old.valueMinor != asset.valueMinor || old.valueType != asset.valueType.name) {
                db.assets().insertValuation(AssetValuationEntity(newId(), asset.id, LocalDate.now(), asset.valueMinor, asset.valueType.name,
                    if (old == null) "Opening value" else reason, ts))
                db.liabilities().insertAdjustment(BalanceAdjustmentEntity(newId(), "ASSET", asset.id, LocalDate.now(), old?.valueMinor, asset.valueMinor, reason, ts))
            }
        }
    }

    override suspend fun deleteAsset(id: String) = io {
        db.withTransaction {
            val old = db.assets().get(id)
            db.assets().delete(id)
            db.liabilities().insertAdjustment(BalanceAdjustmentEntity(newId(), "ASSET", id, LocalDate.now(), old?.valueMinor, null, "Deleted ${old?.name ?: ""}", now()))
        }
    }

    override suspend fun assetValuations(id: String): List<ValueHistoryLine> = io {
        db.assets().valuations(id).map { ValueHistoryLine(it.date, it.valueMinor, it.note) }
    }

    override suspend fun saveDebt(debt: Debt, reason: String) = io {
        db.withTransaction {
            val old = db.liabilities().get(debt.id)
            val ts = now()
            db.liabilities().upsert(debt.toEntity(old?.createdAt ?: ts, ts))
            if (old == null || old.balanceMinor != debt.balanceMinor) {
                db.liabilities().insertAdjustment(BalanceAdjustmentEntity(newId(), "LIABILITY", debt.id, LocalDate.now(), old?.balanceMinor, debt.balanceMinor,
                    if (old == null) "Opening balance" else reason, ts))
            }
        }
    }

    override suspend fun deleteDebt(id: String) = io {
        db.withTransaction {
            val old = db.liabilities().get(id)
            db.liabilities().delete(id)
            db.liabilities().insertAdjustment(BalanceAdjustmentEntity(newId(), "LIABILITY", id, LocalDate.now(), old?.balanceMinor, null, "Deleted ${old?.name ?: ""}", now()))
        }
    }

    override suspend fun recordDebtPayment(debtId: String, date: LocalDate, amountMinor: Long, interestMinor: Long, note: String) = io {
        db.withTransaction {
            val d = db.liabilities().get(debtId) ?: return@withTransaction
            val principal = (amountMinor - interestMinor).coerceAtLeast(0L)
            val newBalance = d.balanceMinor?.let { (it - principal).coerceAtLeast(0L) }
            val ts = now()
            db.liabilities().insertPayment(LiabilityPaymentEntity(newId(), debtId, date, amountMinor, principal, interestMinor, note, ts))
            db.liabilities().upsert(
                d.copy(balanceMinor = newBalance, interestPaidToDateMinor = (d.interestPaidToDateMinor ?: 0L) + interestMinor, updatedAt = ts),
            )
            db.liabilities().insertAdjustment(BalanceAdjustmentEntity(newId(), "LIABILITY", debtId, date, d.balanceMinor, newBalance, "Payment recorded", ts))
        }
    }

    override suspend fun saveAccount(account: Account) = io {
        val old = db.accounts().get(account.id)
        val ts = now()
        db.accounts().upsert(
            AccountEntity(account.id, account.name.trim(), account.kind.name, account.institution.trim(), account.openingBalanceMinor,
                account.openingDate, false, old?.createdAt ?: ts, ts),
        )
    }

    override suspend fun deleteAccount(id: String): Boolean = io {
        if (db.transactions().countForAccount(id) > 0) false else { db.accounts().delete(id); true }
    }

    override suspend fun saveCategory(category: BudgetCategory) = io {
        val existing = db.categories().all()
        val old = existing.firstOrNull { it.id == category.id }
        val ts = now()
        db.categories().upsert(
            CategoryEntity(category.id, category.name.trim(), old?.parentId, category.monthlyBudgetMinor,
                old?.sortOrder ?: ((existing.maxOfOrNull { it.sortOrder } ?: 0) + 1), false, old?.createdAt ?: ts, ts),
        )
    }

    override suspend fun saveTransaction(transaction: Transaction) = io {
        db.withTransaction {
            val ts = now()
            db.transactions().upsert(transaction.toEntity(Dedupe.hash(transaction), ts))
            db.transactions().deleteSplits(transaction.id)
            if (transaction.splits.isNotEmpty()) {
                db.transactions().insertSplits(transaction.splits.map { TransactionSplitEntity(newId(), transaction.id, it.categoryId, it.amountMinor) })
            }
        }
    }

    override suspend fun deleteTransaction(id: String) = io { db.transactions().delete(id) }

    override suspend fun existingHashCounts(hashes: Collection<String>): Map<String, Int> = io {
        hashes.distinct().chunked(500).flatMap { db.transactions().hashesIn(it) }.groupingBy { it }.eachCount()
    }

    override suspend fun importTransactions(
        batch: ImportBatch, transactions: List<Transaction>, hashes: List<String>, newCategories: List<BudgetCategory>,
    ) = io {
        db.withTransaction {
            val ts = now()
            val existing = db.categories().all()
            var order = existing.maxOfOrNull { it.sortOrder } ?: 0
            db.categories().upsertAll(newCategories.map { CategoryEntity(it.id, it.name, null, it.monthlyBudgetMinor, ++order, false, ts, ts) })
            db.transactions().insertBatch(
                ImportBatchEntity(batch.id, batch.fileName, if (batch.fileName.endsWith(".xlsx", true)) "XLSX" else "CSV", ts,
                    batch.rowCount, batch.importedCount, batch.duplicateCount, batch.errorCount, batch.errorReport),
            )
            db.transactions().insertAll(transactions.mapIndexed { i, t -> t.toEntity(hashes[i], ts) })
        }
    }

    override suspend fun recordSnapshot(method: SnapshotMethod) = io {
        val assets = db.assets().all().sumOf { it.valueMinor }
        val liabilities = db.liabilities().all().sumOf { it.balanceMinor ?: 0L }
        db.snapshots().put(SnapshotEntity(newId(), LocalDate.now(), assets, liabilities, method.name, now()))
    }

    override suspend fun ensureRecentSnapshot() {
        val hasData = io { db.assets().all().isNotEmpty() || db.liabilities().all().any { it.balanceMinor != null } }
        if (!hasData) return
        val latest = io { db.snapshots().latest() }
        if (latest == null || latest.date.isBefore(LocalDate.now().minusDays(6))) recordSnapshot(SnapshotMethod.AUTOMATIC)
    }

    override suspend fun loadExampleDebts() = io {
        if (db.liabilities().all().isNotEmpty()) return@io
        exampleDebts().forEach { saveDebt(it, "Example starting value (unverified)") }
    }

    override suspend fun eraseAllData() = io {
        db.clearAllTables()
        seedDefaultsIfEmpty()
    }

    // ---- Seeding ----

    /** Settings-like defaults only (categories, accounts, milestones, goal). No financial figures are invented. */
    private suspend fun seedDefaultsIfEmpty() {
        val ts = now()
        if (db.categories().count() == 0) {
            db.categories().upsertAll(DEFAULT_CATEGORIES.mapIndexed { i, (id, name, budget) -> CategoryEntity(id, name, null, budget, i, false, ts, ts) })
        }
        if (db.accounts().count() == 0) {
            db.accounts().upsert(AccountEntity("acct-bank", "Bank account", AccountKind.BANK.name, "", 0, LocalDate.now(), false, ts, ts))
            db.accounts().upsert(AccountEntity("acct-mpesa", "M-Pesa", AccountKind.MOBILE_MONEY.name, "Safaricom", 0, LocalDate.now(), false, ts, ts))
        }
        if (db.plan().milestoneCount() == 0) {
            db.plan().upsertMilestones(
                listOf(
                    MilestoneEntity("m1", 2027, "Project Zero Debt", "Clear every interest-bearing loan and card.", MilestoneStatus.IN_PROGRESS.name, ts),
                    MilestoneEntity("m2", 2028, "Financial Stability & Business Growth", "Six-month emergency fund; Ignite growing steadily.", MilestoneStatus.UPCOMING.name, ts),
                    MilestoneEntity("m3", 2030, "Wealth Acceleration", "Invest surplus consistently.", MilestoneStatus.UPCOMING.name, ts),
                    MilestoneEntity("m4", 2035, "Porsche Cayenne", "Buy the Cayenne in cash from the dedicated fund.", MilestoneStatus.UPCOMING.name, ts),
                ),
            )
        }
        if (db.plan().goalCount() == 0) db.plan().upsertGoal(DEFAULT_GOAL.toEntity())
    }

    private fun exampleDebts() = listOf(
        Debt("d-absa-loan", "ABSA Personal Loan", "ABSA Bank", DebtCategory.PERSONAL_LOAN, DebtKind.REDUCING_BALANCE,
            1_480_000.kes, null, 1650, 42_000.kes, null, null, 3, "Example balance from your brief. Rate and installment are assumptions: edit them."),
        Debt("d-sacco", "SACCO Loan", "SACCO", DebtCategory.SACCO_LOAN, DebtKind.REDUCING_BALANCE,
            666_000.kes, null, 1200, 25_000.kes, null, null, 4, "Example balance from your brief. Rate and installment are assumptions: edit them."),
        Debt("d-absa-card", "ABSA Credit Card", "ABSA Bank", DebtCategory.CREDIT_CARD, DebtKind.REVOLVING,
            200_000.kes, null, 3600, 12_000.kes, null, null, 1, "Revolving, interest-bearing. Rate and payment are assumptions: edit them."),
        Debt("d-equity-card", "Equity Credit Card", "Equity Bank", DebtCategory.CREDIT_CARD, DebtKind.STATEMENT_PAID_IN_FULL,
            140_000.kes, null, 0, 140_000.kes, null, 0L, 6, "Statement balance settled in full monthly; not revolving debt."),
        Debt("d-family", "Family Capital Restoration", "Family", DebtCategory.FAMILY_LOAN, DebtKind.INTEREST_FREE,
            200_000.kes, 200_000.kes, 0, 10_000.kes, null, 0L, 2, "Interest-free obligation. Monthly amount is an assumption."),
        Debt("d-salary-adv", "Salary Advance", "Employer", DebtCategory.SALARY_ADVANCE, DebtKind.INTEREST_FREE,
            null, null, 0, 0L, null, null, 5, "Balance unknown until you enter it. Excluded from totals."),
    )

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val GOAL_ID = "porsche"
        private fun now() = System.currentTimeMillis()
        fun newId(): String = UUID.randomUUID().toString()

        val DEFAULT_GOAL = GoalAssumptions(
            targetYearMonth = YearMonth.of(2035, 12),
            todayPriceMinor = 22_000_000.kes,
            currentFundMinor = 0,
            monthlyContributionMinor = 25_000.kes,
            annualReturnBps = 900,
            annualInflationBps = 500,
            insuranceRateBps = 450,
            annualMaintenanceMinor = 400_000.kes,
            annualFuelMinor = 540_000.kes,
        )

        val DEFAULT_CATEGORIES: List<Triple<String, String, Long>> = listOf(
            Triple("groceries", "Groceries", 45_000.kes),
            Triple("household", "Household", 18_000.kes),
            Triple("transport", "Fuel & transport", 28_000.kes),
            Triple("utilities", "Utilities", 9_500.kes),
            Triple("internet", "Internet & airtime", 6_500.kes),
            Triple("children", "Family & children", 22_000.kes),
            Triple("health", "Healthcare", 8_000.kes),
            Triple("education", "Education", 35_000.kes),
            Triple("dining", "Dining & entertainment", 12_000.kes),
            Triple("shopping", "Personal shopping", 10_000.kes),
            Triple("insurance", "Insurance", 7_500.kes),
            Triple("subscriptions", "Subscriptions", 3_500.kes),
            Triple("support", "Family support", 15_000.kes),
            Triple("misc", "Miscellaneous", 5_000.kes),
        )
    }
}

/** Net spending: spending types add, refunds subtract, everything else is zero. */
fun Transaction.spendingEffect(): Long = when {
    type.countsAsSpending -> amountMinor
    type == TransactionType.REFUND -> -amountMinor
    else -> 0L
}

internal inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun AccountEntity.toDomain() = Account(id, name, enumOr(kind, AccountKind.OTHER), institution, openingBalanceMinor, openingDate)

private fun AssetEntity.toDomain() = Asset(id, name, enumOr(category, AssetCategory.OTHER), valueMinor, institution, enumOr(valueType, ValueType.ACTUAL), isGoalFund)

private fun LiabilityEntity.toDomain(payments: List<LiabilityPaymentEntity>) = Debt(
    id, name, lender, enumOr(category, DebtCategory.OTHER), enumOr(kind, DebtKind.REDUCING_BALANCE), balanceMinor,
    originalPrincipalMinor, annualRateBps, monthlyPaymentMinor, dueDayOfMonth, interestPaidToDateMinor, customPriority, notes,
    payments.map { DebtPayment(it.date, it.amountMinor, it.principalMinor, it.interestMinor) },
)

private fun Debt.toEntity(createdAt: Long, updatedAt: Long) = LiabilityEntity(
    id, name.trim(), lender.trim(), category.name, kind.name, balanceMinor, originalPrincipalMinor, annualRateBps,
    monthlyPaymentMinor, dueDayOfMonth, interestPaidToDateMinor, customPriority, notes, createdAt, updatedAt,
)

private fun Transaction.toEntity(hash: String, ts: Long) = TransactionEntity(
    id, accountId, date, amountMinor, type.name, if (splits.isNotEmpty()) null else categoryId, merchant.trim(), note.trim(),
    paidByCreditCard, source.name, importBatchId, hash, ts, ts,
)

private fun GoalEntity.toDomain() = GoalAssumptions(
    YearMonth.of(targetYear, targetMonth), todayPriceMinor, 0L, monthlyContributionMinor, annualReturnBps, annualInflationBps,
    insuranceRateBps, annualMaintenanceMinor, annualFuelMinor,
)

private fun GoalAssumptions.toEntity() = GoalEntity(
    RoomFinanceRepository.GOAL_ID, targetYearMonth.year, targetYearMonth.monthValue, todayPriceMinor, monthlyContributionMinor,
    annualReturnBps, annualInflationBps, insuranceRateBps, annualMaintenanceMinor, annualFuelMinor, System.currentTimeMillis(),
)
