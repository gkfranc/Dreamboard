package com.dreamboard.app.feature.ignite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.EmptyState
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.MoneyInfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.Tag
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.money.MoneyFormatter
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.fillMaxWidth
import com.dreamboard.app.core.designsystem.component.SecondaryAction

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun VehicleDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: VehicleDetailViewModel = hiltViewModel()) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val v = s.vehicle
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(v?.title ?: "Vehicle", overline = v?.registration, onBack = onBack) }
        if (v == null) {
            item { EmptyState(Icons.Outlined.DirectionsCar, "Vehicle not found", "It may have been removed.") }
            return@LazyColumn
        }
        item {
            DbCard(contentPadding = 24.dp) {
                Row { Tag(v.status.label, color = statusColor(v.status)) }
                Spacer(Modifier.height(12.dp))
                val realized = s.realizedProfit
                Overline(if (realized != null) "Net deal profit" else "Projected net profit", color = DbTheme.colors.gold)
                Spacer(Modifier.height(6.dp))
                val p = realized ?: s.projectedProfit
                if (p != null) {
                    MoneyText(p, style = MaterialTheme.typography.displaySmall, showSign = true,
                        color = if (p >= 0) DbTheme.colors.positive else DbTheme.colors.negative)
                } else {
                    Text("Set an expected price to project profit", style = MaterialTheme.typography.titleMedium, color = DbTheme.colors.textMuted)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Selling price − purchase price − all attributable deal expenses." + if (realized == null) " Forecast, not realized." else "",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                )
            }
        }
        item { SecondaryAction("Edit car", { onEdit(v.id) }, Modifier.fillMaxWidth()) }
        item { SectionHeader("Deal economics") }
        item {
            DbCard {
                MoneyInfoRow("Purchase price", v.purchasePriceMinor)
                v.expenses.forEach { e -> MoneyInfoRow(e.kind.label, e.amountMinor) }
                HorizontalDivider(color = DbTheme.colors.hairline, modifier = Modifier.padding(vertical = 6.dp))
                MoneyInfoRow("Total cost", v.totalCostMinor, valueColor = DbTheme.colors.gold)
                MoneyInfoRow("Listing price", v.listingPriceMinor, emptyText = "Not listed")
                MoneyInfoRow("Expected selling price", v.expectedPriceMinor, emptyText = "—")
                MoneyInfoRow("Actual selling price", v.actualPriceMinor, emptyText = "Not sold")
            }
        }
        s.split?.let { split ->
            item { SectionHeader("Profit sharing") }
            item {
                DbCard {
                    Text(s.arrangement?.name ?: "", style = MaterialTheme.typography.titleSmall)
                    Text(s.arrangement?.type?.label ?: "", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                    val a = s.arrangement!!
                    MoneyInfoRow("Investor (${MoneyFormatter.percent(a.investorShareBps, 0)})", split.investorMinor)
                    MoneyInfoRow("Partner (${MoneyFormatter.percent(a.partnerShareBps, 0)})", split.partnerMinor)
                    MoneyInfoRow("You (${MoneyFormatter.percent(a.personalShareBps, 0)})", split.personalMinor, valueColor = DbTheme.colors.positive)
                    Text(
                        "Sharing rules are set per arrangement, never hard-coded across vehicles. Splits always add up to the deal profit exactly.",
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                    )
                }
            }
        }
        item { SectionHeader("Details") }
        item {
            DbCard {
                InfoRow("Make & model", "${v.make} ${v.model}")
                if (v.registration.isNotBlank()) InfoRow("Registration", v.registration)
                if (v.year > 0) InfoRow("Year", "${v.year}")
                if (v.condition.isNotBlank()) InfoRow("Condition", v.condition)
                InfoRow("Purchase date", v.purchaseDate?.format(DAY) ?: "Not purchased")
                InfoRow("Sale date", v.saleDate?.format(DAY) ?: "—")
                InfoRow("Days in stock", s.daysInStock?.toString() ?: "—")
                if (v.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(v.notes, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
