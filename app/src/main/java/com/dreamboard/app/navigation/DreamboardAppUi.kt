package com.dreamboard.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dreamboard.app.core.designsystem.component.AppActions
import com.dreamboard.app.core.designsystem.component.DisplayPrefs
import com.dreamboard.app.core.designsystem.component.LocalAppActions
import com.dreamboard.app.core.designsystem.component.LocalDisplayPrefs
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.data.prefs.UserPreferences
import com.dreamboard.app.feature.budget.BudgetCategoryScreen
import com.dreamboard.app.feature.budget.BudgetScreen
import com.dreamboard.app.feature.debt.DebtDetailScreen
import com.dreamboard.app.feature.debt.DebtScreen
import com.dreamboard.app.feature.dreamboard.DreamboardScreen
import com.dreamboard.app.feature.dreamboard.PorscheGoalScreen
import com.dreamboard.app.feature.ignite.IgniteScreen
import com.dreamboard.app.feature.ignite.VehicleDetailScreen
import com.dreamboard.app.feature.settings.SettingsScreen

@Composable
fun DreamboardAppUi(preferences: UserPreferences, onTogglePrivacy: () -> Unit) {
    val navController = rememberNavController()
    val actions = remember(navController, onTogglePrivacy) {
        AppActions(
            togglePrivacy = onTogglePrivacy,
            openSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
        )
    }
    CompositionLocalProvider(
        LocalDisplayPrefs provides DisplayPrefs(preferences.privacyMode, preferences.abbreviateAmounts),
        LocalAppActions provides actions,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { DreamboardBottomBar(navController) },
        ) { padding ->
            DreamboardNavHost(navController, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DreamboardBottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = destination?.hierarchy?.any { it.route == dest.graphRoute } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(dest.graphRoute) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DbTheme.colors.gold,
                    selectedTextColor = DbTheme.colors.gold,
                    indicatorColor = DbTheme.colors.gold.copy(alpha = 0.14f),
                    unselectedIconColor = DbTheme.colors.textMuted,
                    unselectedTextColor = DbTheme.colors.textMuted,
                ),
            )
        }
    }
}

@Composable
private fun DreamboardNavHost(navController: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = Routes.DREAMBOARD_GRAPH,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        navigation(startDestination = Routes.DREAMBOARD, route = Routes.DREAMBOARD_GRAPH) {
            composable(Routes.DREAMBOARD) {
                DreamboardScreen(onOpenGoal = { navController.navigate(Routes.PORSCHE_GOAL) })
            }
            composable(Routes.PORSCHE_GOAL) { PorscheGoalScreen(onBack = back) }
        }
        navigation(startDestination = Routes.DEBT, route = Routes.DEBT_GRAPH) {
            composable(Routes.DEBT) {
                DebtScreen(onOpenDebt = { id -> navController.navigate(Routes.debtDetail(id)) })
            }
            composable(Routes.DEBT_DETAIL, arguments = listOf(navArgument("debtId") { type = NavType.StringType })) {
                DebtDetailScreen(onBack = back)
            }
        }
        navigation(startDestination = Routes.BUDGET, route = Routes.BUDGET_GRAPH) {
            composable(Routes.BUDGET) {
                BudgetScreen(onOpenCategory = { id -> navController.navigate(Routes.budgetCategory(id)) })
            }
            composable(Routes.BUDGET_CATEGORY, arguments = listOf(navArgument("categoryId") { type = NavType.StringType })) {
                BudgetCategoryScreen(onBack = back)
            }
        }
        navigation(startDestination = Routes.IGNITE, route = Routes.IGNITE_GRAPH) {
            composable(Routes.IGNITE) {
                IgniteScreen(onOpenVehicle = { id -> navController.navigate(Routes.vehicleDetail(id)) })
            }
            composable(Routes.VEHICLE_DETAIL, arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })) {
                VehicleDetailScreen(onBack = back)
            }
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
    }
}
