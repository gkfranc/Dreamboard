package com.dreamboard.app.feature.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.designsystem.component.DateInput
import com.dreamboard.app.core.designsystem.component.EditorScaffold
import com.dreamboard.app.core.designsystem.component.MoneyInput
import com.dreamboard.app.core.designsystem.component.PickerInput
import com.dreamboard.app.core.designsystem.component.TextInput
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.component.moneyToInput
import com.dreamboard.app.core.designsystem.component.parseMoneyInput
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.finance.LoanMath
import com.dreamboard.app.domain.model.Debt
import com.dreamboard.app.domain.model.DebtCategory
import com.dreamboard.app.domain.model.DebtKind
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** "16.5" -> 1650 bps. Null if invalid or above 100%. */
fun parseRateBps(text: String): Int? {
    val t = text.trim().removeSuffix("%").trim()
    if (t.isEmpty()) return 0
    return runCatching { BigDecimal(t).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt() }.getOrNull()?.takeIf { it in 0..10_000 }
}

fun rateToInput(bps: Int): String = BigDecimal.valueOf(bps.toLong()).movePointLeft(2).stripTrailingZeros().toPlainString()

@HiltViewModel
class DebtEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val existing: Debt? = savedStateHandle.get<String>("debtId")?.let { id -> repo.debts.value.firstOrNull { it.id == id } }
    val isNew = existing == null
    var name by mutableStateOf(existing?.name ?: "")
    var lender by mutableStateOf(existing?.lender ?: "")
    var category by mutableStateOf(existing?.category ?: DebtCategory.PERSONAL_LOAN)
    var kind by mutableStateOf(existing?.kind ?: DebtKind.REDUCING_BALANCE)
    var balance by mutableStateOf(moneyToInput(existing?.balanceMinor))
    var original by mutableStateOf(moneyToInput(existing?.originalPrincipalMinor))
    var rate by mutableStateOf(existing?.let { rateToInput(it.annualRateBps) } ?: "")
    var installment by mutableStateOf(moneyToInput(existing?.monthlyPaymentMinor?.takeIf { it > 0 }))
    var dueDay by mutableStateOf(existing?.dueDayOfMonth?.toString() ?: "")
    var priority by mutableStateOf((existing?.customPriority ?: 5).toString())
    var notes by mutableStateOf(existing?.notes ?: "")
    var reason by mutableStateOf("")

    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    private fun optionalMoney(t: String): Result<Long?> =
        if (t.isBlank()) Result.success<Long?>(null) else parseMoneyInput(t)?.let { Result.success<Long?>(it) } ?: Result.failure<Long?>(IllegalArgumentException())

    val canSave: Boolean
        get() = name.isNotBlank() && optionalMoney(balance).isSuccess && optionalMoney(original).isSuccess &&
            optionalMoney(installment).isSuccess && parseRateBps(rate) != null &&
            (dueDay.isBlank() || dueDay.toIntOrNull()?.let { it in 1..31 } == true) && priority.toIntOrNull() != null

    fun save() = viewModelScope.launch {
        if (!canSave) return@launch
        val d = Debt(
            id = existing?.id ?: UUID.randomUUID().toString(), name = name, lender = lender, category = category, kind = kind,
            balanceMinor = optionalMoney(balance).getOrNull(), originalPrincipalMinor = optionalMoney(original).getOrNull(),
            annualRateBps = parseRateBps(rate) ?: 0, monthlyPaymentMinor = optionalMoney(installment).getOrNull() ?: 0L,
            dueDayOfMonth = dueDay.toIntOrNull(), interestPaidToDateMinor = existing?.interestPaidToDateMinor,
            customPriority = priority.toIntOrNull() ?: 5, notes = notes.trim(),
        )
        repo.saveDebt(d, reason.ifBlank { "Balance updated" })
        _done.value = true
    }

    fun delete() = viewModelScope.launch { existing?.let { repo.deleteDebt(it.id) }; _done.value = true }
}

@Composable
fun DebtEditorScreen(onBack: () -> Unit, viewModel: DebtEditorViewModel = hiltViewModel()) {
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    val vm = viewModel
    EditorScaffold(
        title = if (vm.isNew) "Add debt" else "Edit debt", overline = "Debt Position", onBack = onBack,
        canSave = vm.canSave, onSave = { vm.save() }, onDelete = if (vm.isNew) null else ({ vm.delete() }),
        deleteMessage = "This removes the debt and its payment history from this phone.",
    ) {
        TextInput(vm.name, { vm.name = it }, "Name", supporting = "e.g. ABSA Personal Loan")
        TextInput(vm.lender, { vm.lender = it }, "Lender")
        PickerInput(vm.category, DebtCategory.entries, "Category", { it.label }, { vm.category = it })
        PickerInput(vm.kind, DebtKind.entries, "How it works", { it.label }, { vm.kind = it })
        MoneyInput(vm.balance, { vm.balance = it }, "Outstanding balance", required = false, supporting = "Leave blank if you don't know it yet")
        if (!vm.isNew) TextInput(vm.reason, { vm.reason = it }, "Reason for balance change (optional)", supporting = "Kept in the audit history")
        MoneyInput(vm.original, { vm.original = it }, "Original principal", required = false)
        TextInput(vm.rate, { vm.rate = it }, "Interest rate (% per year)", keyboard = KeyboardType.Decimal,
            isError = parseRateBps(vm.rate) == null, supporting = "Use 0 for interest-free")
        MoneyInput(vm.installment, { vm.installment = it }, "Monthly installment", required = false)
        TextInput(vm.dueDay, { vm.dueDay = it.filter(Char::isDigit).take(2) }, "Due day of month (optional)", keyboard = KeyboardType.Number)
        TextInput(vm.priority, { vm.priority = it.filter(Char::isDigit).take(2) }, "Custom priority (1 = pay first)", keyboard = KeyboardType.Number)
        TextInput(vm.notes, { vm.notes = it }, "Notes", singleLine = false)
    }
}

/** Records a payment: splits it into principal and interest, reduces the balance, keeps history. */
@Composable
fun RecordPaymentDialog(debt: Debt, onDismiss: () -> Unit, onRecord: (LocalDate, Long, Long, String) -> Unit) {
    val suggestedInterest = debt.balanceMinor?.let { LoanMath.monthlyInterest(it, debt.annualRateBps) } ?: 0L
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amount by remember { mutableStateOf(moneyToInput(debt.monthlyPaymentMinor.takeIf { it > 0 })) }
    var interest by remember { mutableStateOf(moneyToInput(suggestedInterest)) }
    var note by remember { mutableStateOf("") }
    val a = parseMoneyInput(amount)
    val i = if (interest.isBlank()) 0L else parseMoneyInput(interest)
    val valid = a != null && a > 0 && i != null && i <= a
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateInput(date, { date = it }, "Date")
                MoneyInput(amount, { amount = it }, "Amount paid")
                MoneyInput(interest, { interest = it }, "Of which interest", required = false,
                    supporting = "Estimated ${formatMoney(suggestedInterest)} from the rate. Use your statement figure if you have it.")
                TextInput(note, { note = it }, "Note (optional)")
                if (a != null && i != null && i <= a) {
                    Text("Principal ${formatMoney(a - i)} will come off the balance.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onRecord(date, a ?: 0L, i ?: 0L, note) }) { Text("Record") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
