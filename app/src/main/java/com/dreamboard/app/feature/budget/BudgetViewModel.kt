package com.dreamboard.app.feature.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.money.ratioBps
import com.dreamboard.app.domain.model.BudgetCategory
import com.dreamboard.app.domain.model.MonthSummary
import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.TransactionType
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CategoryLine(
    val category: BudgetCategory,
    val actualMinor: Long,
) {
    val usedBps: Long get() = ratioBps(actualMinor, category.monthlyBudgetMinor)
    val isOver: Boolean get() = actualMinor > category.monthlyBudgetMinor
    val remainingMinor: Long get() = category.monthlyBudgetMinor - actualMinor
}

enum class TxFilter(val label: String) { ALL("All"), SPENDING("Spending"), INCOME("Income"), NOT_SPENDING("Transfers & repayments") }

data class BudgetUiState(
    val isDemo: Boolean,
    val asOf: LocalDate,
    val month: YearMonth,
    val budgetMinor: Long,
    val spentMinor: Long,
    val incomeMinor: Long,
    val principalRepaymentsMinor: Long,
    val cardSettlementsMinor: Long,
    val projectedMonthEndMinor: Long,
    val lines: List<CategoryLine>,
    val history: List<MonthSummary>,
    val topMerchants: List<Pair<String, Long>>,
    val query: String,
    val filter: TxFilter,
    val transactions: List<Transaction>,
    val categoryNames: Map<String, String>,
) {
    val remainingMinor: Long get() = budgetMinor - spentMinor
    val usedBps: Long get() = ratioBps(spentMinor, budgetMinor)
    val alerts: List<CategoryLine> get() = lines.filter { it.isOver || it.usedBps >= 9_000 }
    val surplusMinor: Long get() = incomeMinor - spentMinor
}

/** Net spending for one transaction: spending types add, refunds subtract, everything else is zero. */
fun Transaction.spendingEffectMinor(): Long = when {
    type.countsAsSpending -> amountMinor
    type == TransactionType.REFUND -> -amountMinor
    else -> 0L
}

@HiltViewModel
class BudgetViewModel @Inject constructor(private val repo: FinanceRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TxFilter.ALL)

    val uiState: StateFlow<BudgetUiState> = combine(
        repo.budgetCategories, repo.transactions, repo.monthHistory, query, filter,
    ) { c, t, h, q, f -> build(c, t, h, q, f) }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            build(repo.budgetCategories.value, repo.transactions.value, repo.monthHistory.value, query.value, filter.value),
        )

    fun setQuery(q: String) = query.update { q }
    fun setFilter(f: TxFilter) = filter.update { f }

    private fun build(cats: List<BudgetCategory>, txs: List<Transaction>, hist: List<MonthSummary>, q: String, f: TxFilter): BudgetUiState {
        val asOf = repo.asOf
        val month = YearMonth.from(asOf)
        val monthTx = txs.filter { YearMonth.from(it.date) == month }
        val spent = monthTx.sumOf { it.spendingEffectMinor() }
        val budget = cats.sumOf { it.monthlyBudgetMinor }
        val income = monthTx.filter { it.type.countsAsIncome }.sumOf { it.amountMinor }
        val lines = cats.map { c -> CategoryLine(c, monthTx.filter { it.categoryId == c.id }.sumOf { it.spendingEffectMinor() }) }
            .sortedByDescending { it.actualMinor }
        val projected = if (asOf.dayOfMonth == 0) spent else spent * month.lengthOfMonth() / asOf.dayOfMonth
        val merchants = monthTx.filter { it.type.countsAsSpending }
            .groupBy { it.merchant }
            .map { (m, l) -> m to l.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
            .take(5)
        val names = cats.associate { it.id to it.name }
        val filtered = monthTx
            .filter {
                when (f) {
                    TxFilter.ALL -> true
                    TxFilter.SPENDING -> it.type.countsAsSpending || it.type == TransactionType.REFUND
                    TxFilter.INCOME -> it.type.countsAsIncome
                    TxFilter.NOT_SPENDING -> !it.type.countsAsSpending && !it.type.countsAsIncome && it.type != TransactionType.REFUND
                }
            }
            .filter {
                q.isBlank() || it.merchant.contains(q, ignoreCase = true) || it.note.contains(q, ignoreCase = true) ||
                    (names[it.categoryId]?.contains(q, ignoreCase = true) == true) || it.account.contains(q, ignoreCase = true)
            }
            .sortedByDescending { it.date }

        return BudgetUiState(
            isDemo = repo.isDemoData,
            asOf = asOf,
            month = month,
            budgetMinor = budget,
            spentMinor = spent,
            incomeMinor = income,
            principalRepaymentsMinor = monthTx.filter { it.type == TransactionType.LOAN_PRINCIPAL }.sumOf { it.amountMinor },
            cardSettlementsMinor = monthTx.filter { it.type == TransactionType.CARD_PAYMENT }.sumOf { it.amountMinor },
            projectedMonthEndMinor = projected,
            lines = lines,
            history = hist + MonthSummary(month, budget, spent, income),
            topMerchants = merchants,
            query = q,
            filter = f,
            transactions = filtered,
            categoryNames = names,
        )
    }
}

data class CategoryUiState(
    val line: CategoryLine?,
    val transactions: List<Transaction>,
    val month: YearMonth,
)

@HiltViewModel
class BudgetCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val categoryId: String = checkNotNull(savedStateHandle["categoryId"])

    val uiState: StateFlow<CategoryUiState> = combine(repo.budgetCategories, repo.transactions) { c, t -> build(c, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.budgetCategories.value, repo.transactions.value))

    private fun build(cats: List<BudgetCategory>, txs: List<Transaction>): CategoryUiState {
        val month = YearMonth.from(repo.asOf)
        val tx = txs.filter { it.categoryId == categoryId && YearMonth.from(it.date) == month }.sortedByDescending { it.date }
        val cat = cats.firstOrNull { it.id == categoryId }
        return CategoryUiState(cat?.let { CategoryLine(it, tx.sumOf { t -> t.spendingEffectMinor() }) }, tx, month)
    }
}
