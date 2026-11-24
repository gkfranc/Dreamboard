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
    const val BUDGET_CATEGORY = "budget/category/{categoryId}?month={month}"
    fun budgetCategory(id: String, month: java.time.YearMonth) = "budget/category/$id?month=$month"

    const val IGNITE_GRAPH = "ignite_graph"
    const val IGNITE = "ignite"
    const val VEHICLE_DETAIL = "ignite/vehicle/{vehicleId}"
    fun vehicleDetail(id: String) = "ignite/vehicle/$id"

    const val SETTINGS = "settings"
    const val ACCOUNTS = "manage/accounts"
    const val IMPORT = "manage/import"
    const val ASSETS = "manage/assets"
    const val ASSET_EDIT = "manage/asset?assetId={assetId}"
    fun assetEdit(id: String?) = if (id == null) "manage/asset" else "manage/asset?assetId=$id"
    const val DEBT_EDIT = "manage/debt?debtId={debtId}"
    fun debtEdit(id: String?) = if (id == null) "manage/debt" else "manage/debt?debtId=$id"
    const val TX_EDIT = "manage/transaction?txId={txId}"
    fun txEdit(id: String?) = if (id == null) "manage/transaction" else "manage/transaction?txId=$id"
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
