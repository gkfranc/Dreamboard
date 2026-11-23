package com.dreamboard.app.domain.model

import java.time.LocalDate

enum class VehicleStatus(val label: String, val inStock: Boolean) {
    UNDER_CONSIDERATION("Under consideration", false),
    PURCHASED("Purchased", true),
    IN_PREPARATION("In preparation", true),
    LISTED("Listed for sale", true),
    RESERVED("Reserved", true),
    SOLD("Sold", false),
}

enum class DealExpenseKind(val label: String) {
    REPAIRS("Repairs & preparation"),
    SHOWROOM("Showroom"),
    TRANSFER_FEES("Transfer fees"),
    BROKER_COMMISSION("Broker commission"),
    OTHER("Other"),
}

enum class FundingType(val label: String) {
    JOINT_POOL("Joint business pool"),
    UK_INVESTOR("UK investor-funded"),
    INDIVIDUAL_INVESTOR("Individual investor"),
    PARTNER_FUNDED("Partner-funded"),
}

/**
 * A configurable profit-sharing arrangement. Shares are basis points of net deal profit and
 * must sum to 10,000. Nothing is hard-coded across vehicles: each vehicle references one.
 */
data class FundingArrangement(
    val id: String,
    val name: String,
    val type: FundingType,
    val investorShareBps: Long,
    val partnerShareBps: Long,
    val personalShareBps: Long,
) {
    init {
        require(investorShareBps + partnerShareBps + personalShareBps == 10_000L) { "Shares must sum to 100%" }
    }
}

data class DealExpense(val kind: DealExpenseKind, val amountMinor: Long, val note: String = "")

data class Vehicle(
    val id: String,
    val registration: String,
    val make: String,
    val model: String,
    val year: Int,
    val purchaseDate: LocalDate?,
    val purchasePriceMinor: Long,
    val arrangementId: String,
    val condition: String,
    val expenses: List<DealExpense>,
    val listingPriceMinor: Long?,
    val expectedPriceMinor: Long?,
    val actualPriceMinor: Long?,
    val saleDate: LocalDate?,
    val status: VehicleStatus,
    val notes: String,
) {
    val title: String get() = "$year $make $model"
    val totalExpensesMinor: Long get() = expenses.sumOf { it.amountMinor }
    val totalCostMinor: Long get() = purchasePriceMinor + totalExpensesMinor
}

data class IgniteMonth(
    val month: java.time.YearMonth,
    val unitsSold: Int,
    val revenueMinor: Long,
    val netProfitMinor: Long,
    val targetNetProfitMinor: Long,
    val targetUnits: Int,
)
