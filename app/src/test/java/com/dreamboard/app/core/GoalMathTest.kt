package com.dreamboard.app.core

import com.dreamboard.app.core.finance.GoalAssumptions
import com.dreamboard.app.core.finance.GoalMath
import com.dreamboard.app.core.money.kes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class GoalMathTest {
    private val base = GoalAssumptions(
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
    private val asOf = YearMonth.of(2026, 11)

    @Test fun zeroRatesAreSimpleArithmetic() {
        val a = base.copy(annualReturnBps = 0, annualInflationBps = 0, todayPriceMinor = 1_200_000.kes, currentFundMinor = 0,
            targetYearMonth = asOf.plusMonths(12))
        val p = GoalMath.project(a, asOf)
        assertEquals(12, p.monthsRemaining)
        assertEquals(1_200_000.kes, p.inflatedTargetMinor)
        assertEquals(100_000.kes, p.requiredMonthlyMinor)
    }

    @Test fun inflationRaisesTarget() {
        val p = GoalMath.project(base, asOf)
        assertEquals(109, p.monthsRemaining)
        assertTrue(p.inflatedTargetMinor > base.todayPriceMinor)
        assertTrue(p.remainingMinor == p.inflatedTargetMinor - base.currentFundMinor)
    }

    @Test fun contributingTheRequiredAmountReachesTheTarget() {
        val p = GoalMath.project(base, asOf)
        val check = GoalMath.project(base.copy(monthlyContributionMinor = p.requiredMonthlyMinor), asOf)
        assertTrue(check.projectedFundAtTargetMinor >= p.inflatedTargetMinor)
        val ach = check.estimatedAchievement
        assertNotNull(ach)
        assertTrue(!ach!!.isAfter(base.targetYearMonth))
    }

    @Test fun fundedPercentageUsesOnlyTheFund() {
        val p = GoalMath.project(base, asOf)
        assertTrue(p.fundedBps in 1..10_000)
    }
}
