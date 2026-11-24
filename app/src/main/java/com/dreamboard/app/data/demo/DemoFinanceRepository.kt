package com.dreamboard.app.data.demo

import com.dreamboard.app.core.finance.GoalAssumptions
import com.dreamboard.app.core.money.kes
import com.dreamboard.app.domain.model.Asset
import com.dreamboard.app.domain.model.AssetCategory
import com.dreamboard.app.domain.model.BudgetCategory
import com.dreamboard.app.domain.model.DealExpense
import com.dreamboard.app.domain.model.DealExpenseKind
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.DebtCategory
import com.dreamboard.app.domain.model.DebtKind
import com.dreamboard.app.domain.model.DebtPayment
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.FundingType
import com.dreamboard.app.domain.model.IgniteMonth
import com.dreamboard.app.domain.model.Milestone
import com.dreamboard.app.domain.model.MilestoneStatus
import com.dreamboard.app.domain.model.MonthSummary
import com.dreamboard.app.domain.model.NetWorthSnapshot
import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.TransactionSource
import com.dreamboard.app.domain.model.TransactionType
import com.dreamboard.app.domain.model.ValueType
import com.dreamboard.app.domain.model.Vehicle
import com.dreamboard.app.domain.model.VehicleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEMONSTRATION DATA ONLY. Since Phase 2 only the Ignite Motors section is still served from here
 * (until Phase 4); personal finance data lives in the Room database.
 * Every figure here is illustrative and is labelled as such in the UI.
 * The debt balances are the user's own example figures, used as editable seed values, not verified
 * live balances. Edits made in Phase 1 live in memory and reset when the app restarts.
 */
@Singleton
class DemoFinanceRepository @Inject constructor() {

    val isDemoData: Boolean = true
    val asOf: LocalDate = DEMO_AS_OF

    val assets = MutableStateFlow(demoAssets()).asStateFlow()
    val debts = MutableStateFlow(demoDebts()).asStateFlow()
    val netWorthHistory = MutableStateFlow(demoNetWorthHistory()).asStateFlow()

    private val _goal = MutableStateFlow(
        GoalAssumptions(
            targetYearMonth = YearMonth.of(2035, 12),
            todayPriceMinor = 22_000_000.kes,
            currentFundMinor = 350_000.kes,
            monthlyContributionMinor = 25_000.kes,
            annualReturnBps = 900,
            annualInflationBps = 500,
            insuranceRateBps = 450,
            annualMaintenanceMinor = 400_000.kes,
            annualFuelMinor = 540_000.kes,
        )
    )
    val goalAssumptions: StateFlow<GoalAssumptions> = _goal.asStateFlow()

    private val _milestones = MutableStateFlow(
        listOf(
            Milestone("m1", 2027, "Project Zero Debt", "Clear every interest-bearing loan and card.", MilestoneStatus.IN_PROGRESS),
            Milestone("m2", 2028, "Financial Stability & Business Growth", "Six-month emergency fund; Ignite selling 4+ cars a month.", MilestoneStatus.UPCOMING),
            Milestone("m3", 2030, "Wealth Acceleration", "Invest surplus consistently; grow net worth past KES 15M.", MilestoneStatus.UPCOMING),
            Milestone("m4", 2035, "Porsche Cayenne", "Buy the Cayenne in cash from the dedicated fund.", MilestoneStatus.UPCOMING),
        )
    )
    val milestones: StateFlow<List<Milestone>> = _milestones.asStateFlow()

    val budgetCategories = MutableStateFlow(demoCategories()).asStateFlow()
    val transactions = MutableStateFlow(demoTransactions()).asStateFlow()
    val monthHistory = MutableStateFlow(demoMonthHistory()).asStateFlow()
    val monthlyNetIncomeMinor = MutableStateFlow(285_000.kes).asStateFlow()

    val arrangements = MutableStateFlow(demoArrangements()).asStateFlow()
    val vehicles = MutableStateFlow(demoVehicles()).asStateFlow()
    val igniteMonths = MutableStateFlow(demoIgniteMonths()).asStateFlow()
    val igniteAvailableCashMinor = MutableStateFlow(850_000.kes).asStateFlow()

    fun updateGoalAssumptions(transform: (GoalAssumptions) -> GoalAssumptions) = _goal.update(transform)

    fun updateMilestone(milestone: Milestone) = _milestones.update { list ->
        list.map { if (it.id == milestone.id) milestone else it }.sortedBy { it.year }
    }

    companion object {
        val DEMO_AS_OF: LocalDate = LocalDate.of(2026, 11, 23)

        private fun demoAssets() = listOf(
            Asset("a1", "Current account", AssetCategory.CASH_BANK, 185_000.kes, "Demo Bank", ValueType.ACTUAL),
            Asset("a2", "M-Pesa wallet", AssetCategory.MOBILE_MONEY, 42_500.kes, "Safaricom", ValueType.ACTUAL),
            Asset("a3", "SACCO deposits", AssetCategory.SACCO_SAVINGS, 520_000.kes, "Demo SACCO", ValueType.ACTUAL),
            Asset("a4", "Money market fund", AssetCategory.INVESTMENTS, 310_000.kes, "Demo MMF", ValueType.ACTUAL),
            Asset("a5", "Porsche fund (MMF)", AssetCategory.INVESTMENTS, 350_000.kes, "Demo MMF", ValueType.ACTUAL, isGoalFund = true),
            Asset("a6", "Residential plot", AssetCategory.PROPERTY, 2_400_000.kes, "Valuation", ValueType.ESTIMATED),
            Asset("a7", "Family car", AssetCategory.VEHICLES, 1_650_000.kes, "Market estimate", ValueType.ESTIMATED),
            Asset("a8", "Ignite Motors: personal equity", AssetCategory.BUSINESS_EQUITY, 1_200_000.kes, "Own share only", ValueType.ESTIMATED),
        )

        private fun demoDebts() = listOf(
            Debt(
                "d1", "ABSA Personal Loan", "ABSA Bank", DebtCategory.PERSONAL_LOAN, DebtKind.REDUCING_BALANCE,
                balanceMinor = 1_480_000.kes, originalPrincipalMinor = 2_000_000.kes, annualRateBps = 1650,
                monthlyPaymentMinor = 42_000.kes, dueDayOfMonth = 28, interestPaidToDateMinor = 498_000.kes,
                customPriority = 3, notes = "Rate and installment are demo assumptions.",
                payments = demoPayments(1_480_000.kes, 1650, 42_000.kes),
            ),
            Debt(
                "d2", "SACCO Loan", "Demo SACCO", DebtCategory.SACCO_LOAN, DebtKind.REDUCING_BALANCE,
                balanceMinor = 666_000.kes, originalPrincipalMinor = 900_000.kes, annualRateBps = 1200,
                monthlyPaymentMinor = 25_000.kes, dueDayOfMonth = 5, interestPaidToDateMinor = 121_000.kes,
                customPriority = 4, notes = "1% per month on the reducing balance (demo assumption).",
                payments = demoPayments(666_000.kes, 1200, 25_000.kes),
            ),
            Debt(
                "d3", "ABSA Credit Card", "ABSA Bank", DebtCategory.CREDIT_CARD, DebtKind.REVOLVING,
                balanceMinor = 200_000.kes, originalPrincipalMinor = null, annualRateBps = 3600,
                monthlyPaymentMinor = 12_000.kes, dueDayOfMonth = 15, interestPaidToDateMinor = null,
                customPriority = 1, notes = "Revolving, interest-bearing balance.",
            ),
            Debt(
                "d4", "Equity Credit Card", "Equity Bank", DebtCategory.CREDIT_CARD, DebtKind.STATEMENT_PAID_IN_FULL,
                balanceMinor = 140_000.kes, originalPrincipalMinor = null, annualRateBps = 0,
                monthlyPaymentMinor = 140_000.kes, dueDayOfMonth = 20, interestPaidToDateMinor = 0L,
                customPriority = 6, notes = "Used as a payment instrument and settled in full each month. " +
                    "Counts as a current liability, not revolving debt; purchases already count as spending.",
            ),
            Debt(
                "d5", "Family Capital Restoration", "Family", DebtCategory.FAMILY_LOAN, DebtKind.INTEREST_FREE,
                balanceMinor = 200_000.kes, originalPrincipalMinor = 200_000.kes, annualRateBps = 0,
                monthlyPaymentMinor = 10_000.kes, dueDayOfMonth = null, interestPaidToDateMinor = 0L,
                customPriority = 2, notes = "Interest-free obligation to restore family capital.",
            ),
            Debt(
                "d6", "Salary Advance", "Employer", DebtCategory.SALARY_ADVANCE, DebtKind.INTEREST_FREE,
                balanceMinor = null, originalPrincipalMinor = null, annualRateBps = 0,
                monthlyPaymentMinor = 0L, dueDayOfMonth = null, interestPaidToDateMinor = null,
                customPriority = 5, notes = "Balance unknown until you enter it. Excluded from totals.",
            ),
        )

        /** Six illustrative past installments ending at the current balance. */
        private fun demoPayments(currentMinor: Long, rateBps: Int, paymentMinor: Long): List<DebtPayment> {
            val out = ArrayList<DebtPayment>()
            var bal = currentMinor
            var date = DEMO_AS_OF.withDayOfMonth(1)
            repeat(6) {
                // Walk backwards: previous balance B satisfies B + i(B) - payment = bal.
                val prev = java.math.BigDecimal.valueOf(bal + paymentMinor)
                    .multiply(java.math.BigDecimal.valueOf(120_000L))
                    .divide(java.math.BigDecimal.valueOf(120_000L + rateBps), 0, java.math.RoundingMode.HALF_UP)
                    .toLong()
                val interest = com.dreamboard.app.core.finance.LoanMath.monthlyInterest(prev, rateBps)
                out += DebtPayment(date, paymentMinor, paymentMinor - interest, interest)
                bal = prev
                date = date.minusMonths(1)
            }
            return out
        }

        /** Weekly illustrative snapshots for five years. Clearly labelled as demo in the UI. */
        private fun demoNetWorthHistory(): List<NetWorthSnapshot> {
            val weeks = 5 * 52
            val start = DEMO_AS_OF.minusWeeks(weeks.toLong())
            val out = ArrayList<NetWorthSnapshot>(weeks + 1)
            val endAssets = 6_657_500.kes
            val endLiab = 2_686_000.kes
            for (w in 0..weeks) {
                val t = w.toDouble() / weeks
                // Synthetic demo curve shape only (not a balance calculation); rounded to whole shillings.
                val assets = (endAssets * (0.42 + 0.58 * t * t) * (1 + 0.012 * kotlin.math.sin(w / 3.0))).toLong()
                val liab = (endLiab * (1.45 - 0.45 * t) * (1 + 0.006 * kotlin.math.cos(w / 5.0))).toLong()
                out += NetWorthSnapshot(start.plusWeeks(w.toLong()), roundToShilling(assets), roundToShilling(liab))
            }
            out[out.lastIndex] = NetWorthSnapshot(DEMO_AS_OF, endAssets, endLiab)
            return out
        }

        private fun roundToShilling(minor: Long) = minor - Math.floorMod(minor, 100L)

        private fun demoCategories() = listOf(
            BudgetCategory("groceries", "Groceries", 45_000.kes),
            BudgetCategory("household", "Household", 18_000.kes),
            BudgetCategory("transport", "Fuel & transport", 28_000.kes),
            BudgetCategory("utilities", "Utilities", 9_500.kes),
            BudgetCategory("internet", "Internet & airtime", 6_500.kes),
            BudgetCategory("children", "Family & children", 22_000.kes),
            BudgetCategory("health", "Healthcare", 8_000.kes),
            BudgetCategory("education", "Education", 35_000.kes),
            BudgetCategory("dining", "Dining & entertainment", 12_000.kes),
            BudgetCategory("shopping", "Personal shopping", 10_000.kes),
            BudgetCategory("insurance", "Insurance", 7_500.kes),
            BudgetCategory("subscriptions", "Subscriptions", 3_500.kes),
            BudgetCategory("support", "Family support", 15_000.kes),
            BudgetCategory("misc", "Miscellaneous", 5_000.kes),
        )

        private fun tx(
            id: Int, day: Int, merchant: String, amount: Int, type: TransactionType, category: String?,
            account: String, note: String = "", card: Boolean = false,
        ) = Transaction(
            "t$id", DEMO_AS_OF.withDayOfMonth(day), merchant, amount.kes, type, category, account,
            TransactionSource.DEMO, note, card,
        )

        private fun demoTransactions(): List<Transaction> {
            val E = TransactionType.EXPENSE
            return listOf(
                tx(1, 1, "Salary (net)", 285_000, TransactionType.INCOME, null, "Demo Bank"),
                tx(3, 2, "Naivas Supermarket", 8_640, E, "groceries", "Equity Credit Card", card = true),
                tx(4, 2, "Shell Fuel", 6_000, E, "transport", "M-Pesa"),
                tx(5, 3, "KPLC Tokens", 3_000, E, "utilities", "M-Pesa"),
                tx(6, 3, "Safaricom Home Fibre", 4_100, E, "internet", "M-Pesa"),
                tx(7, 4, "School fees: term 3 balance", 35_000, E, "education", "Demo Bank"),
                tx(8, 5, "SACCO loan installment", 18_340, TransactionType.LOAN_PRINCIPAL, null, "Demo Bank", "Principal part"),
                tx(9, 5, "SACCO loan interest", 6_660, TransactionType.INTEREST, "misc", "Demo Bank", "Interest part"),
                tx(10, 6, "Carrefour", 11_250, E, "groceries", "Equity Credit Card", card = true),
                tx(11, 7, "Transfer to Porsche fund", 25_000, TransactionType.INVESTMENT, null, "Demo Bank"),
                tx(12, 7, "Bank → M-Pesa", 15_000, TransactionType.TRANSFER, null, "Demo Bank", "Internal transfer"),
                tx(13, 8, "Java House", 3_450, E, "dining", "Equity Credit Card", card = true),
                tx(14, 9, "Total Energies", 7_500, E, "transport", "M-Pesa"),
                tx(15, 10, "Mum: monthly support", 10_000, E, "support", "M-Pesa"),
                tx(16, 10, "Netflix", 1_100, E, "subscriptions", "Equity Credit Card", card = true),
                tx(17, 11, "Goodlife Pharmacy", 2_380, E, "health", "M-Pesa"),
                tx(18, 12, "Quickmart", 6_920, E, "groceries", "M-Pesa"),
                tx(19, 13, "Kids swimming lessons", 6_000, E, "children", "M-Pesa"),
                tx(20, 14, "Water bill", 1_850, E, "utilities", "M-Pesa"),
                tx(21, 15, "ABSA card payment", 12_000, TransactionType.CARD_PAYMENT, null, "Demo Bank", "Bill payment, not new spending"),
                tx(22, 15, "ABSA card interest", 6_000, TransactionType.INTEREST, "misc", "ABSA Credit Card"),
                tx(23, 16, "Uber", 1_240, E, "transport", "M-Pesa"),
                tx(24, 16, "Artcaffe", 4_800, E, "dining", "Equity Credit Card", card = true),
                tx(25, 17, "Ignite distribution received", 60_000, TransactionType.INCOME, null, "Demo Bank", "Personal share actually received"),
                tx(26, 18, "Naivas Supermarket", 9_870, E, "groceries", "Equity Credit Card", card = true),
                tx(27, 18, "Kids clothing", 7_400, E, "children", "Equity Credit Card", card = true),
                tx(28, 19, "Shell Fuel", 6_500, E, "transport", "M-Pesa"),
                tx(29, 20, "Equity card statement payment", 64_000, TransactionType.CARD_PAYMENT, null, "Demo Bank", "Settled in full, not new spending"),
                tx(30, 20, "Motor insurance (monthly)", 7_500, E, "insurance", "Demo Bank"),
                tx(31, 20, "Shoe repair refund", 1_200, TransactionType.REFUND, "shopping", "M-Pesa"),
                tx(32, 21, "Jumia: headphones", 8_900, E, "shopping", "Equity Credit Card", card = true),
                tx(33, 21, "Airtime", 1_000, E, "internet", "M-Pesa"),
                tx(34, 22, "Cleaning supplies", 3_960, E, "household", "M-Pesa"),
                tx(35, 22, "Sister: school support", 5_000, E, "support", "M-Pesa"),
                tx(36, 23, "Chandarana Foodplus", 7_310, E, "groceries", "Equity Credit Card", card = true),
                tx(37, 23, "Work lunch reimbursed", 2_600, TransactionType.REIMBURSEMENT, null, "M-Pesa", "Not income"),
            )
        }

        private fun demoMonthHistory(): List<MonthSummary> {
            val m = YearMonth.from(DEMO_AS_OF)
            return listOf(
                MonthSummary(m.minusMonths(5), 222_000.kes, 214_300.kes, 291_000.kes),
                MonthSummary(m.minusMonths(4), 222_000.kes, 236_900.kes, 285_000.kes),
                MonthSummary(m.minusMonths(3), 225_000.kes, 219_450.kes, 345_000.kes),
                MonthSummary(m.minusMonths(2), 225_000.kes, 228_100.kes, 300_000.kes),
                MonthSummary(m.minusMonths(1), 225_000.kes, 207_800.kes, 325_000.kes),
                // Current month's spent/income are computed from transactions in the ViewModel.
            )
        }

        private fun demoArrangements() = listOf(
            FundingArrangement("joint", "Joint business pool", FundingType.JOINT_POOL, 0, 5_000, 5_000),
            FundingArrangement("uk", "UK investor pool", FundingType.UK_INVESTOR, 6_000, 2_000, 2_000),
            FundingArrangement("ind", "Individual investor: demo", FundingType.INDIVIDUAL_INVESTOR, 5_000, 2_500, 2_500),
            FundingArrangement("partner", "Partner-funded", FundingType.PARTNER_FUNDED, 0, 7_000, 3_000),
        )

        private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

        private fun demoVehicles(): List<Vehicle> = listOf(
            Vehicle("v1", "DEMO 001", "Toyota", "Harrier", 2017, d(2026, 8, 4), 2_850_000.kes, "uk", "Very good",
                listOf(DealExpense(DealExpenseKind.REPAIRS, 85_000.kes), DealExpense(DealExpenseKind.TRANSFER_FEES, 12_000.kes), DealExpense(DealExpenseKind.BROKER_COMMISSION, 40_000.kes)),
                3_350_000.kes, 3_300_000.kes, 3_280_000.kes, d(2026, 9, 2), VehicleStatus.SOLD, "Quick sale via referral."),
            Vehicle("v2", "DEMO 002", "Mazda", "CX-5", 2018, d(2026, 8, 20), 2_300_000.kes, "joint", "Good",
                listOf(DealExpense(DealExpenseKind.REPAIRS, 120_000.kes), DealExpense(DealExpenseKind.SHOWROOM, 15_000.kes), DealExpense(DealExpenseKind.TRANSFER_FEES, 12_000.kes)),
                2_750_000.kes, 2_680_000.kes, 2_650_000.kes, d(2026, 10, 1), VehicleStatus.SOLD, ""),
            Vehicle("v3", "DEMO 003", "Subaru", "Forester", 2017, d(2026, 9, 10), 1_950_000.kes, "ind", "Good",
                listOf(DealExpense(DealExpenseKind.REPAIRS, 64_000.kes), DealExpense(DealExpenseKind.TRANSFER_FEES, 12_000.kes)),
                2_300_000.kes, 2_250_000.kes, 2_240_000.kes, d(2026, 10, 18), VehicleStatus.SOLD, ""),
            Vehicle("v4", "DEMO 004", "Toyota", "Land Cruiser Prado TX", 2015, d(2026, 10, 2), 4_600_000.kes, "uk", "Very good",
                listOf(DealExpense(DealExpenseKind.REPAIRS, 140_000.kes), DealExpense(DealExpenseKind.SHOWROOM, 20_000.kes), DealExpense(DealExpenseKind.TRANSFER_FEES, 15_000.kes), DealExpense(DealExpenseKind.BROKER_COMMISSION, 60_000.kes)),
                5_400_000.kes, 5_250_000.kes, 5_200_000.kes, d(2026, 11, 9), VehicleStatus.SOLD, "Buyer financed through bank."),
            Vehicle("v5", "DEMO 005", "Volkswagen", "Tiguan", 2017, d(2026, 10, 21), 2_100_000.kes, "partner", "Fair",
                listOf(DealExpense(DealExpenseKind.REPAIRS, 180_000.kes), DealExpense(DealExpenseKind.SHOWROOM, 15_000.kes)),
                2_650_000.kes, 2_550_000.kes, null, null, VehicleStatus.LISTED, "Gearbox service done."),
            Vehicle("v6", "DEMO 006", "Nissan", "X-Trail", 2018, d(2026, 11, 3), 2_250_000.kes, "joint", "Good",
                listOf(DealExpense(DealExpenseKind.REPAIRS, 45_000.kes)),
                2_690_000.kes, 2_620_000.kes, null, null, VehicleStatus.RESERVED, "Deposit of KES 200,000 received."),
            Vehicle("v7", "DEMO 007", "Toyota", "Fielder", 2018, d(2026, 11, 14), 1_480_000.kes, "joint", "Good",
                listOf(DealExpense(DealExpenseKind.REPAIRS, 38_000.kes)),
                null, 1_720_000.kes, null, null, VehicleStatus.IN_PREPARATION, "Detailing and new tyres."),
            Vehicle("v8", "DEMO 008", "Honda", "Vezel Hybrid", 2018, null, 2_050_000.kes, "ind", "Unknown",
                emptyList(), null, 2_400_000.kes, null, null, VehicleStatus.UNDER_CONSIDERATION, "Inspection booked."),
        )

        private fun demoIgniteMonths(): List<IgniteMonth> {
            val m = YearMonth.from(DEMO_AS_OF)
            return listOf(
                IgniteMonth(m.minusMonths(5), 2, 5_100_000.kes, 310_000.kes, 400_000.kes, 3),
                IgniteMonth(m.minusMonths(4), 3, 7_900_000.kes, 455_000.kes, 400_000.kes, 3),
                IgniteMonth(m.minusMonths(3), 1, 2_600_000.kes, 140_000.kes, 400_000.kes, 3),
                IgniteMonth(m.minusMonths(2), 1, 3_280_000.kes, 293_000.kes, 400_000.kes, 3),
                IgniteMonth(m.minusMonths(1), 2, 4_890_000.kes, 417_000.kes, 450_000.kes, 3),
                IgniteMonth(m, 1, 5_200_000.kes, 365_000.kes, 450_000.kes, 3),
            )
        }
    }
}
