package com.dreamboard.app.domain.repository

import com.dreamboard.app.core.finance.GoalAssumptions
import com.dreamboard.app.domain.model.Account
import com.dreamboard.app.domain.model.Asset
import com.dreamboard.app.domain.model.BudgetCategory
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.IgniteMonth
import com.dreamboard.app.domain.model.IgniteTargets
import com.dreamboard.app.domain.model.ImportBatch
import com.dreamboard.app.domain.model.Milestone
import com.dreamboard.app.domain.model.MonthSummary
import com.dreamboard.app.domain.model.NetWorthSnapshot
import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.Vehicle
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Why a snapshot was recorded. */
enum class SnapshotMethod { AUTOMATIC, MANUAL }

/**
 * Single source of truth for the UI. Reads are [StateFlow]s so screens can render immediately;
 * writes are suspend functions that persist to the local database.
 */
interface FinanceRepository {
    /** True while personal finance figures are demonstration data (Phase 1 only). */
    val isDemoData: Boolean
    /** Today, or the fixed demo date for demonstration data. */
    val asOf: LocalDate

    val accounts: StateFlow<List<Account>>
    val assets: StateFlow<List<Asset>>
    val netWorthHistory: StateFlow<List<NetWorthSnapshot>>
    val debts: StateFlow<List<Debt>>
    val goalAssumptions: StateFlow<GoalAssumptions>
    val milestones: StateFlow<List<Milestone>>

    val budgetCategories: StateFlow<List<BudgetCategory>>
    val transactions: StateFlow<List<Transaction>>
    val monthHistory: StateFlow<List<MonthSummary>>
    /** Average monthly take-home income, used for readiness checks. 0 when unknown. */
    val monthlyNetIncomeMinor: StateFlow<Long>
    val importBatches: StateFlow<List<ImportBatch>>

    // Ignite Motors: cars, deal costs and profit-sharing rules, kept separate from personal net worth.
    val igniteIsDemo: Boolean
    val igniteAsOf: LocalDate
    val vehicles: StateFlow<List<Vehicle>>
    val arrangements: StateFlow<List<FundingArrangement>>
    val igniteMonths: StateFlow<List<IgniteMonth>>
    val igniteTargets: StateFlow<IgniteTargets>
    suspend fun saveVehicle(vehicle: Vehicle)
    suspend fun deleteVehicle(id: String)
    suspend fun saveArrangement(arrangement: FundingArrangement)
    /** False when cars still use it. */
    suspend fun deleteArrangement(id: String): Boolean
    suspend fun setIgniteTargets(targets: IgniteTargets)
    /** Import key -> existing vehicle, for re-imports of the flipping tracker. */
    suspend fun vehiclesByImportKey(keys: Collection<String>): Map<String, Vehicle>
    /** Inserts or replaces each vehicle (with its expenses) and stores its import key. */
    suspend fun importVehicles(vehicles: List<Pair<Vehicle, String>>, newArrangements: List<FundingArrangement>)

    fun updateGoalAssumptions(transform: (GoalAssumptions) -> GoalAssumptions)
    fun updateMilestone(milestone: Milestone)

    suspend fun saveAsset(asset: Asset, reason: String = "Updated")
    suspend fun deleteAsset(id: String)
    suspend fun assetValuations(id: String): List<ValueHistoryLine>

    suspend fun saveDebt(debt: Debt, reason: String = "Updated")
    suspend fun deleteDebt(id: String)
    suspend fun recordDebtPayment(debtId: String, date: LocalDate, amountMinor: Long, interestMinor: Long, note: String)

    suspend fun saveAccount(account: Account)
    /** Returns false when the account still has transactions. */
    suspend fun deleteAccount(id: String): Boolean

    suspend fun saveCategory(category: BudgetCategory)
    suspend fun saveTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: String)
    /** Ids of [hashes] that already exist, counted per hash, for duplicate detection. */
    suspend fun existingHashCounts(hashes: Collection<String>): Map<String, Int>
    suspend fun importTransactions(batch: ImportBatch, transactions: List<Transaction>, hashes: List<String>, newCategories: List<BudgetCategory>)

    suspend fun recordSnapshot(method: SnapshotMethod)
    /** Records an automatic snapshot if the last one is older than 7 days and there is data. */
    suspend fun ensureRecentSnapshot()

    /** Adds Frank's example debts (unverified starting values) when no debts exist yet. */
    suspend fun loadExampleDebts()
    suspend fun eraseAllData()
}

/** A dated value from an audit or valuation history. */
data class ValueHistoryLine(val date: LocalDate, val valueMinor: Long, val note: String)
