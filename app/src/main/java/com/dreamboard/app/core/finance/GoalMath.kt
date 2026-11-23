package com.dreamboard.app.core.finance

import com.dreamboard.app.core.money.applyBps
import com.dreamboard.app.core.money.ceilToWholeMajor
import com.dreamboard.app.core.money.ratioBps
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Editable assumptions for a long-term purchase goal. All amounts are minor units. */
data class GoalAssumptions(
    val targetYearMonth: YearMonth,
    /** Price of the vehicle in today's money. */
    val todayPriceMinor: Long,
    /** Money actually saved or invested for this goal (not net worth). */
    val currentFundMinor: Long,
    val monthlyContributionMinor: Long,
    val annualReturnBps: Int,
    val annualInflationBps: Int,
    val insuranceRateBps: Int,
    val annualMaintenanceMinor: Long,
    val annualFuelMinor: Long,
)

data class GoalProjection(
    val monthsRemaining: Int,
    val inflatedTargetMinor: Long,
    val fundedBps: Long,
    val remainingMinor: Long,
    val requiredMonthlyMinor: Long,
    val projectedFundAtTargetMinor: Long,
    /** First month the projected fund covers the inflation-adjusted price, or null beyond 50 years. */
    val estimatedAchievement: YearMonth?,
    /** Insurance + maintenance + fuel in the first year of ownership, at the inflated price. */
    val annualOwnershipCostMinor: Long,
)

/**
 * Goal maths using BigDecimal. Annual rates are converted to monthly by dividing by 12
 * (nominal monthly compounding). Results are forecasts, not guarantees.
 */
object GoalMath {
    private val MC = MathContext.DECIMAL64
    private const val HORIZON = 600

    fun project(a: GoalAssumptions, asOf: YearMonth): GoalProjection {
        val n = maxOf(0, ChronoUnit.MONTHS.between(asOf, a.targetYearMonth).toInt())
        val r = monthlyRate(a.annualReturnBps)
        val i = monthlyRate(a.annualInflationBps)

        val inflated = BigDecimal.valueOf(a.todayPriceMinor).multiply(i.add(BigDecimal.ONE).pow(n, MC), MC)
        val inflatedMinor = inflated.setScale(0, RoundingMode.HALF_UP).toLong()

        val growth = r.add(BigDecimal.ONE).pow(n, MC)
        val fundFv = BigDecimal.valueOf(a.currentFundMinor).multiply(growth, MC)
        val contribFv = if (r.signum() == 0) {
            BigDecimal.valueOf(a.monthlyContributionMinor).multiply(BigDecimal.valueOf(n.toLong()))
        } else {
            BigDecimal.valueOf(a.monthlyContributionMinor).multiply(growth.subtract(BigDecimal.ONE).divide(r, MC), MC)
        }
        val projectedAtTarget = fundFv.add(contribFv).setScale(0, RoundingMode.HALF_UP).toLong()

        val gap = inflated.subtract(fundFv)
        val required = when {
            gap.signum() <= 0 -> 0L
            n == 0 -> gap.setScale(0, RoundingMode.UP).toLong()
            r.signum() == 0 -> gap.divide(BigDecimal.valueOf(n.toLong()), 0, RoundingMode.UP).toLong()
            else -> gap.multiply(r, MC).divide(growth.subtract(BigDecimal.ONE), 0, RoundingMode.UP).toLong()
        }

        return GoalProjection(
            monthsRemaining = n,
            inflatedTargetMinor = inflatedMinor,
            fundedBps = minOf(10_000L, ratioBps(a.currentFundMinor, inflatedMinor)),
            remainingMinor = maxOf(0L, inflatedMinor - a.currentFundMinor),
            requiredMonthlyMinor = ceilToWholeMajor(required),
            projectedFundAtTargetMinor = projectedAtTarget,
            estimatedAchievement = achievementMonth(a, asOf, r, i),
            annualOwnershipCostMinor = applyBps(inflatedMinor, a.insuranceRateBps.toLong()) +
                a.annualMaintenanceMinor + a.annualFuelMinor,
        )
    }

    private fun achievementMonth(a: GoalAssumptions, asOf: YearMonth, r: BigDecimal, i: BigDecimal): YearMonth? {
        var fund = BigDecimal.valueOf(a.currentFundMinor)
        var price = BigDecimal.valueOf(a.todayPriceMinor)
        val contrib = BigDecimal.valueOf(a.monthlyContributionMinor)
        if (fund >= price) return asOf
        for (m in 1..HORIZON) {
            fund = fund.multiply(r.add(BigDecimal.ONE), MC).add(contrib)
            price = price.multiply(i.add(BigDecimal.ONE), MC)
            if (fund >= price) return asOf.plusMonths(m.toLong())
        }
        return null
    }

    private fun monthlyRate(annualBps: Int): BigDecimal =
        BigDecimal.valueOf(annualBps.toLong()).divide(BigDecimal.valueOf(120_000L), MC)
}
