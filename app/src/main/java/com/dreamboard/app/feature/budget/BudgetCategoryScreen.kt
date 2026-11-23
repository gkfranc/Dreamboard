package com.dreamboard.app.feature.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
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
import com.dreamboard.app.core.designsystem.component.MoneyInfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.SlimProgress
import com.dreamboard.app.core.designsystem.theme.DbTheme
import java.time.format.DateTimeFormatter

@Composable
fun BudgetCategoryScreen(onBack: () -> Unit, viewModel: BudgetCategoryViewModel = hiltViewModel()) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val line = s.line
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(line?.category?.name ?: "Category", overline = s.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), onBack = onBack) }
        if (line != null) {
            item {
                DbCard(contentPadding = 24.dp) {
                    Overline("Spent this month", color = DbTheme.colors.gold)
                    Spacer(Modifier.height(8.dp))
                    MoneyText(line.actualMinor, style = MaterialTheme.typography.displaySmall, color = if (line.isOver) DbTheme.colors.negative else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    SlimProgress(line.usedBps / 10_000f, color = if (line.isOver) DbTheme.colors.negative else DbTheme.colors.gold)
                    Spacer(Modifier.height(12.dp))
                    MoneyInfoRow("Monthly limit", line.category.monthlyBudgetMinor)
                    MoneyInfoRow("Remaining", line.remainingMinor, valueColor = if (line.isOver) DbTheme.colors.negative else DbTheme.colors.positive)
                    Text("Limits become editable, copyable between months and annual in Phase 3.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
        }
        item { SectionHeader("Transactions (${s.transactions.size})") }
        if (s.transactions.isEmpty()) {
            item { EmptyState(Icons.Outlined.ReceiptLong, "Nothing spent here yet", "Transactions in this category will appear here.") }
        } else {
            items(s.transactions, key = { it.id }) { t -> TransactionRow(t, null) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
