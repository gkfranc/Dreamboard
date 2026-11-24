package com.dreamboard.app.feature.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.dreamboard.app.core.designsystem.component.MoneyInput
import com.dreamboard.app.core.designsystem.component.TextInput
import com.dreamboard.app.core.designsystem.component.moneyToInput
import com.dreamboard.app.core.designsystem.component.parseMoneyInput
import com.dreamboard.app.domain.model.BudgetCategory
import java.util.UUID

/** Add a category, or rename one and change its monthly limit. */
@Composable
internal fun CategoryDialog(existing: BudgetCategory?, onDismiss: () -> Unit, onSave: (BudgetCategory) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var limit by remember { mutableStateOf(moneyToInput(existing?.monthlyBudgetMinor ?: 0L)) }
    val limitMinor = parseMoneyInput(limit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, "Name")
                MoneyInput(limit, { limit = it }, "Monthly limit", supporting = "Use 0 for a category you track without a limit.")
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && limitMinor != null, onClick = {
                onSave(BudgetCategory(existing?.id ?: UUID.randomUUID().toString(), name.trim(), limitMinor ?: 0L))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
