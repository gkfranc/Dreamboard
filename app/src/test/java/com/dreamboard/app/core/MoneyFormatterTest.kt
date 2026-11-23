package com.dreamboard.app.core

import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.core.money.applyBps
import com.dreamboard.app.core.money.ceilToWholeMajor
import com.dreamboard.app.core.money.kes
import com.dreamboard.app.core.money.ratioBps
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    @Test fun fullFormatGroupsThousands() {
        assertEquals("KES 1,480,000", MoneyFormatter.format(1_480_000.kes))
        assertEquals("KES 0", MoneyFormatter.format(0))
        assertEquals("KES 999", MoneyFormatter.format(999.kes))
        assertEquals("KES 1,000", MoneyFormatter.format(1_000.kes))
    }

    @Test fun negativeUsesTrueMinusSign() {
        assertEquals("−KES 2,686,000", MoneyFormatter.format(-2_686_000.kes))
    }

    @Test fun showSignAndCents() {
        assertEquals("+KES 12,345.67", MoneyFormatter.format(1_234_567, showSign = true, showCents = true))
        assertEquals("KES 0.05", MoneyFormatter.format(5, showCents = true))
    }

    @Test fun abbreviation() {
        assertEquals("KES 3.45M", MoneyFormatter.format(3_450_000.kes, abbreviated = true))
        assertEquals("KES 3.97M", MoneyFormatter.format(3_971_500.kes, abbreviated = true))
        assertEquals("KES 1M", MoneyFormatter.format(1_000_000.kes, abbreviated = true))
        assertEquals("KES 845.5K", MoneyFormatter.format(845_500.kes, abbreviated = true))
        assertEquals("KES 1.2B", MoneyFormatter.format(1_200_000_000.kes, abbreviated = true))
        assertEquals("KES 950", MoneyFormatter.format(950.kes, abbreviated = true))
    }

    @Test fun percent() {
        assertEquals("12.3%", MoneyFormatter.percent(1234))
        assertEquals("+5.0%", MoneyFormatter.percent(500, showSign = true))
        assertEquals("−2.5%", MoneyFormatter.percent(-250))
    }

    @Test fun integerHelpers() {
        assertEquals(5_000L, ratioBps(50, 100))
        assertEquals(0L, ratioBps(5, 0))
        assertEquals(333L, applyBps(10_000, 333))
        assertEquals(200L, ceilToWholeMajor(101))
        assertEquals(100L, ceilToWholeMajor(100))
    }
}
