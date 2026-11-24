package com.dreamboard.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dreamboard.app.core.designsystem.theme.DbTheme

/** Full-screen form: header, scrolling fields, Save (and optional Delete with confirmation). */
@Composable
fun EditorScaffold(
    title: String,
    overline: String?,
    onBack: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteMessage: String = "This permanently removes it from this phone.",
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title, overline = overline, onBack = onBack, showSettings = false)
            content()
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onDelete != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete", color = DbTheme.colors.negative) }
            }
            Button(
                onClick = onSave, enabled = canSave, modifier = Modifier.weight(if (onDelete != null) 2f else 1f),
                colors = ButtonDefaults.buttonColors(containerColor = DbTheme.colors.gold, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text("Save") }
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog("Delete?", deleteMessage, "Delete", onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = DbTheme.colors.negative) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Gold primary action button used on dashboards ("Add asset", "Add transaction"). */
@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = DbTheme.colors.gold, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) { Text(label) }
}

@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, color = DbTheme.colors.gold) }
}
