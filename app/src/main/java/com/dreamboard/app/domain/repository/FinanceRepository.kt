package com.dreamboard.app.domain.repository

import com.dreamboard.app.core.finance.GoalAssumptions
import com.dreamboard.app.domain.model.Asset
import com.dreamboard.app.domain.model.BudgetCategory
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.IgniteMonth
import com.dreamboard.app.domain.model.Milestone
import com.dreamboard.app.domain.model.MonthSummary
import com.dreamboard.app.domain.model.NetWorthSnapshot
import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.Vehicle
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Single source of truth for the UI. Phase 1 binds a demo implementation; Phase 2 replaces it
 * with a Room-backed implementation without touching any screen or ViewModel.
 */
interface FinanceRepository {
    /** True while the data shown is demonstration data rather than the user's own records. */
    val isDemoData: Boolean
    val asOf: LocalDate

    val assets: StateFlow<List<Asset>>
    val netWorthHistory: StateFlow<List<NetWorthSnapshot>>
    val debts: StateFlow<List<Debt>>
    val goalAssumptions: StateFlow<GoalAssumptions>
    val milestones: StateFlow<List<Milestone>>

    val budgetCategories: StateFlow<List<BudgetCategory>>
    val transactions: StateFlow<List<Transaction>>
    val monthHistory: StateFlow<List<MonthSummary>>
    /** Demo monthly net take-home income, used for readiness checks. */
    val monthlyNetIncomeMinor: StateFlow<Long>

    val vehicles: StateFlow<List<Vehicle>>
    val arrangements: StateFlow<List<FundingArrangement>>
    val igniteMonths: StateFlow<List<IgniteMonth>>
    val igniteAvailableCashMinor: StateFlow<Long>

    fun updateGoalAssumptions(transform: (GoalAssumptions) -> GoalAssumptions)
    fun updateMilestone(milestone: Milestone)
}
