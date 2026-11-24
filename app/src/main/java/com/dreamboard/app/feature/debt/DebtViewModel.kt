package com.dreamboard.app.feature.debt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.finance.AmortizationRow
import com.dreamboard.app.core.finance.DebtForProjection
import com.dreamboard.app.core.finance.DebtProjection
import com.dreamboard.app.core.finance.LoanMath
import com.dreamboard.app.core.finance.RepaymentStrategy
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.DebtCategory
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
import javax.inject.Inject

data class ScenarioInputs(
    val strategy: RepaymentStrategy = RepaymentStrategy.AVALANCHE,
    val extraMonthlyMinor: Long = 0L,
    val businessIncomeToDebtMinor: Long = 0L,
) {
    val totalExtraMinor: Long get() = extraMonthlyMinor + businessIncomeToDebtMinor
}

data class DebtUiState(
    val isDemo: Boolean,
    val asOf: LocalDate,
    val debts: List<Debt>,
    val totalOutstandingMinor: Long,
    val interestBearingMinor: Long,
    val monthlyCommitmentMinor: Long,
    val composition: List<Pair<DebtCategory, Long>>,
    val inputs: ScenarioInputs,
    val baseline: DebtProjection,
    val scenario: DebtProjection,
) {
    val debtFreeDate: LocalDate? get() = scenario.monthsToDebtFree?.let { asOf.plusMonths(it.toLong()) }
    val monthsSaved: Int? get() {
        val b = baseline.monthsToDebtFree ?: return null
        val s = scenario.monthsToDebtFree ?: return null
        return b - s
    }
    val interestSavedMinor: Long get() = baseline.totalInterestMinor - scenario.totalInterestMinor
}

fun Debt.toProjection() = DebtForProjection(id, balanceMinor ?: 0L, annualRateBps, monthlyPaymentMinor, customPriority)

@HiltViewModel
class DebtViewModel @Inject constructor(private val repo: FinanceRepository) : ViewModel() {

    private val inputs = MutableStateFlow(ScenarioInputs())

    val uiState: StateFlow<DebtUiState> = combine(repo.debts, inputs) { d, i -> build(d, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.debts.value, inputs.value))

    fun setStrategy(s: RepaymentStrategy) = inputs.update { it.copy(strategy = s) }
    fun setExtra(minor: Long) = inputs.update { it.copy(extraMonthlyMinor = minor) }
    fun loadExampleDebts() = viewModelScope.launch { repo.loadExampleDebts() }
    fun setBusinessIncome(minor: Long) = inputs.update { it.copy(businessIncomeToDebtMinor = minor) }

    private fun build(debts: List<Debt>, i: ScenarioInputs): DebtUiState {
        val projectable = debts.filter { it.isProjectable }.map { it.toProjection() }
        return DebtUiState(
            isDemo = repo.isDemoData,
            asOf = repo.asOf,
            debts = debts,
            totalOutstandingMinor = debts.sumOf { it.balanceMinor ?: 0L },
            interestBearingMinor = debts.filter { it.isProjectable && it.annualRateBps > 0 }.sumOf { it.balanceMinor ?: 0L },
            monthlyCommitmentMinor = debts.sumOf { it.monthlyPaymentMinor },
            composition = debts.filter { (it.balanceMinor ?: 0L) > 0 }
                .groupBy { it.category }
                .map { (c, l) -> c to l.sumOf { it.balanceMinor ?: 0L } }
                .sortedByDescending { it.second },
            inputs = i,
            baseline = LoanMath.project(projectable, i.strategy, 0L),
            scenario = LoanMath.project(projectable, i.strategy, i.totalExtraMinor),
        )
    }
}

data class DebtDetailUiState(
    val debt: Debt?,
    val asOf: LocalDate,
    val extraMinor: Long,
    val schedule: List<AmortizationRow>,
    val baselineSchedule: List<AmortizationRow>,
)

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val debtId: String = checkNotNull(savedStateHandle["debtId"])
    private val extra = MutableStateFlow(0L)

    val uiState: StateFlow<DebtDetailUiState> = combine(repo.debts, extra) { d, e -> build(d, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.debts.value, extra.value))

    fun setExtra(minor: Long) = extra.update { minor }

    fun recordPayment(date: LocalDate, amountMinor: Long, interestMinor: Long, note: String) =
        viewModelScope.launch { repo.recordDebtPayment(debtId, date, amountMinor, interestMinor, note) }

    private fun build(debts: List<Debt>, e: Long): DebtDetailUiState {
        val d = debts.firstOrNull { it.id == debtId }
        val canSchedule = d != null && d.isProjectable
        return DebtDetailUiState(
            debt = d,
            asOf = repo.asOf,
            extraMinor = e,
            schedule = if (canSchedule) LoanMath.schedule(d!!.balanceMinor!!, d.annualRateBps, d.monthlyPaymentMinor, e) else emptyList(),
            baselineSchedule = if (canSchedule) LoanMath.schedule(d!!.balanceMinor!!, d.annualRateBps, d.monthlyPaymentMinor) else emptyList(),
        )
    }
}
