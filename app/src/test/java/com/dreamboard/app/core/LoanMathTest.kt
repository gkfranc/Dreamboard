package com.dreamboard.app.core

import com.dreamboard.app.core.finance.DebtForProjection
import com.dreamboard.app.core.finance.LoanMath
import com.dreamboard.app.core.finance.RepaymentStrategy
import com.dreamboard.app.core.money.kes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanMathTest {
    @Test fun monthlyInterestIsExactAndRoundedHalfUp() {
        // 1% per month on KES 666,000 = KES 6,660
        assertEquals(6_660.kes, LoanMath.monthlyInterest(666_000.kes, 1200))
        assertEquals(0L, LoanMath.monthlyInterest(100.kes, 0))
        // 1 cent at 18% p.a. → 0.015 cents → rounds to 0
        assertEquals(0L, LoanMath.monthlyInterest(1, 1800))
    }

    @Test fun scheduleClosesToZeroAndConservesPrincipal() {
        val rows = LoanMath.schedule(666_000.kes, 1200, 25_000.kes)
        assertEquals(0L, rows.last().closingMinor)
        assertEquals(666_000.kes, rows.sumOf { it.principalMinor })
        rows.forEach { assertEquals(it.openingMinor - it.principalMinor, it.closingMinor) }
    }

    @Test fun extraPaymentShortensTermAndCutsInterest() {
        val base = LoanMath.schedule(1_480_000.kes, 1650, 42_000.kes)
        val fast = LoanMath.schedule(1_480_000.kes, 1650, 42_000.kes, extraMinor = 20_000.kes)
        assertTrue(fast.size < base.size)
        assertTrue(fast.sumOf { it.interestMinor } < base.sumOf { it.interestMinor })
    }

    @Test fun paymentBelowInterestIsDetected() {
        val p = LoanMath.project(listOf(DebtForProjection("x", 1_000_000.kes, 3600, 10_000.kes)), RepaymentStrategy.AVALANCHE, 0)
        assertNull(p.monthsToDebtFree)
    }

    private val debts = listOf(
        DebtForProjection("absa", 1_480_000.kes, 1650, 42_000.kes, customPriority = 3),
        DebtForProjection("sacco", 666_000.kes, 1200, 25_000.kes, customPriority = 4),
        DebtForProjection("card", 200_000.kes, 3600, 12_000.kes, customPriority = 1),
        DebtForProjection("family", 200_000.kes, 0, 10_000.kes, customPriority = 2),
    )

    @Test fun projectionClearsAllDebtAndPrincipalIsConserved() {
        val p = LoanMath.project(debts, RepaymentStrategy.AVALANCHE, 0)
        assertNotNull(p.monthsToDebtFree)
        assertEquals(debts.sumOf { it.balanceMinor }, p.totalPrincipalMinor)
        assertEquals(0L, p.balanceSeries.last())
        assertEquals(p.totalInterestMinor, p.interestByDebt.values.sum())
    }

    @Test fun avalancheNeverCostsMoreInterestThanSnowball() {
        val a = LoanMath.project(debts, RepaymentStrategy.AVALANCHE, 30_000.kes)
        val s = LoanMath.project(debts, RepaymentStrategy.SNOWBALL, 30_000.kes)
        assertTrue(a.totalInterestMinor <= s.totalInterestMinor)
    }

    @Test fun extraMoneyNeverMakesThingsWorse() {
        val base = LoanMath.project(debts, RepaymentStrategy.AVALANCHE, 0)
        val extra = LoanMath.project(debts, RepaymentStrategy.AVALANCHE, 50_000.kes)
        assertTrue(extra.monthsToDebtFree!! < base.monthsToDebtFree!!)
        assertTrue(extra.totalInterestMinor < base.totalInterestMinor)
    }

    @Test fun snowballClearsSmallestFirst() {
        val s = LoanMath.project(debts, RepaymentStrategy.SNOWBALL, 40_000.kes)
        val card = s.payoffMonthByDebt.getValue("card")!!
        val absa = s.payoffMonthByDebt.getValue("absa")!!
        assertTrue(card < absa)
    }
}
