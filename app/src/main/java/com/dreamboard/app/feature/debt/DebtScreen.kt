package com.dreamboard.app.feature.debt

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.chart.LineAreaChart
import com.dreamboard.app.core.designsystem.chart.StackedBar
import com.dreamboard.app.core.designsystem.component.CardRow
import com.dreamboard.app.core.designsystem.component.ChoiceChips
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.DemoDataBanner
import com.dreamboard.app.core.designsystem.component.Dot
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.KpiCard
import com.dreamboard.app.core.designsystem.component.MoneyInfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.SlimProgress
import com.dreamboard.app.core.designsystem.component.Tag
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.finance.RepaymentStrategy
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.core.money.kes
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.DebtKind
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

internal val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

internal fun durationLabel(months: Int): String {
    val y = months / 12
    val m = months % 12
    val parts = ArrayList<String>()
    if (y > 0) parts += if (y == 1) "1 yr" else "$y yrs"
    if (m > 0 || y == 0) parts += if (m == 1) "1 mo" else "$m mos"
    return parts.joinToString(" ")
}

@Composable
fun DebtScreen(onOpenDebt: (String) -> Unit, viewModel: DebtViewModel = hiltViewModel()) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Debt Position", overline = "Project Zero Debt") }
        if (s.isDemo) item {
            DemoDataBanner("Seeded with your example balances as editable demo data, not verified live balances. Rates and installments are assumptions.")
        }
        item { DebtHero(s) }
        item {
            CardRow { m ->
                KpiCard("Interest-bearing", s.interestBearingMinor, m, caption = "Loans & revolving card")
                KpiCard("Monthly commitments", s.monthlyCommitmentMinor, m, caption = "Installments incl. card settlement")
            }
        }
        item { ReductionChart(s) }
        item { StrategyCard(s, viewModel) }
        item { CompositionCard(s) }
        item { SectionHeader("By lender") }
        items(s.debts, key = { it.id }) { d ->
            DebtRow(d, payoffMonth = s.scenario.payoffMonthByDebt[d.id], s = s, onClick = { onOpenDebt(d.id) })
        }
        item {
            Text(
                "Projections assume the stated rates and installments stay constant and interest is charged monthly on the reducing balance. " +
                    "Lenders may compute differently. These are estimates, not guarantees.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun DebtHero(s: DebtUiState) {
    DbCard(contentPadding = 24.dp) {
        Overline("Total outstanding", color = DbTheme.colors.gold)
        Spacer(Modifier.height(10.dp))
        MoneyText(s.totalOutstandingMinor, style = MaterialTheme.typography.displayMedium, animate = true)
        Spacer(Modifier.height(16.dp))
        Overline("Debt-free countdown")
        Spacer(Modifier.height(4.dp))
        val months = s.scenario.monthsToDebtFree
        if (months == null) {
            Text("Payments don't cover interest within 50 years", style = MaterialTheme.typography.titleMedium, color = DbTheme.colors.negative)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(durationLabel(months), style = MaterialTheme.typography.headlineSmall, color = DbTheme.colors.positive)
                Spacer(Modifier.width(10.dp))
                Text("projected ${s.debtFreeDate?.format(MONTH_YEAR)}", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Excludes the Equity card (settled in full monthly) and balances not yet entered.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
    }
}

@Composable
private fun ReductionChart(s: DebtUiState) {
    var selected by remember(s.scenario) { mutableStateOf<Int?>(null) }
    val series = s.scenario.balanceSeries
    DbCard {
        Overline("Projected debt reduction")
        Spacer(Modifier.height(8.dp))
        val idx = selected ?: 0
        Row(verticalAlignment = Alignment.Bottom) {
            MoneyText(series.getOrElse(idx) { 0L }, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text(if (idx == 0) "today" else s.asOf.plusMonths(idx.toLong()).format(MONTH_YEAR) + " · forecast",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(10.dp))
        LineAreaChart(series, selected, { selected = it }, lineColor = DbTheme.colors.positive, forecastFromIndex = 0, contentDescription = "Projected total debt")
        Spacer(Modifier.height(10.dp))
        Overline("Principal vs interest (remaining)")
        Spacer(Modifier.height(8.dp))
        StackedBar(listOf(s.scenario.totalPrincipalMinor to DbTheme.colors.gold, s.scenario.totalInterestMinor to DbTheme.colors.negative))
        Spacer(Modifier.height(8.dp))
        Row {
            Legend("Principal", formatMoney(s.scenario.totalPrincipalMinor), DbTheme.colors.gold, Modifier.weight(1f))
            Legend("Interest", formatMoney(s.scenario.totalInterestMinor), DbTheme.colors.negative, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun Legend(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun StrategyCard(s: DebtUiState, vm: DebtViewModel) {
    DbCard {
        Overline("Repayment strategy")
        Spacer(Modifier.height(8.dp))
        ChoiceChips(RepaymentStrategy.entries, s.inputs.strategy, { it.label }, vm::setStrategy, Modifier.horizontalScroll(rememberScrollState()))
        Spacer(Modifier.height(6.dp))
        Text(s.inputs.strategy.description, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
        if (s.inputs.strategy == RepaymentStrategy.CUSTOM) {
            Text("Order: " + s.debts.filter { it.isProjectable }.sortedBy { it.customPriority }.joinToString(" → ") { it.name },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        MoneySlider("Extra monthly repayment", s.inputs.extraMonthlyMinor, maxKes = 150_000, stepKes = 5_000, onChange = vm::setExtra)
        MoneySlider("Ignite income directed to debt", s.inputs.businessIncomeToDebtMinor, maxKes = 200_000, stepKes = 5_000, onChange = vm::setBusinessIncome)
        Spacer(Modifier.height(8.dp))
        MoneyInfoRow("Total interest (projected)", s.scenario.totalInterestMinor, valueColor = DbTheme.colors.negative)
        InfoRow("Debt-free date", s.debtFreeDate?.format(MONTH_YEAR) ?: "Not within horizon")
        if (s.inputs.totalExtraMinor > 0) {
            InfoRow("Time saved", s.monthsSaved?.let { durationLabel(it) } ?: "—", valueColor = DbTheme.colors.positive)
            MoneyInfoRow("Interest saved", s.interestSavedMinor, valueColor = DbTheme.colors.positive)
        }
    }
}

@Composable
internal fun MoneySlider(label: String, valueMinor: Long, maxKes: Int, stepKes: Int, onChange: (Long) -> Unit) {
    val steps = maxKes / stepKes
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
            Text(formatMoney(valueMinor), style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = (valueMinor / 100L / stepKes).toFloat(),
            onValueChange = { onChange((it.roundToLong() * stepKes).kes) },
            valueRange = 0f..steps.toFloat(),
            steps = steps - 1,
            colors = SliderDefaults.colors(
                thumbColor = DbTheme.colors.gold, activeTrackColor = DbTheme.colors.gold, inactiveTrackColor = DbTheme.colors.hairline,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun CompositionCard(s: DebtUiState) {
    val palette = DbTheme.colors.chartPalette
    val total = s.composition.sumOf { it.second }
    DbCard {
        Overline("Debt composition")
        Spacer(Modifier.height(12.dp))
        StackedBar(s.composition.mapIndexed { i, (_, v) -> v to palette[i % palette.size] }, height = 14.dp)
        Spacer(Modifier.height(12.dp))
        s.composition.forEachIndexed { i, (c, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(palette[i % palette.size])
                Spacer(Modifier.width(8.dp))
                Text(c.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(MoneyFormatter.percent(com.dreamboard.app.core.money.ratioBps(v, total), 0), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
                Spacer(Modifier.width(10.dp))
                MoneyText(v, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DebtRow(d: Debt, payoffMonth: Int?, s: DebtUiState, onClick: () -> Unit) {
    DbCard(onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium)
                Text(d.lender + " · " + d.category.label, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            }
            if (d.balanceMinor == null) {
                Tag("Not entered", color = DbTheme.colors.gold)
            } else {
                MoneyText(d.balanceMinor, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        val principal = d.originalPrincipalMinor
        if (principal != null && d.balanceMinor != null && principal > 0) {
            SlimProgress(1f - d.balanceMinor.toFloat() / principal.toFloat(), color = DbTheme.colors.positive)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Tag(if (d.annualRateBps > 0) MoneyFormatter.percent(d.annualRateBps.toLong()) + " p.a." else "0%")
            if (d.monthlyPaymentMinor > 0) Tag(formatMoney(d.monthlyPaymentMinor, forceAbbreviate = true) + "/mo")
            when {
                d.kind == DebtKind.STATEMENT_PAID_IN_FULL -> Tag("Paid in full monthly", color = DbTheme.colors.positive)
                payoffMonth != null && payoffMonth > 0 -> Tag("Clear " + s.asOf.plusMonths(payoffMonth.toLong()).format(MONTH_YEAR), color = DbTheme.colors.positive)
                else -> {}
            }
        }
    }
}
