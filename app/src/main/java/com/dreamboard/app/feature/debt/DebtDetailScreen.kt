package com.dreamboard.app.feature.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.chart.LineAreaChart
import com.dreamboard.app.core.designsystem.chart.StackedBar
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.EmptyState
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.MoneyInfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.core.designsystem.component.PrimaryAction
import com.dreamboard.app.core.designsystem.component.SecondaryAction
import com.dreamboard.app.feature.manage.RecordPaymentDialog
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun DebtDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: DebtDetailViewModel = hiltViewModel()) {
    var recording by remember { mutableStateOf(false) }
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val d = s.debt
    var selected by remember(s.schedule) { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(d?.name ?: "Debt", overline = d?.lender, onBack = onBack) }
        if (d == null) {
            item { EmptyState(Icons.Outlined.EditNote, "Debt not found", "It may have been removed.") }
            return@LazyColumn
        }
        item {
            DbCard(contentPadding = 24.dp) {
                Overline("Outstanding balance", color = DbTheme.colors.gold)
                Spacer(Modifier.height(8.dp))
                if (d.balanceMinor == null) {
                    Text("Not entered yet", style = MaterialTheme.typography.headlineMedium, color = DbTheme.colors.gold)
                    Text("Tap Edit to enter the balance so it counts in totals and projections.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                } else {
                    MoneyText(d.balanceMinor, style = MaterialTheme.typography.displayMedium)
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("Type", d.kind.label)
                MoneyInfoRow("Original principal", d.originalPrincipalMinor, emptyText = "—")
                InfoRow("Interest rate", if (d.annualRateBps > 0) MoneyFormatter.percent(d.annualRateBps.toLong()) + " p.a." else "Interest-free")
                MoneyInfoRow("Monthly installment", d.monthlyPaymentMinor.takeIf { it > 0 })
                InfoRow("Payment due", d.dueDayOfMonth?.let { "Day $it of each month" } ?: "Flexible")
                MoneyInfoRow("Interest paid to date", d.interestPaidToDateMinor, emptyText = "—")
                Spacer(Modifier.height(6.dp))
                Text(d.notes, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryAction("Edit", { onEdit(d.id) }, Modifier.weight(1f))
                PrimaryAction("Record payment", { recording = true }, Modifier.weight(1f))
            }
        }
        if (s.schedule.isNotEmpty()) {
            val last = s.schedule.last()
            val interestLeft = s.schedule.sumOf { it.interestMinor }
            val baseInterest = s.baselineSchedule.sumOf { it.interestMinor }
            item {
                DbCard {
                    Overline("Early repayment scenario")
                    Spacer(Modifier.height(6.dp))
                    MoneySlider("Extra per month on this debt", s.extraMinor, maxKes = 100_000, stepKes = 2_500, onChange = viewModel::setExtra)
                    InfoRow("Remaining term", durationLabel(s.schedule.size))
                    InfoRow("Projected payoff", s.asOf.plusMonths(last.month.toLong()).format(MONTH_YEAR), valueColor = DbTheme.colors.positive)
                    MoneyInfoRow("Estimated interest remaining", interestLeft, valueColor = DbTheme.colors.negative)
                    if (s.extraMinor > 0) {
                        InfoRow("Time saved", durationLabel(s.baselineSchedule.size - s.schedule.size), valueColor = DbTheme.colors.positive)
                        MoneyInfoRow("Interest saved", baseInterest - interestLeft, valueColor = DbTheme.colors.positive)
                    }
                    Spacer(Modifier.height(12.dp))
                    val idx = selected
                    val row = idx?.let { s.schedule.getOrNull(it) }
                    Text(
                        if (row == null) "Tap the chart to inspect a month" else
                            "${s.asOf.plusMonths(row.month.toLong()).format(MONTH_YEAR)} · closing ${formatMoney(row.closingMinor)} · interest ${formatMoney(row.interestMinor)}",
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    LineAreaChart(s.schedule.map { it.closingMinor }, selected, { selected = it }, lineColor = DbTheme.colors.positive, forecastFromIndex = 0, height = 150.dp, contentDescription = "Balance forecast")
                    Spacer(Modifier.height(12.dp))
                    Overline("Principal vs interest still to pay")
                    Spacer(Modifier.height(8.dp))
                    StackedBar(listOf(d.balanceMinor!! to DbTheme.colors.gold, interestLeft to DbTheme.colors.negative))
                    Row(Modifier.padding(top = 8.dp)) {
                        Legend("Principal", formatMoney(d.balanceMinor), DbTheme.colors.gold, Modifier.weight(1f))
                        Legend("Interest", formatMoney(interestLeft), DbTheme.colors.negative, Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionHeader("Repayment history") }
        if (d.payments.isEmpty()) {
            item {
                DbCard {
                    EmptyState(Icons.Outlined.EditNote, "No payments recorded", "Tap Record payment after each installment. Interest is split out so the balance drops by principal only.")
                }
            }
        } else {
            item {
                DbCard(contentPadding = 16.dp) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("Date", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted)
                        Text("Principal", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted, textAlign = TextAlign.End)
                        Text("Interest", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = DbTheme.colors.textMuted, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = DbTheme.colors.hairline)
                    d.payments.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(p.date.format(DAY), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                            Text(formatMoney(p.principalMinor, forceAbbreviate = true), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                            Text(formatMoney(p.interestMinor, forceAbbreviate = true), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.negative, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    if (recording && d != null) {
        RecordPaymentDialog(d, onDismiss = { recording = false }, onRecord = { date, amount, interest, note ->
            viewModel.recordPayment(date, amount, interest, note); recording = false
        })
    }
}
