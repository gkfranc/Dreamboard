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
import kotlinx.coroutines.launch
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
    val isCurrentMonth: Boolean = true,
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

/** Net spending attributed to [categoryId], honouring splits. */
fun Transaction.spendingFor(categoryId: String): Long {
    val sign = when {
        type.countsAsSpending -> 1L
        type == TransactionType.REFUND -> -1L
        else -> return 0L
    }
    return allocations().filter { it.first == categoryId }.sumOf { it.second } * sign
}

fun Transaction.touchesCategory(categoryId: String): Boolean = allocations().any { it.first == categoryId }

@HiltViewModel
class BudgetViewModel @Inject constructor(private val repo: FinanceRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TxFilter.ALL)
    private val selectedMonth = MutableStateFlow(YearMonth.from(repo.asOf))

    val uiState: StateFlow<BudgetUiState> = combine(
        combine(repo.budgetCategories, repo.transactions, repo.monthHistory) { c, t, h -> Triple(c, t, h) },
        query, filter, selectedMonth,
    ) { d, q, f, m -> build(d.first, d.second, d.third, q, f, m) }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            build(repo.budgetCategories.value, repo.transactions.value, repo.monthHistory.value, query.value, filter.value, selectedMonth.value),
        )

    fun setQuery(q: String) = query.update { q }
    fun setFilter(f: TxFilter) = filter.update { f }
    fun shiftMonth(delta: Long) = selectedMonth.update { m ->
        val next = m.plusMonths(delta)
        if (next.isAfter(YearMonth.from(repo.asOf))) m else next
    }
    fun saveCategory(c: BudgetCategory) = viewModelScope.launch { repo.saveCategory(c) }

    private fun build(cats: List<BudgetCategory>, txs: List<Transaction>, hist: List<MonthSummary>, q: String, f: TxFilter, month: YearMonth): BudgetUiState {
        val today = repo.asOf
        val isCurrent = month == YearMonth.from(today)
        val asOf = if (isCurrent) today else month.atEndOfMonth()
        val monthTx = txs.filter { YearMonth.from(it.date) == month }
        val spent = monthTx.sumOf { it.spendingEffectMinor() }
        val budget = cats.sumOf { it.monthlyBudgetMinor }
        val income = monthTx.filter { it.type.countsAsIncome }.sumOf { it.amountMinor }
        val lines = cats.map { c -> CategoryLine(c, monthTx.sumOf { it.spendingFor(c.id) }) }
            .sortedByDescending { it.actualMinor }
        val projected = if (!isCurrent) spent else spent * month.lengthOfMonth() / asOf.dayOfMonth
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
            history = if (isCurrent) hist + MonthSummary(month, budget, spent, income) else hist + MonthSummary(YearMonth.from(today), budget,
                txs.filter { YearMonth.from(it.date) == YearMonth.from(today) }.sumOf { it.spendingEffectMinor() },
                txs.filter { YearMonth.from(it.date) == YearMonth.from(today) && it.type.countsAsIncome }.sumOf { it.amountMinor }),
            isCurrentMonth = isCurrent,
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
    private val month: YearMonth = savedStateHandle.get<String>("month")?.takeIf { it.isNotBlank() }
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.from(repo.asOf)

    fun saveCategory(c: BudgetCategory) = viewModelScope.launch { repo.saveCategory(c) }

    val uiState: StateFlow<CategoryUiState> = combine(repo.budgetCategories, repo.transactions) { c, t -> build(c, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.budgetCategories.value, repo.transactions.value))

    private fun build(cats: List<BudgetCategory>, txs: List<Transaction>): CategoryUiState {
        val tx = txs.filter { it.touchesCategory(categoryId) && YearMonth.from(it.date) == month }.sortedByDescending { it.date }
        val cat = cats.firstOrNull { it.id == categoryId }
        return CategoryUiState(cat?.let { CategoryLine(it, tx.sumOf { t -> t.spendingFor(categoryId) }) }, tx, month)
    }
}
