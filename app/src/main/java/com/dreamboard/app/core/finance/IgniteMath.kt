package com.dreamboard.app.core.finance

import com.dreamboard.app.core.money.applyBps
import com.dreamboard.app.core.money.ratioBps
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.Vehicle
import com.dreamboard.app.domain.model.VehicleStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class ProfitSplit(
    val netProfitMinor: Long,
    val investorMinor: Long,
    val partnerMinor: Long,
    val personalMinor: Long,
)

data class IgniteSummary(
    val carsInStock: Int,
    val capitalDeployedMinor: Long,
    val totalSalesMinor: Long,
    val monthRevenueMinor: Long,
    val grossProfitMinor: Long,
    val netProfitMinor: Long,
    val personalEarningsMinor: Long,
    val avgProfitPerVehicleMinor: Long,
    val avgDaysToSell: Int,
    val returnOnCapitalBps: Long,
    val soldCount: Int,
)

object IgniteMath {
    /** Net Deal Profit = Selling Price − Purchase Price − All Attributable Deal Expenses. */
    fun netDealProfit(v: Vehicle): Long? = v.actualPriceMinor?.let { it - v.purchasePriceMinor - v.totalExpensesMinor }

    fun grossDealProfit(v: Vehicle): Long? = v.actualPriceMinor?.let { it - v.purchasePriceMinor }

    /** Projected profit for unsold stock using the expected selling price. */
    fun projectedDealProfit(v: Vehicle): Long? = v.expectedPriceMinor?.let { it - v.purchasePriceMinor - v.totalExpensesMinor }

    /** Splits profit exactly: any rounding cent goes to the personal share so parts always sum to the whole. */
    fun split(profitMinor: Long, a: FundingArrangement): ProfitSplit {
        val investor = applyBps(profitMinor, a.investorShareBps)
        val partner = applyBps(profitMinor, a.partnerShareBps)
        return ProfitSplit(profitMinor, investor, partner, profitMinor - investor - partner)
    }

    fun daysInStock(v: Vehicle, asOf: LocalDate): Int? {
        val start = v.purchaseDate ?: return null
        val end = v.saleDate ?: asOf
        return ChronoUnit.DAYS.between(start, end).toInt()
    }

    fun summarize(vehicles: List<Vehicle>, arrangements: Map<String, FundingArrangement>, asOf: LocalDate): IgniteSummary {
        val stock = vehicles.filter { it.status.inStock }
        val sold = vehicles.filter { it.status == VehicleStatus.SOLD && it.actualPriceMinor != null }
        val net = sold.sumOf { netDealProfit(it) ?: 0L }
        val personal = sold.sumOf { v ->
            val a = arrangements[v.arrangementId]
            if (a == null) 0L else split(netDealProfit(v) ?: 0L, a).personalMinor
        }
        val month = YearMonth.from(asOf)
        val soldCapital = sold.sumOf { it.totalCostMinor }
        return IgniteSummary(
            carsInStock = stock.size,
            capitalDeployedMinor = stock.sumOf { it.totalCostMinor },
            totalSalesMinor = sold.sumOf { it.actualPriceMinor ?: 0L },
            monthRevenueMinor = sold.filter { it.saleDate != null && YearMonth.from(it.saleDate) == month }
                .sumOf { it.actualPriceMinor ?: 0L },
            grossProfitMinor = sold.sumOf { grossDealProfit(it) ?: 0L },
            netProfitMinor = net,
            personalEarningsMinor = personal,
            avgProfitPerVehicleMinor = if (sold.isEmpty()) 0L else net / sold.size,
            avgDaysToSell = sold.mapNotNull { daysInStock(it, asOf) }.let { if (it.isEmpty()) 0 else it.sum() / it.size },
            returnOnCapitalBps = ratioBps(net, soldCapital),
            soldCount = sold.size,
        )
    }
}
