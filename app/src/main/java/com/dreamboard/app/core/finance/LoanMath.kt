package com.dreamboard.app.core.finance

import java.math.BigDecimal
import java.math.RoundingMode

enum class RepaymentStrategy(val label: String, val description: String) {
    AVALANCHE("Avalanche", "Extra cash goes to the highest interest rate first. Usually the lowest total interest."),
    SNOWBALL("Snowball", "Extra cash goes to the smallest balance first. Quick wins build momentum."),
    CUSTOM("Custom", "Extra cash follows the priority order you set."),
}

/** One interest-bearing debt fed into the projection engine. Amounts are minor units. */
data class DebtForProjection(
    val id: String,
    val balanceMinor: Long,
    /** Nominal annual rate in basis points, applied monthly on the reducing balance. */
    val annualRateBps: Int,
    val minimumPaymentMinor: Long,
    val customPriority: Int = 0,
)

data class DebtProjection(
    /** Months until every balance reaches zero, or null if not cleared within the horizon. */
    val monthsToDebtFree: Int?,
    val totalInterestMinor: Long,
    val totalPrincipalMinor: Long,
    val payoffMonthByDebt: Map<String, Int?>,
    val interestByDebt: Map<String, Long>,
    /** Total outstanding at the start of each month; index 0 is today. */
    val balanceSeries: List<Long>,
    /** Cumulative interest paid at the end of each month; index 0 is 0. */
    val cumulativeInterestSeries: List<Long>,
)

data class AmortizationRow(
    val month: Int,
    val openingMinor: Long,
    val interestMinor: Long,
    val principalMinor: Long,
    val closingMinor: Long,
)

/**
 * Reducing-balance loan maths in exact minor units. Interest for a month is
 * balance × annualRate / 12, rounded half-up to the nearest cent.
 * Projections are estimates: real lenders may compound daily, add fees, or change rates.
 */
object LoanMath {
    const val MAX_MONTHS = 600

    fun monthlyInterest(balanceMinor: Long, annualRateBps: Int): Long {
        if (balanceMinor <= 0L || annualRateBps <= 0) return 0L
        return BigDecimal.valueOf(balanceMinor)
            .multiply(BigDecimal.valueOf(annualRateBps.toLong()))
            .divide(BigDecimal.valueOf(120_000L), 0, RoundingMode.HALF_UP)
            .toLong()
    }

    /** Month-by-month schedule for a single loan with an optional fixed extra payment. */
    fun schedule(
        balanceMinor: Long,
        annualRateBps: Int,
        paymentMinor: Long,
        extraMinor: Long = 0L,
        maxMonths: Int = MAX_MONTHS,
    ): List<AmortizationRow> {
        val rows = ArrayList<AmortizationRow>()
        var bal = balanceMinor
        var month = 0
        while (bal > 0 && month < maxMonths) {
            month++
            val interest = monthlyInterest(bal, annualRateBps)
            val due = bal + interest
            val pay = minOf(paymentMinor + extraMinor, due)
            if (pay <= interest && pay < due) {
                // Payment never covers interest: the loan would grow forever. Stop and report.
                rows += AmortizationRow(month, bal, interest, pay - interest, due - pay)
                break
            }
            val principal = pay - interest
            rows += AmortizationRow(month, bal, interest, principal, bal - principal)
            bal -= principal
        }
        return rows
    }

    /**
     * Projects several debts together. Each month every debt accrues interest and receives its
     * minimum payment; the remaining budget (extra + minimums freed by debts already cleared)
     * goes to debts in strategy order.
     */
    fun project(
        debts: List<DebtForProjection>,
        strategy: RepaymentStrategy,
        extraMonthlyMinor: Long,
        maxMonths: Int = MAX_MONTHS,
    ): DebtProjection {
        val balances = LinkedHashMap<String, Long>().apply { debts.forEach { put(it.id, maxOf(0L, it.balanceMinor)) } }
        val interestBy = debts.associate { it.id to 0L }.toMutableMap()
        val payoff = debts.associate<DebtForProjection, String, Int?> { d -> d.id to (if (d.balanceMinor <= 0) 0 else null) }.toMutableMap()
        val monthlyBudget = debts.sumOf { it.minimumPaymentMinor } + maxOf(0L, extraMonthlyMinor)
        val series = arrayListOf(balances.values.sum())
        val cumInterest = arrayListOf(0L)
        var totalInterest = 0L
        var month = 0

        while (balances.values.sum() > 0 && month < maxMonths) {
            month++
            for (d in debts) {
                val bal = balances.getValue(d.id)
                if (bal > 0) {
                    val i = monthlyInterest(bal, d.annualRateBps)
                    balances[d.id] = bal + i
                    interestBy[d.id] = interestBy.getValue(d.id) + i
                    totalInterest += i
                }
            }
            var pool = monthlyBudget
            for (d in debts) {
                val bal = balances.getValue(d.id)
                if (bal > 0 && pool > 0) {
                    val pay = minOf(d.minimumPaymentMinor, bal, pool)
                    balances[d.id] = bal - pay
                    pool -= pay
                }
            }
            for (d in order(debts, balances, strategy)) {
                if (pool <= 0) break
                val bal = balances.getValue(d.id)
                if (bal > 0) {
                    val pay = minOf(pool, bal)
                    balances[d.id] = bal - pay
                    pool -= pay
                }
            }
            for (d in debts) if (payoff[d.id] == null && balances.getValue(d.id) == 0L) payoff[d.id] = month
            series += balances.values.sum()
            cumInterest += totalInterest
            // Bail out early if the debt is growing (payments below interest).
            if (month > 24 && series.last() >= series[series.size - 13]) break
        }
        val cleared = balances.values.sum() == 0L
        val startTotal = debts.sumOf { maxOf(0L, it.balanceMinor) }
        return DebtProjection(
            monthsToDebtFree = if (cleared) month else null,
            totalInterestMinor = totalInterest,
            totalPrincipalMinor = startTotal - balances.values.sum(),
            payoffMonthByDebt = payoff,
            interestByDebt = interestBy,
            balanceSeries = series,
            cumulativeInterestSeries = cumInterest,
        )
    }

    private fun order(
        debts: List<DebtForProjection>,
        balances: Map<String, Long>,
        strategy: RepaymentStrategy,
    ): List<DebtForProjection> = when (strategy) {
        RepaymentStrategy.AVALANCHE -> debts.sortedWith(
            compareByDescending<DebtForProjection> { it.annualRateBps }.thenBy { balances.getValue(it.id) }
        )
        RepaymentStrategy.SNOWBALL -> debts.sortedWith(
            compareBy<DebtForProjection> { balances.getValue(it.id) }.thenByDescending { it.annualRateBps }
        )
        RepaymentStrategy.CUSTOM -> debts.sortedBy { it.customPriority }
    }
}
