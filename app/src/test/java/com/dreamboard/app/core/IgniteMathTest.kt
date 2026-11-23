package com.dreamboard.app.core

import com.dreamboard.app.core.finance.IgniteMath
import com.dreamboard.app.core.money.kes
import com.dreamboard.app.domain.model.DealExpense
import com.dreamboard.app.domain.model.DealExpenseKind
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.FundingType
import com.dreamboard.app.domain.model.Vehicle
import com.dreamboard.app.domain.model.VehicleStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class IgniteMathTest {
    private val uk = FundingArrangement("uk", "UK", FundingType.UK_INVESTOR, 6_000, 2_000, 2_000)

    private fun car(price: Long?, status: VehicleStatus = VehicleStatus.SOLD) = Vehicle(
        "v", "DEMO", "Toyota", "Harrier", 2017, LocalDate.of(2026, 8, 4), 2_850_000.kes, "uk", "Good",
        listOf(DealExpense(DealExpenseKind.REPAIRS, 85_000.kes), DealExpense(DealExpenseKind.TRANSFER_FEES, 12_000.kes), DealExpense(DealExpenseKind.BROKER_COMMISSION, 40_000.kes)),
        3_350_000.kes, 3_300_000.kes, price, if (price != null) LocalDate.of(2026, 9, 2) else null, status, "",
    )

    @Test fun netDealProfitSubtractsAllExpenses() {
        assertEquals(293_000.kes, IgniteMath.netDealProfit(car(3_280_000.kes)))
        assertEquals(430_000.kes, IgniteMath.grossDealProfit(car(3_280_000.kes)))
    }

    @Test fun splitAlwaysSumsExactly() {
        val odd = 1_000_001L // forces rounding
        val s = IgniteMath.split(odd, uk)
        assertEquals(odd, s.investorMinor + s.partnerMinor + s.personalMinor)
    }

    @Test fun summaryCountsPersonalShareOnly() {
        val sum = IgniteMath.summarize(listOf(car(3_280_000.kes)), mapOf("uk" to uk), LocalDate.of(2026, 11, 23))
        assertEquals(293_000.kes, sum.netProfitMinor)
        assertEquals(58_600.kes, sum.personalEarningsMinor) // 20% of 293,000
        assertEquals(29, sum.avgDaysToSell)
        assertEquals(0, sum.carsInStock)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sharesMustSumTo100() {
        FundingArrangement("bad", "Bad", FundingType.JOINT_POOL, 5_000, 5_000, 1)
    }
}
