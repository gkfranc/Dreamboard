package com.dreamboard.app.feature.dreamboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.chart.DonutChart
import com.dreamboard.app.core.designsystem.chart.DonutSlice
import com.dreamboard.app.core.designsystem.chart.LineAreaChart
import com.dreamboard.app.core.designsystem.component.ChangePill
import com.dreamboard.app.core.designsystem.component.ChoiceChips
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.DemoDataBanner
import com.dreamboard.app.core.designsystem.component.Dot
import com.dreamboard.app.core.designsystem.component.EmptyState
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ProgressRing
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.Tag
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.domain.model.Milestone
import com.dreamboard.app.domain.model.MilestoneStatus
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy")
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy")

@Composable
fun DreamboardScreen(onOpenGoal: () -> Unit, viewModel: DreamboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Milestone?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(title = "Dreamboard", overline = "Private wealth") }
        if (state.isDemo) item { DemoDataBanner() }
        item { NetWorthHero(state) }
        item { NetWorthHistoryCard(state, viewModel::selectPeriod) }
        item { AllocationCard(state) }
        item { GoalCard(state, onOpenGoal) }
        item { SectionHeader("Financial milestones") }
        item { MilestoneJourney(state.milestones, onEdit = { editing = it }) }
        item {
            Text(
                "Net worth = total personal assets − total personal liabilities. Only your own equity in Ignite Motors is " +
                    "counted; investor and partner capital is excluded.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }

    editing?.let { m ->
        MilestoneDialog(m, onDismiss = { editing = null }, onSave = { viewModel.updateMilestone(it); editing = null })
    }
}

@Composable
private fun NetWorthHero(s: DreamboardUiState) {
    DbCard(contentPadding = 24.dp) {
        Overline("Total net worth", color = DbTheme.colors.gold)
        Spacer(Modifier.height(10.dp))
        MoneyText(s.netWorthMinor, style = MaterialTheme.typography.displayLarge, animate = true)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.change30dMinor != null) {
                ChangePill(s.change30dMinor, s.change30dBps)
                Spacer(Modifier.width(8.dp))
                Text("vs 30 days ago", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            } else {
                Text("Change appears after 30 days of snapshots", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Overline("Assets")
                Spacer(Modifier.height(4.dp))
                MoneyText(s.totalAssetsMinor, style = MaterialTheme.typography.titleMedium, color = DbTheme.colors.positive)
            }
            Column(Modifier.weight(1f)) {
                Overline("Liabilities")
                Spacer(Modifier.height(4.dp))
                MoneyText(s.totalLiabilitiesMinor, style = MaterialTheme.typography.titleMedium, color = DbTheme.colors.negative)
            }
        }
        if (s.unknownLiabilities > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${s.unknownLiabilities} liability balance not yet entered and excluded (salary advance).",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun NetWorthHistoryCard(s: DreamboardUiState, onPeriod: (HistoryPeriod) -> Unit) {
    var selected by remember(s.period, s.history) { mutableStateOf<Int?>(null) }
    DbCard {
        Overline("Net worth history")
        Spacer(Modifier.height(10.dp))
        ChoiceChips(
            options = HistoryPeriod.entries, selected = s.period, label = { it.label }, onSelect = onPeriod,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(12.dp))
        if (s.history.size < 2) {
            EmptyState(
                Icons.Outlined.ShowChart, "History starts today",
                "Dreamboard records a verified snapshot each month. No past values are invented.",
            )
        } else {
            val idx = selected ?: s.history.lastIndex
            val point = s.history[idx]
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(point.netWorthMinor, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(point.date.format(DATE), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
            val first = s.history.first().netWorthMinor
            Text(
                "Period change " + formatMoney(point.netWorthMinor - first, showSign = true),
                style = MaterialTheme.typography.bodySmall,
                color = if (point.netWorthMinor >= first) DbTheme.colors.positive else DbTheme.colors.negative,
            )
            Spacer(Modifier.height(12.dp))
            LineAreaChart(
                values = s.history.map { it.netWorthMinor },
                selectedIndex = selected,
                onSelect = { selected = it },
                contentDescription = "Net worth over ${s.period.label}",
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(s.history.first().date.format(MONTH_YEAR), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
                Text(s.history.last().date.format(MONTH_YEAR), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap or drag the chart to inspect a snapshot.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
        }
    }
}

@Composable
private fun AllocationCard(s: DreamboardUiState) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val palette = DbTheme.colors.chartPalette
    DbCard {
        Overline("Asset allocation")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = s.allocation.mapIndexed { i, a -> DonutSlice(a.valueMinor, palette[i % palette.size]) },
                selectedIndex = selected,
                onSelect = { selected = it },
                size = 140.dp,
            ) {
                val sel = selected?.let { s.allocation.getOrNull(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        sel?.let { MoneyFormatter.percent(it.shareBps, 0) } ?: "${s.allocation.size}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(if (sel == null) "classes" else "of assets", style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.allocation.forEachIndexed { i, a ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { selected = if (selected == i) null else i }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(palette[i % palette.size])
                        Spacer(Modifier.width(8.dp))
                        Text(a.category.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1,
                            color = if (selected == null || selected == i) MaterialTheme.colorScheme.onSurface else DbTheme.colors.textMuted)
                        Text(MoneyFormatter.percent(a.shareBps, 0), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
                    }
                }
            }
        }
        selected?.let { s.allocation.getOrNull(it) }?.let { a ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                MoneyText(a.valueMinor, style = MaterialTheme.typography.titleSmall)
            }
            s.assets.filter { it.category == a.category }.forEach { asset ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.name, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
                    Tag(asset.valueType.label)
                    Spacer(Modifier.width(8.dp))
                    MoneyText(asset.valueMinor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GoalCard(s: DreamboardUiState, onOpen: () -> Unit) {
    val g = s.goalProjection
    DbCard(onClick = onOpen, contentPadding = 0.dp) {
        Box(Modifier.fillMaxWidth().background(DbTheme.colors.surfaceRaised).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("The dream · ${s.goal.targetYearMonth.year}", color = DbTheme.colors.gold, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Open goal", tint = DbTheme.colors.textMuted)
                }
                Text("Porsche Cayenne", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                SuvIllustration()
            }
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(g.fundedBps / 10_000f, MoneyFormatter.percent(g.fundedBps, 1), size = 84.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Overline("Porsche fund progress")
                    Spacer(Modifier.height(4.dp))
                    MoneyText(s.goal.currentFundMinor, style = MaterialTheme.typography.titleLarge)
                    Text("of " + formatMoney(g.inflatedTargetMinor) + " inflation-adjusted", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Overline("Needed monthly")
                    MoneyText(g.requiredMonthlyMinor, style = MaterialTheme.typography.titleSmall)
                }
                Column(Modifier.weight(1f)) {
                    Overline("On current plan")
                    Text(g.estimatedAchievement?.format(MONTH_YEAR) ?: "Beyond 50 yrs", style = MaterialTheme.typography.titleSmall,
                        color = if (g.estimatedAchievement != null && !g.estimatedAchievement.isAfter(s.goal.targetYearMonth)) DbTheme.colors.positive else DbTheme.colors.negative)
                }
            }
            Spacer(Modifier.height(14.dp))
            val passed = s.readiness.count { it.passed }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Ownership readiness")
                Spacer(Modifier.width(8.dp))
                Tag("$passed of ${s.readiness.size} checks", color = if (passed == s.readiness.size) DbTheme.colors.positive else DbTheme.colors.gold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Fund progress counts only money saved for the car. Readiness asks whether your whole position can carry it.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun MilestoneJourney(items: List<Milestone>, onEdit: (Milestone) -> Unit) {
    DbCard(contentPadding = 12.dp) {
        items.forEachIndexed { i, m ->
            val color = when (m.status) {
                MilestoneStatus.ACHIEVED -> DbTheme.colors.positive
                MilestoneStatus.IN_PROGRESS -> DbTheme.colors.gold
                MilestoneStatus.UPCOMING -> DbTheme.colors.textMuted
            }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onEdit(m) }.padding(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                    if (i < items.lastIndex) Box(Modifier.width(2.dp).height(44.dp).background(DbTheme.colors.hairline))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.year}", style = MaterialTheme.typography.labelLarge, color = color)
                        Spacer(Modifier.width(8.dp))
                        Tag(m.status.label, color = color)
                    }
                    Text(m.title, style = MaterialTheme.typography.titleMedium)
                    Text(m.description, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
                Icon(
                    if (m.status == MilestoneStatus.ACHIEVED) Icons.Outlined.CheckCircle else Icons.Outlined.Edit,
                    contentDescription = "Edit milestone", tint = DbTheme.colors.textMuted, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MilestoneDialog(m: Milestone, onDismiss: () -> Unit, onSave: (Milestone) -> Unit) {
    var year by remember { mutableStateOf(m.year.toString()) }
    var title by remember { mutableStateOf(m.title) }
    var description by remember { mutableStateOf(m.description) }
    var status by remember { mutableStateOf(m.status) }
    val yearValue = year.toIntOrNull()
    val valid = yearValue != null && yearValue in 2000..2100 && title.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit milestone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = yearValue == null)
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                ChoiceChips(MilestoneStatus.entries, status, { it.label }, { status = it }, Modifier.horizontalScroll(rememberScrollState()))
                Text("Saved for this session. Permanent storage arrives with the Phase 2 database.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(m.copy(year = yearValue ?: m.year, title = title.trim(), description = description.trim(), status = status)) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
