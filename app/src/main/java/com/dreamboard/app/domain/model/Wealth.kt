package com.dreamboard.app.domain.model

import java.time.LocalDate

/** Whether a figure is a verified actual, an estimate (e.g. a valuation) or a forecast. */
enum class ValueType(val label: String) { ACTUAL("Actual"), ESTIMATED("Estimate"), FORECAST("Forecast") }

enum class AssetCategory(val label: String) {
    CASH_BANK("Cash & bank"),
    MOBILE_MONEY("Mobile money"),
    SACCO_SAVINGS("SACCO savings"),
    INVESTMENTS("Investments"),
    PROPERTY("Property"),
    VEHICLES("Personal vehicles"),
    BUSINESS_EQUITY("Business equity"),
    OTHER("Other assets"),
}

data class Asset(
    val id: String,
    val name: String,
    val category: AssetCategory,
    val valueMinor: Long,
    val institution: String,
    val valueType: ValueType,
    /** True for the dedicated Porsche fund: counts in net worth, and is the only money counted as goal progress. */
    val isGoalFund: Boolean = false,
)

data class NetWorthSnapshot(
    val date: LocalDate,
    val assetsMinor: Long,
    val liabilitiesMinor: Long,
) {
    val netWorthMinor: Long get() = assetsMinor - liabilitiesMinor
}

enum class MilestoneStatus(val label: String) { ACHIEVED("Achieved"), IN_PROGRESS("In progress"), UPCOMING("Upcoming") }

data class Milestone(
    val id: String,
    val year: Int,
    val title: String,
    val description: String,
    val status: MilestoneStatus,
)
