package com.dreamboard.app.feature.ignite

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.chart.BarGroup
import com.dreamboard.app.core.designsystem.chart.GroupedBarChart
import com.dreamboard.app.core.designsystem.chart.StackedBar
import com.dreamboard.app.core.designsystem.component.CardRow
import com.dreamboard.app.core.designsystem.component.ChoiceChips
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.DemoDataBanner
import com.dreamboard.app.core.designsystem.component.Dot
import com.dreamboard.app.core.designsystem.component.EmptyState
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.KpiCard
import com.dreamboard.app.core.designsystem.component.MoneyInfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.StatCard
import com.dreamboard.app.core.designsystem.component.Tag
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.finance.IgniteMath
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.domain.model.Vehicle
import com.dreamboard.app.domain.model.VehicleStatus
import java.time.format.DateTimeFormatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import com.dreamboard.app.core.designsystem.component.MoneyInput
import com.dreamboard.app.core.designsystem.component.PrimaryAction
import com.dreamboard.app.core.designsystem.component.SecondaryAction
import com.dreamboard.app.core.designsystem.component.TextInput
import com.dreamboard.app.core.designsystem.component.moneyToInput
import com.dreamboard.app.core.designsystem.component.parseMoneyInput
import com.dreamboard.app.domain.model.IgniteTargets

private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM")
private val MONTH_FULL = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
internal fun statusColor(s: VehicleStatus): Color = when (s) {
    VehicleStatus.SOLD -> DbTheme.colors.positive
    VehicleStatus.RESERVED, VehicleStatus.LISTED -> DbTheme.colors.gold
    VehicleStatus.UNDER_CONSIDERATION -> DbTheme.colors.textMuted
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun IgniteScreen(
    onOpenVehicle: (String) -> Unit,
    onAddVehicle: () -> Unit,
    onImportTracker: () -> Unit,
    viewModel: IgniteViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val sum = s.summary
    var editingTargets by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Ignite", overline = "Ignite Motors · business") }
        if (s.isDemo) item { DemoDataBanner("Demonstration vehicles and deals (registrations marked DEMO). Business figures are kept separate from personal net worth.") }
        if (s.vehicleCount == 0) {
            item {
                DbCard {
                    EmptyState(Icons.Outlined.DirectionsCar, "No cars yet",
                        "Import your flipping tracker (Excel or Google Sheet) to bring in every deal with its costs and Peter's share, or add a car by hand.")
                    PrimaryAction("Import flipping tracker", onImportTracker, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction("Add a car", onAddVehicle, Modifier.fillMaxWidth())
                }
            }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryAction("Add car", onAddVehicle, Modifier.weight(1f))
                SecondaryAction("Import tracker", onImportTracker, Modifier.weight(1f))
            }
        }
        item {
            DbCard(contentPadding = 24.dp) {
                Overline("Net profit · all completed deals", color = DbTheme.colors.gold)
                Spacer(Modifier.height(8.dp))
                MoneyText(sum.netProfitMinor, style = MaterialTheme.typography.displayMedium, animate = true)
                Spacer(Modifier.height(12.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Overline("Your earnings")
                        MoneyText(sum.personalEarningsMinor, style = MaterialTheme.typography.titleMedium, color = DbTheme.colors.positive)
                    }
                    Column(Modifier.weight(1f)) {
                        Overline("Gross profit")
                        MoneyText(sum.grossProfitMinor, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your earnings are your profit entitlement under each deal's sharing rule. They reach personal cash flow only when a distribution is actually received.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                )
            }
        }
        item {
            CardRow { m ->
                StatCard("Cars in stock", "${sum.carsInStock}", m, caption = "Purchased → reserved")
                KpiCard("Capital deployed", sum.capitalDeployedMinor, m, caption = "Cost of stock incl. prep")
            }
        }
        item {
            CardRow { m ->
                KpiCard("Partner shares", s.partnerSharesMinor, m, caption = "Owed or paid to partners")
                KpiCard("Revenue this month", sum.monthRevenueMinor, m, caption = s.asOf.format(MONTH_FULL))
            }
        }
        item {
            CardRow { m ->
                KpiCard("Total sales", sum.totalSalesMinor, m, caption = "${sum.soldCount} completed deals")
                KpiCard("Avg profit / car", sum.avgProfitPerVehicleMinor, m, caption = "Net, after expenses")
            }
        }
        item {
            CardRow { m ->
                StatCard("Avg days to sell", "${sum.avgDaysToSell}", m, caption = "Purchase → sale")
                StatCard("Return on capital", MoneyFormatter.percent(sum.returnOnCapitalBps), m,
                    caption = "Net profit ÷ cost of sold cars", valueColor = DbTheme.colors.positive)
            }
        }
        item {
            CardRow { m ->
                StatCard("Inventory turnover", "${s.inventoryTurnoverX10 / 10}.${s.inventoryTurnoverX10 % 10}×", m, caption = "Sales (6 mo) ÷ current stock")
                StatCard("Sold to date", "${sum.soldCount}", m, caption = "Completed deals")
            }
        }
        item { MonthlyCard(s, onSetTargets = { editingTargets = true }) }
        item { SectionHeader("Inventory") }
        item {
            ChoiceChips(InventoryFilter.entries, s.filter, { it.label }, viewModel::setFilter, Modifier.horizontalScroll(rememberScrollState()))
        }
        if (s.vehicles.isEmpty()) {
            item { EmptyState(Icons.Outlined.DirectionsCar, "No vehicles here", "Vehicles with this status will appear here.") }
        } else {
            items(s.vehicles, key = { it.id }) { v -> VehicleRow(v, s, onClick = { onOpenVehicle(v.id) }) }
        }
        item { SectionHeader("Profit by investor pool") }
        item { PoolsCard(s) }
        item { SectionHeader("Deal profitability ranking") }
        item { RankingCard(s, onOpenVehicle) }
        item {
            Text(
                "Only your verified ownership equity in Ignite counts towards personal net worth. Investor and partner capital never does.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
    if (editingTargets) {
        TargetsDialog(s.targets, onDismiss = { editingTargets = false }, onSave = { viewModel.setTargets(it); editingTargets = false })
    }
}

@Composable
private fun TargetsDialog(current: IgniteTargets, onDismiss: () -> Unit, onSave: (IgniteTargets) -> Unit) {
    var profit by remember { mutableStateOf(moneyToInput(current.netProfitMinor)) }
    var units by remember { mutableStateOf(if (current.units > 0) current.units.toString() else "") }
    val profitMinor = parseMoneyInput(profit)
    val unitsValue = if (units.isBlank()) 0 else units.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly targets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyInput(profit, { profit = it }, "Net profit per month", supporting = "Use 0 for no target.")
                TextInput(units, { units = it.filter(Char::isDigit).take(3) }, "Cars sold per month", keyboard = KeyboardType.Number)
            }
        },
        confirmButton = {
            TextButton(enabled = profitMinor != null && unitsValue != null, onClick = { onSave(IgniteTargets(profitMinor ?: 0L, unitsValue ?: 0)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MonthlyCard(s: IgniteUiState, onSetTargets: () -> Unit) {
    var selected by remember(s.months) { mutableStateOf<Int?>(null) }
    val m = s.months.getOrNull(selected ?: s.months.lastIndex)
    DbCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(if (s.targets.netProfitMinor > 0) "Monthly net profit vs target" else "Monthly net profit", modifier = Modifier.weight(1f))
            TextButton(onClick = onSetTargets) { Text(if (s.targets.netProfitMinor > 0 || s.targets.units > 0) "Edit targets" else "Set targets", color = DbTheme.colors.gold) }
        }
        Spacer(Modifier.height(8.dp))
        if (m != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(m.netProfitMinor, style = MaterialTheme.typography.titleLarge,
                    color = if (m.netProfitMinor >= m.targetNetProfitMinor) DbTheme.colors.positive else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(m.month.format(MONTH_FULL), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(
                listOfNotNull(
                    m.targetNetProfitMinor.takeIf { it > 0 }?.let { "Target ${formatMoney(it)}" },
                    if (m.targetUnits > 0) "${m.unitsSold} of ${m.targetUnits} cars sold" else "${m.unitsSold} cars sold",
                    "revenue ${formatMoney(m.revenueMinor, forceAbbreviate = true)}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(12.dp))
        GroupedBarChart(
            groups = s.months.map { BarGroup(it.month.format(MONTH_SHORT), it.netProfitMinor, target = it.targetNetProfitMinor.takeIf { t -> t > 0 }) },
            selectedIndex = selected,
            onSelect = { selected = it },
            primaryColor = DbTheme.colors.gold,
        )
        Spacer(Modifier.height(6.dp))
        Text(if (s.targets.netProfitMinor > 0) "Dashed line marks the monthly target. Tap a month for detail." else "Tap a month for detail.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
        Spacer(Modifier.height(10.dp))
        MoneyInfoRow("Projected profit on current stock", s.projectedStockProfitMinor, valueColor = DbTheme.colors.gold)
        Text("Forecast from expected selling prices; not yet realized.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
    }
}

@Composable
private fun VehicleRow(v: Vehicle, s: IgniteUiState, onClick: () -> Unit) {
    val days = IgniteMath.daysInStock(v, s.asOf)
    val profit = IgniteMath.netDealProfit(v)
    val projected = IgniteMath.projectedDealProfit(v)
    DbCard(onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(DbTheme.colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = DbTheme.colors.gold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(listOf(v.registration, s.arrangements[v.arrangementId]?.name ?: "Unassigned").filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                when {
                    profit != null -> MoneyText(profit, style = MaterialTheme.typography.titleSmall, color = if (profit >= 0) DbTheme.colors.positive else DbTheme.colors.negative, showSign = true)
                    projected != null -> Text("~" + formatMoney(projected, showSign = true, forceAbbreviate = true), style = MaterialTheme.typography.titleSmall, color = DbTheme.colors.textMuted)
                }
                Spacer(Modifier.height(2.dp))
                Tag(v.status.label, color = statusColor(v.status))
            }
        }
        if (days != null && v.status.inStock) {
            Spacer(Modifier.height(8.dp))
            Text("$days days in stock", style = MaterialTheme.typography.bodySmall,
                color = if (days > 45) DbTheme.colors.negative else DbTheme.colors.textMuted)
        }
    }
}

@Composable
private fun PoolsCard(s: IgniteUiState) {
    val c = DbTheme.colors
    DbCard {
        s.pools.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.arrangement.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Investor ${MoneyFormatter.percent(p.arrangement.investorShareBps, 0)} · partner ${MoneyFormatter.percent(p.arrangement.partnerShareBps, 0)} · you ${MoneyFormatter.percent(p.arrangement.personalShareBps, 0)}",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    )
                }
                MoneyText(p.split.netProfitMinor, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            if (p.split.netProfitMinor > 0) {
                StackedBar(listOf(p.split.investorMinor to c.chartPalette[2], p.split.partnerMinor to c.chartPalette[3], p.split.personalMinor to c.positive), height = 8.dp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniLegend("Investor", p.split.investorMinor, c.chartPalette[2])
                    MiniLegend("Partner", p.split.partnerMinor, c.chartPalette[3])
                    MiniLegend("You", p.split.personalMinor, c.positive)
                }
            } else {
                Text("${p.deals} completed deals", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            if (p.capitalInStockMinor > 0) {
                InfoRow("Capital in current stock", formatMoney(p.capitalInStockMinor))
            }
        }
    }
}

@Composable
private fun MiniLegend(label: String, v: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 6.dp)
        Spacer(Modifier.width(4.dp))
        Text("$label ${formatMoney(v, forceAbbreviate = true)}", style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
    }
}

@Composable
private fun RankingCard(s: IgniteUiState, onOpen: (String) -> Unit) {
    DbCard(contentPadding = 12.dp) {
        if (s.ranking.isEmpty()) {
            EmptyState(Icons.Outlined.DirectionsCar, "No completed deals", "Sold vehicles are ranked by net profit.")
        }
        s.ranking.forEachIndexed { i, (v, p) ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .clickable { onOpen(v.id) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = DbTheme.colors.gold, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, style = MaterialTheme.typography.bodyMedium)
                    Text("${IgniteMath.daysInStock(v, s.asOf) ?: 0} days · margin " +
                        MoneyFormatter.percent(com.dreamboard.app.core.money.ratioBps(p, v.totalCostMinor)),
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
                MoneyText(p, style = MaterialTheme.typography.titleSmall, color = DbTheme.colors.positive)
            }
        }
        if (s.byModel.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Overline("Profit by model", modifier = Modifier.padding(horizontal = 8.dp))
            s.byModel.forEach { (model, p) -> InfoRow(model, formatMoney(p), Modifier.padding(horizontal = 8.dp)) }
        }
    }
}
