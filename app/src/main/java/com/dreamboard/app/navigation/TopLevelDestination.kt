package com.dreamboard.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DREAMBOARD_GRAPH = "dreamboard_graph"
    const val DREAMBOARD = "dreamboard"
    const val PORSCHE_GOAL = "dreamboard/porsche"

    const val DEBT_GRAPH = "debt_graph"
    const val DEBT = "debt"
    const val DEBT_DETAIL = "debt/detail/{debtId}"
    fun debtDetail(id: String) = "debt/detail/$id"

    const val BUDGET_GRAPH = "budget_graph"
    const val BUDGET = "budget"
    const val BUDGET_CATEGORY = "budget/category/{categoryId}"
    fun budgetCategory(id: String) = "budget/category/$id"

    const val IGNITE_GRAPH = "ignite_graph"
    const val IGNITE = "ignite"
    const val VEHICLE_DETAIL = "ignite/vehicle/{vehicleId}"
    fun vehicleDetail(id: String) = "ignite/vehicle/$id"

    const val SETTINGS = "settings"
}

enum class TopLevelDestination(
    val graphRoute: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    DREAMBOARD(Routes.DREAMBOARD_GRAPH, "Dreamboard", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    DEBT(Routes.DEBT_GRAPH, "Debt", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance),
    BUDGET(Routes.BUDGET_GRAPH, "Budget", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    IGNITE(Routes.IGNITE_GRAPH, "Ignite", Icons.Filled.LocalFireDepartment, Icons.Outlined.LocalFireDepartment),
}
