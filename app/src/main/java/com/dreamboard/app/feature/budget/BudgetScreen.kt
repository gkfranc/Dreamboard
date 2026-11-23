package com.dreamboard.app.feature.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.chart.BarGroup
import com.dreamboard.app.core.designsystem.chart.GroupedBarChart
import com.dreamboard.app.core.designsystem.component.CardRow
import com.dreamboard.app.core.designsystem.component.ChoiceChips
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.DemoDataBanner
import com.dreamboard.app.core.designsystem.component.EmptyState
import com.dreamboard.app.core.designsystem.component.KpiCard
import com.dreamboard.app.core.designsystem.component.MoneyInfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ProgressRing
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.SlimProgress
import com.dreamboard.app.core.designsystem.component.Tag
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.domain.model.Transaction
import java.time.format.DateTimeFormatter

private val MONTH_FULL = DateTimeFormatter.ofPattern("MMMM yyyy")
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM")
internal val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun BudgetScreen(onOpenCategory: (String) -> Unit, viewModel: BudgetViewModel = hiltViewModel()) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Budget & Expenses", overline = s.month.format(MONTH_FULL)) }
        if (s.isDemo) item { DemoDataBanner() }
        item { BudgetHero(s) }
        if (s.alerts.isNotEmpty()) item { AlertsCard(s, onOpenCategory) }
        item {
            CardRow { m -> KpiCard("Income", s.incomeMinor, m, amountColor = DbTheme.colors.positive, caption = "Received this month") }
        }
        item {
            CardRow { m ->
                KpiCard("Cash-flow surplus", s.surplusMinor, m,
                    amountColor = if (s.surplusMinor >= 0) DbTheme.colors.positive else DbTheme.colors.negative,
                    caption = "Income − spending, before savings & repayments")
            }
        }
        item { CommitmentsCard(s) }
        item { TrendCard(s) }
        item { SectionHeader("Categories") }
        item { CategoriesCard(s, onOpenCategory) }
        item { MerchantsCard(s) }
        item { SectionHeader("Transactions") }
        item { TransactionFilters(s, viewModel) }
        if (s.transactions.isEmpty()) {
            item { EmptyState(Icons.Outlined.ReceiptLong, "No matching transactions", "Try a different search or filter.") }
        } else {
            items(s.transactions, key = { it.id }) { t -> TransactionRow(t, s.categoryNames[t.categoryId]) }
        }
        item {
            Text(
                "Manual entry arrives with the Phase 2 database; CSV, XLSX and M-Pesa statement imports in Phase 5. " +
                    "Dreamboard never asks for online-banking passwords.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun BudgetHero(s: BudgetUiState) {
    DbCard(contentPadding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Remaining budget", color = DbTheme.colors.gold)
                Spacer(Modifier.height(8.dp))
                MoneyText(s.remainingMinor, style = MaterialTheme.typography.displaySmall,
                    color = if (s.remainingMinor >= 0) MaterialTheme.colorScheme.onSurface else DbTheme.colors.negative, animate = true)
                Spacer(Modifier.height(4.dp))
                Text("of " + formatMoney(s.budgetMinor) + " budget", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            }
            ProgressRing(
                s.usedBps / 10_000f, MoneyFormatter.percent(s.usedBps, 0), size = 88.dp,
                color = if (s.usedBps > 10_000) DbTheme.colors.negative else DbTheme.colors.gold,
            )
        }
        Spacer(Modifier.height(16.dp))
        MoneyInfoRow("Actual spending", s.spentMinor)
        MoneyInfoRow("Budget variance", s.remainingMinor, valueColor = if (s.remainingMinor >= 0) DbTheme.colors.positive else DbTheme.colors.negative)
        MoneyInfoRow(
            "Projected month-end spending", s.projectedMonthEndMinor,
            valueColor = if (s.projectedMonthEndMinor > s.budgetMinor) DbTheme.colors.negative else DbTheme.colors.positive,
        )
        Text("Forecast: straight-line from day ${s.asOf.dayOfMonth} of ${s.month.lengthOfMonth()}.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
    }
}

@Composable
private fun AlertsCard(s: BudgetUiState, onOpen: (String) -> Unit) {
    DbCard(color = DbTheme.colors.negative.copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = DbTheme.colors.negative, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Overline("Budget alerts", color = DbTheme.colors.negative)
        }
        Spacer(Modifier.height(8.dp))
        s.alerts.forEach { l ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(l.category.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(l.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (l.isOver) "Over by " + formatMoney(-l.remainingMinor) else MoneyFormatter.percent(l.usedBps, 0) + " used",
                    style = MaterialTheme.typography.labelLarge, color = DbTheme.colors.negative,
                )
            }
        }
    }
}

@Composable
private fun CommitmentsCard(s: BudgetUiState) {
    DbCard {
        Overline("Cash commitments (not counted as spending)")
        Spacer(Modifier.height(6.dp))
        MoneyInfoRow("Loan principal repayments", s.principalRepaymentsMinor)
        MoneyInfoRow("Credit-card bill settlements", s.cardSettlementsMinor)
        Text(
            "Card purchases count as spending when made, so paying the bill is not counted again. " +
                "Principal repayments reduce liabilities; only interest is an expense.",
            style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
        )
    }
}

@Composable
private fun TrendCard(s: BudgetUiState) {
    var selected by remember(s.history) { mutableStateOf<Int?>(null) }
    DbCard {
        Overline("Budget vs actual · 6 months")
        Spacer(Modifier.height(8.dp))
        val m = s.history.getOrNull(selected ?: s.history.lastIndex)
        if (m != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(m.spentMinor, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text("spent in ${m.month.format(MONTH_FULL)}", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(
                "Budget ${formatMoney(m.budgetMinor)} · income ${formatMoney(m.incomeMinor)} · surplus ${formatMoney(m.incomeMinor - m.spentMinor, showSign = true)}",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(12.dp))
        GroupedBarChart(
            groups = s.history.map { BarGroup(it.month.format(MONTH_SHORT), it.spentMinor, secondary = it.budgetMinor) },
            selectedIndex = selected,
            onSelect = { selected = it },
            overColor = DbTheme.colors.negative,
        )
        Spacer(Modifier.height(8.dp))
        Text("Gold: actual · grey: budget · coral: over budget. Current month is month-to-date.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
    }
}

@Composable
private fun CategoriesCard(s: BudgetUiState, onOpen: (String) -> Unit) {
    DbCard(contentPadding = 12.dp) {
        s.lines.forEach { l ->
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onOpen(l.category.id) }.padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    MoneyText(l.actualMinor, style = MaterialTheme.typography.labelLarge, color = if (l.isOver) DbTheme.colors.negative else MaterialTheme.colorScheme.onSurface)
                    Text(" / " + formatMoney(l.category.monthlyBudgetMinor, forceAbbreviate = true), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
                }
                Spacer(Modifier.height(6.dp))
                SlimProgress(l.usedBps / 10_000f, color = if (l.isOver) DbTheme.colors.negative else DbTheme.colors.gold, height = 5.dp)
            }
        }
    }
}

@Composable
private fun MerchantsCard(s: BudgetUiState) {
    DbCard {
        Overline("Top merchants & recipients")
        Spacer(Modifier.height(8.dp))
        val max = s.topMerchants.maxOfOrNull { it.second } ?: 1L
        s.topMerchants.forEachIndexed { i, (name, v) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = DbTheme.colors.gold, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        MoneyText(v, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    SlimProgress(v.toFloat() / max, height = 3.dp, color = DbTheme.colors.gold.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun TransactionFilters(s: BudgetUiState, vm: BudgetViewModel) {
    Column {
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search merchant, note, category, account") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (s.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DbTheme.colors.gold,
                unfocusedBorderColor = DbTheme.colors.hairline,
                cursorColor = DbTheme.colors.gold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        ChoiceChips(TxFilter.entries, s.filter, { it.label }, vm::setFilter, Modifier.horizontalScroll(rememberScrollState()))
    }
}

@Composable
internal fun TransactionRow(t: Transaction, categoryName: String?) {
    val spending = t.type.countsAsSpending
    val income = t.type.countsAsIncome
    val color = when {
        income -> DbTheme.colors.positive
        spending -> MaterialTheme.colorScheme.onSurface
        else -> DbTheme.colors.textMuted
    }
    DbCard(contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.merchant, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    listOfNotNull(t.date.format(DAY_SHORT), categoryName, t.account).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(if (income) t.amountMinor else -t.amountMinor, style = MaterialTheme.typography.titleSmall, color = color, showSign = income)
                if (!spending) Tag(t.type.label, color = if (income) DbTheme.colors.positive else DbTheme.colors.textMuted)
                else if (t.paidByCreditCard) Tag("Card", color = DbTheme.colors.gold)
            }
        }
        if (t.note.isNotBlank()) {
            Text(t.note, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
