package com.dreamboard.app.feature.dreamboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.finance.GoalAssumptions
import com.dreamboard.app.core.finance.GoalMath
import com.dreamboard.app.core.finance.GoalProjection
import com.dreamboard.app.core.money.ratioBps
import com.dreamboard.app.domain.model.Asset
import com.dreamboard.app.domain.model.AssetCategory
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.Milestone
import com.dreamboard.app.domain.model.MonthSummary
import com.dreamboard.app.domain.model.NetWorthSnapshot
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

enum class HistoryPeriod(val label: String, val months: Long?) {
    M1("1M", 1), M3("3M", 3), M6("6M", 6), Y1("1Y", 12), Y5("5Y", 60), ALL("All", null),
}

data class AllocationSlice(val category: AssetCategory, val valueMinor: Long, val shareBps: Long)

data class ReadinessCheck(val title: String, val detail: String, val passed: Boolean)

data class DreamboardUiState(
    val isDemo: Boolean,
    val asOf: LocalDate,
    val totalAssetsMinor: Long,
    val totalLiabilitiesMinor: Long,
    val unknownLiabilities: Int,
    val netWorthMinor: Long,
    val change30dMinor: Long?,
    val change30dBps: Long?,
    val allocation: List<AllocationSlice>,
    val period: HistoryPeriod,
    val history: List<NetWorthSnapshot>,
    val goal: GoalAssumptions,
    val goalProjection: GoalProjection,
    val readiness: List<ReadinessCheck>,
    val milestones: List<Milestone>,
    val assets: List<Asset>,
)

@HiltViewModel
class DreamboardViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val period = MutableStateFlow(HistoryPeriod.Y1)

    val uiState: StateFlow<DreamboardUiState> = combine(
        combine(repo.assets, repo.debts, repo.netWorthHistory, repo.goalAssumptions, repo.milestones) { a, d, h, g, m ->
            Inputs(a, d, h, g, m)
        },
        combine(period, repo.monthHistory, repo.monthlyNetIncomeMinor) { p, mh, inc -> Triple(p, mh, inc) },
    ) { inputs, extra -> build(inputs, extra.first, extra.second, extra.third) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(currentInputs(), period.value, repo.monthHistory.value, repo.monthlyNetIncomeMinor.value))

    fun selectPeriod(p: HistoryPeriod) = period.update { p }

    fun updateMilestone(m: Milestone) = repo.updateMilestone(m)

    fun updateGoal(transform: (GoalAssumptions) -> GoalAssumptions) = repo.updateGoalAssumptions(transform)

    private data class Inputs(
        val assets: List<Asset>,
        val debts: List<Debt>,
        val history: List<NetWorthSnapshot>,
        val goal: GoalAssumptions,
        val milestones: List<Milestone>,
    )

    private fun currentInputs() = Inputs(repo.assets.value, repo.debts.value, repo.netWorthHistory.value, repo.goalAssumptions.value, repo.milestones.value)

    private fun build(i: Inputs, p: HistoryPeriod, months: List<MonthSummary>, monthlyIncome: Long): DreamboardUiState {
        val asOf = repo.asOf
        val totalAssets = i.assets.sumOf { it.valueMinor }
        val totalLiabilities = i.debts.sumOf { it.balanceMinor ?: 0L }
        val net = totalAssets - totalLiabilities

        val monthAgo = i.history.lastOrNull { !it.date.isAfter(asOf.minusDays(30)) }
        val change = monthAgo?.let { net - it.netWorthMinor }
        val changeBps = monthAgo?.takeIf { it.netWorthMinor != 0L }?.let { ratioBps(change ?: 0L, kotlin.math.abs(it.netWorthMinor)) }

        val allocation = i.assets.groupBy { it.category }
            .map { (c, list) -> list.sumOf { it.valueMinor }.let { AllocationSlice(c, it, ratioBps(it, totalAssets)) } }
            .filter { it.valueMinor > 0 }
            .sortedByDescending { it.valueMinor }

        val from = p.months?.let { asOf.minusMonths(it) }
        val history = if (from == null) i.history else i.history.filter { !it.date.isBefore(from) }

        // Goal progress counts ONLY the dedicated fund, never total net worth.
        val fund = i.assets.filter { it.isGoalFund }.sumOf { it.valueMinor }
        val goal = i.goal.copy(currentFundMinor = fund)
        val projection = GoalMath.project(goal, YearMonth.from(asOf))

        return DreamboardUiState(
            isDemo = repo.isDemoData,
            asOf = asOf,
            totalAssetsMinor = totalAssets,
            totalLiabilitiesMinor = totalLiabilities,
            unknownLiabilities = i.debts.count { !it.isKnown },
            netWorthMinor = net,
            change30dMinor = change,
            change30dBps = changeBps,
            allocation = allocation,
            period = p,
            history = history,
            goal = goal,
            goalProjection = projection,
            readiness = readiness(i, projection, months, monthlyIncome),
            milestones = i.milestones.sortedBy { it.year },
            assets = i.assets,
        )
    }

    private fun readiness(i: Inputs, g: GoalProjection, months: List<MonthSummary>, monthlyIncome: Long): List<ReadinessCheck> {
        val interestDebt = i.debts.filter { it.isProjectable && it.annualRateBps > 0 }.sumOf { it.balanceMinor ?: 0L }
        val liquid = i.assets.filter {
            !it.isGoalFund && it.category in setOf(AssetCategory.CASH_BANK, AssetCategory.MOBILE_MONEY, AssetCategory.INVESTMENTS)
        }.sumOf { it.valueMinor }
        val avgSpend = months.takeIf { it.isNotEmpty() }?.let { m -> m.sumOf { it.spentMinor } / m.size } ?: 0L
        val bufferMonthsX10 = if (avgSpend == 0L) 0L else liquid * 10 / avgSpend
        val annualIncome = monthlyIncome * 12
        val costShareBps = ratioBps(g.annualOwnershipCostMinor, annualIncome)
        return listOf(
            ReadinessCheck("Interest-bearing debt cleared", "Loans and revolving cards must reach zero first.", interestDebt == 0L),
            ReadinessCheck(
                "Emergency fund of 6+ months",
                "Liquid savings cover ${bufferMonthsX10 / 10}.${bufferMonthsX10 % 10} months of spending (excl. Porsche fund).",
                bufferMonthsX10 >= 60,
            ),
            ReadinessCheck("Porsche fund fully funded", "Buy from the dedicated fund, not from net worth or credit.", g.fundedBps >= 10_000),
            ReadinessCheck(
                "Running costs ≤ 10% of income",
                "Insurance, service and fuel would take ${costShareBps / 100}% of current take-home income.",
                annualIncome > 0 && costShareBps <= 1_000,
            ),
        )
    }
}
