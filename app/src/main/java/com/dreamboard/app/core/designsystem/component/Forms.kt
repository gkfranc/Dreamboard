package com.dreamboard.app.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.importer.ImportParsing
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Parses a typed amount ("12,500.50") to minor units; null when empty or invalid; negatives rejected. */
fun parseMoneyInput(text: String): Long? = ImportParsing.parseAmount(text)?.takeIf { it >= 0 }

/** Minor units to an editable string without grouping, e.g. 1250050 -> "12500.50", 1250000 -> "12500". */
fun moneyToInput(minor: Long?): String {
    if (minor == null) return ""
    val v = BigDecimal.valueOf(minor).movePointLeft(2)
    return if (minor % 100L == 0L) v.setScale(0).toPlainString() else v.toPlainString()
}

@Composable
fun dbFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DbTheme.colors.gold,
    unfocusedBorderColor = DbTheme.colors.hairline,
    cursorColor = DbTheme.colors.gold,
    focusedLabelColor = DbTheme.colors.gold,
)

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = MaterialTheme.shapes.medium, colors = dbFieldColors(),
    )
}

/** Amount field in KES. Shows an error when the text is not a valid non-negative amount. */
@Composable
fun MoneyInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, required: Boolean = true, supporting: String? = null) {
    val invalid = value.isNotBlank() && parseMoneyInput(value) == null || required && value.isBlank()
    TextInput(
        value, { t -> onChange(t.filter { it.isDigit() || it == '.' || it == ',' }) }, "$label (KES)", modifier,
        supporting = if (invalid && value.isNotBlank()) "Enter an amount like 12500 or 12,500.50" else supporting,
        isError = invalid && value.isNotBlank(), keyboard = KeyboardType.Decimal,
    )
}

private val DATE_TEXT = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(value: LocalDate, onChange: (LocalDate) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.format(DATE_TEXT), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = dbFieldColors(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Read-only field that opens a menu of [options]. */
@Composable
fun <T> PickerInput(
    value: T,
    options: List<T>,
    label: String,
    display: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display(value), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = dbFieldColors(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onPick(o); open = false })
            }
        }
    }
}
