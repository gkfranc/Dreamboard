package com.dreamboard.app.feature.dreamboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.MoneyInfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ProgressRing
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.SlimProgress
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.core.money.kes
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy")

@Composable
fun PorscheGoalScreen(onBack: () -> Unit, viewModel: DreamboardViewModel = hiltViewModel()) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val g = s.goalProjection
    val a = s.goal

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Porsche Cayenne", overline = "Dream tracker · ${a.targetYearMonth.year}", onBack = onBack) }
        item {
            DbCard {
                SuvIllustration()
                Text("Original illustration. Your own photo can replace it in a later phase.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            }
        }

        item { SectionHeader("1 · Porsche fund progress") }
        item {
            DbCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(g.fundedBps / 10_000f, MoneyFormatter.percent(g.fundedBps, 1), size = 104.dp)
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Overline("Saved for the car")
                        MoneyText(a.currentFundMinor, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Remaining " + formatMoney(g.remainingMinor), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                MoneyInfoRow("Target price today", a.todayPriceMinor)
                MoneyInfoRow("Inflation-adjusted target (${a.targetYearMonth.format(MONTH_YEAR)})", g.inflatedTargetMinor, valueColor = DbTheme.colors.gold)
                InfoRow("Months remaining", "${g.monthsRemaining}")
                MoneyInfoRow("Monthly contribution required", g.requiredMonthlyMinor)
                MoneyInfoRow("Your planned contribution", a.monthlyContributionMinor)
                MoneyInfoRow("Projected fund at target date", g.projectedFundAtTargetMinor,
                    valueColor = if (g.projectedFundAtTargetMinor >= g.inflatedTargetMinor) DbTheme.colors.positive else DbTheme.colors.negative)
                InfoRow(
                    "Estimated achievement",
                    g.estimatedAchievement?.format(MONTH_YEAR) ?: "Not within 50 years",
                    valueColor = if (g.estimatedAchievement != null && !g.estimatedAchievement.isAfter(a.targetYearMonth)) DbTheme.colors.positive else DbTheme.colors.negative,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Only the dedicated Porsche fund counts here. Net worth is not cash available for the purchase.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                )
            }
        }

        item { SectionHeader("2 · Porsche readiness") }
        item {
            DbCard {
                val passed = s.readiness.count { it.passed }
                Overline("$passed of ${s.readiness.size} checks met")
                Spacer(Modifier.height(8.dp))
                SlimProgress(passed / s.readiness.size.toFloat(), color = if (passed == s.readiness.size) DbTheme.colors.positive else DbTheme.colors.gold)
                Spacer(Modifier.height(8.dp))
                s.readiness.forEach { c ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (c.passed) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                            contentDescription = if (c.passed) "Met" else "Not yet met",
                            tint = if (c.passed) DbTheme.colors.positive else DbTheme.colors.negative,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            Text(c.detail, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Estimated ownership costs") }
        item {
            DbCard {
                MoneyInfoRow("Insurance (${MoneyFormatter.percent(a.insuranceRateBps.toLong())} of value)", g.annualOwnershipCostMinor - a.annualMaintenanceMinor - a.annualFuelMinor)
                MoneyInfoRow("Service & maintenance", a.annualMaintenanceMinor)
                MoneyInfoRow("Fuel", a.annualFuelMinor)
                MoneyInfoRow("Total per year", g.annualOwnershipCostMinor, valueColor = DbTheme.colors.gold)
            }
        }

        item { SectionHeader("Assumptions") }
        item {
            DbCard {
                AssumptionSlider(
                    "Target price today", formatMoney(a.todayPriceMinor),
                    value = (a.todayPriceMinor / 100L).toFloat() / 1_000_000f, range = 10f..40f, steps = 59,
                ) { v -> viewModel.updateGoal { it.copy(todayPriceMinor = ((v * 2).roundToLong() * 500_000L).kes) } }
                AssumptionSlider(
                    "Planned monthly contribution", formatMoney(a.monthlyContributionMinor),
                    value = (a.monthlyContributionMinor / 100L / 5_000L).toFloat(), range = 0f..60f, steps = 59,
                ) { v -> viewModel.updateGoal { it.copy(monthlyContributionMinor = (v.roundToLong() * 5_000L).kes) } }
                AssumptionSlider(
                    "Expected investment return", MoneyFormatter.percent(a.annualReturnBps.toLong()) + " p.a.",
                    value = a.annualReturnBps / 50f, range = 0f..30f, steps = 29,
                ) { v -> viewModel.updateGoal { it.copy(annualReturnBps = v.roundToInt() * 50) } }
                AssumptionSlider(
                    "Vehicle price inflation (incl. FX)", MoneyFormatter.percent(a.annualInflationBps.toLong()) + " p.a.",
                    value = a.annualInflationBps / 50f, range = 0f..24f, steps = 23,
                ) { v -> viewModel.updateGoal { it.copy(annualInflationBps = v.roundToInt() * 50) } }
                AssumptionSlider(
                    "Target purchase year", "${a.targetYearMonth.year}",
                    value = a.targetYearMonth.year.toFloat(), range = 2028f..2045f, steps = 16,
                ) { v -> viewModel.updateGoal { it.copy(targetYearMonth = YearMonth.of(v.roundToInt(), 12)) } }
                AssumptionSlider(
                    "Insurance rate", MoneyFormatter.percent(a.insuranceRateBps.toLong()) + " of value",
                    value = a.insuranceRateBps / 25f, range = 0f..32f, steps = 31,
                ) { v -> viewModel.updateGoal { it.copy(insuranceRateBps = v.roundToInt() * 25) } }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Projections use nominal monthly compounding. They are estimates, not guarantees. " +
                        "Edits last for this session until the Phase 2 database stores them.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AssumptionSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = DbTheme.colors.gold,
                activeTrackColor = DbTheme.colors.gold,
                inactiveTrackColor = DbTheme.colors.hairline,
                activeTickColor = DbTheme.colors.gold.copy(alpha = 0f),
                inactiveTickColor = DbTheme.colors.hairline.copy(alpha = 0f),
            ),
        )
    }
}
