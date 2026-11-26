package com.dreamboard.app.feature.ignite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.MoneyInput
import com.dreamboard.app.core.designsystem.component.PickerInput
import com.dreamboard.app.core.designsystem.component.SecondaryAction
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.TextInput
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.component.moneyToInput
import com.dreamboard.app.core.designsystem.component.parseMoneyInput
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.domain.model.DealExpense
import com.dreamboard.app.domain.model.DealExpenseKind
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.FundingType
import com.dreamboard.app.domain.model.Vehicle
import com.dreamboard.app.domain.model.VehicleStatus
import com.dreamboard.app.domain.repository.FinanceRepository
import com.dreamboard.app.feature.manage.parseRateBps
import com.dreamboard.app.feature.manage.rateToInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VehicleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val existing: Vehicle? = savedStateHandle.get<String>("vehicleId")?.let { id -> repo.vehicles.value.firstOrNull { it.id == id } }
    val isNew = existing == null
    val arrangements: List<FundingArrangement> get() = repo.arrangements.value

    var make by mutableStateOf(existing?.make ?: "")
    var model by mutableStateOf(existing?.model ?: "")
    var year by mutableStateOf(existing?.year?.takeIf { it > 0 }?.toString() ?: "")
    var registration by mutableStateOf(existing?.registration ?: "")
    var status by mutableStateOf(existing?.status ?: VehicleStatus.PURCHASED)
    var purchaseDate by mutableStateOf(existing?.purchaseDate ?: LocalDate.now())
    var buying by mutableStateOf(moneyToInput(existing?.purchasePriceMinor))
    val costs = mutableStateMapOf<DealExpenseKind, String>().apply {
        DealExpenseKind.entries.forEach { k -> put(k, moneyToInput(existing?.expenses?.filter { it.kind == k }?.sumOf { it.amountMinor }?.takeIf { it > 0 })) }
    }
    var expected by mutableStateOf(moneyToInput(existing?.expectedPriceMinor))
    var selling by mutableStateOf(moneyToInput(existing?.actualPriceMinor))
    var saleDate by mutableStateOf(existing?.saleDate ?: LocalDate.now())
    var arrangementId by mutableStateOf(existing?.arrangementId ?: repo.arrangements.value.firstOrNull()?.id.orEmpty())
    var condition by mutableStateOf(existing?.condition ?: "")
    var notes by mutableStateOf(existing?.notes ?: "")

    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    private fun optional(t: String): Long? = if (t.isBlank()) 0L else parseMoneyInput(t)
    val sold: Boolean get() = status == VehicleStatus.SOLD
    val totalCostMinor: Long? get() {
        val b = parseMoneyInput(buying) ?: return null
        return b + costs.values.sumOf { optional(it) ?: return null }
    }

    val canSave: Boolean
        get() = make.isNotBlank() && parseMoneyInput(buying) != null && costs.values.all { optional(it) != null } &&
            optional(expected) != null && (!sold || (parseMoneyInput(selling) ?: 0L) > 0) &&
            (year.isBlank() || year.toIntOrNull()?.let { it in 1950..2100 } == true) && arrangementId.isNotBlank()

    fun save() = viewModelScope.launch {
        if (!canSave) return@launch
        val v = Vehicle(
            id = existing?.id ?: UUID.randomUUID().toString(), registration = registration.trim(), make = make.trim(), model = model.trim(),
            year = year.toIntOrNull() ?: 0, purchaseDate = if (status == VehicleStatus.UNDER_CONSIDERATION) null else purchaseDate,
            purchasePriceMinor = parseMoneyInput(buying) ?: 0L, arrangementId = arrangementId, condition = condition.trim(),
            expenses = costs.entries.mapNotNull { (k, t) -> optional(t)?.takeIf { it > 0 }?.let { DealExpense(k, it) } }.sortedBy { it.kind.ordinal },
            listingPriceMinor = existing?.listingPriceMinor, expectedPriceMinor = optional(expected)?.takeIf { it > 0 },
            actualPriceMinor = if (sold) parseMoneyInput(selling) else null, saleDate = if (sold) saleDate else null,
            status = status, notes = notes.trim(),
        )
        repo.saveVehicle(v)
        _done.value = true
    }

    fun delete() = viewModelScope.launch { existing?.let { repo.deleteVehicle(it.id) }; _done.value = true }

    fun addArrangement(name: String, partnerBps: Int, investorBps: Int) = viewModelScope.launch {
        val a = FundingArrangement(UUID.randomUUID().toString(), name, if (partnerBps + investorBps == 0) FundingType.SOLE else FundingType.PARTNERSHIP,
            investorBps.toLong(), partnerBps.toLong(), 10_000L - partnerBps - investorBps)
        repo.saveArrangement(a)
        arrangementId = a.id
    }
}


@Composable
fun VehicleEditorScreen(onBack: () -> Unit, viewModel: VehicleEditorViewModel = hiltViewModel()) {
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    val vm = viewModel
    var addingRule by remember { mutableStateOf(false) }
    EditorScaffold(
        title = if (vm.isNew) "Add car" else "Edit car", overline = "Ignite Motors", onBack = onBack,
        canSave = vm.canSave, onSave = { vm.save() }, onDelete = if (vm.isNew) null else ({ vm.delete() }),
        deleteMessage = "This removes the car and its costs from this phone.",
    ) {
        TextInput(vm.make, { vm.make = it }, "Make", supporting = "e.g. Toyota")
        TextInput(vm.model, { vm.model = it }, "Model", supporting = "e.g. Fielder")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(vm.registration, { vm.registration = it.uppercase() }, "Registration", modifier = androidx.compose.ui.Modifier.weight(1f))
            TextInput(vm.year, { vm.year = it.filter(Char::isDigit).take(4) }, "Year", keyboard = KeyboardType.Number, modifier = androidx.compose.ui.Modifier.weight(1f))
        }
        PickerInput(vm.status, VehicleStatus.entries, "Status", { it.label }, { vm.status = it })
        if (vm.status != VehicleStatus.UNDER_CONSIDERATION) DateInput(vm.purchaseDate, { vm.purchaseDate = it }, "Date bought")
        MoneyInput(vm.buying, { vm.buying = it }, "Buying price")

        SectionHeader("Costs")
        DealExpenseKind.entries.forEach { k ->
            MoneyInput(vm.costs[k].orEmpty(), { vm.costs[k] = it }, k.label, required = false)
        }
        vm.totalCostMinor?.let { InfoRow("Total cost", formatMoney(it)) }

        SectionHeader("Sale")
        if (vm.sold) {
            MoneyInput(vm.selling, { vm.selling = it }, "Selling price")
            DateInput(vm.saleDate, { vm.saleDate = it }, "Date sold")
        } else {
            MoneyInput(vm.expected, { vm.expected = it }, "Expected selling price", required = false, supporting = "Used to project profit on stock")
        }
        val total = vm.totalCostMinor
        val price = if (vm.sold) parseMoneyInput(vm.selling) else parseMoneyInput(vm.expected)
        if (total != null && price != null && price > 0) {
            InfoRow(if (vm.sold) "Net deal profit" else "Projected profit", formatMoney(price - total, showSign = true),
                valueColor = if (price >= total) DbTheme.colors.positive else DbTheme.colors.negative)
        }

        SectionHeader("Profit sharing")
        val rules = vm.arrangements
        if (rules.isNotEmpty()) {
            PickerInput(vm.arrangementId, rules.map { it.id }, "Sharing rule", { id ->
                rules.firstOrNull { it.id == id }?.let { a -> "${a.name} · you ${MoneyFormatter.percent(a.personalShareBps, 0)}" } ?: "Choose"
            }, { vm.arrangementId = it })
        }
        SecondaryAction("New sharing rule", { addingRule = true })

        TextInput(vm.condition, { vm.condition = it }, "Condition (optional)")
        TextInput(vm.notes, { vm.notes = it }, "Notes", singleLine = false)
    }
    if (addingRule) {
        ArrangementDialog(onDismiss = { addingRule = false }, onSave = { name, partner, investor -> vm.addArrangement(name, partner, investor); addingRule = false })
    }
}

@Composable
private fun ArrangementDialog(onDismiss: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var partner by remember { mutableStateOf("30") }
    var investor by remember { mutableStateOf("0") }
    val p = parseRateBps(partner)
    val i = parseRateBps(investor)
    val valid = name.isNotBlank() && p != null && i != null && p + i <= 10_000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New sharing rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, "Name", supporting = "e.g. Peter partnership")
                TextInput(partner, { partner = it }, "Partner share (% of profit)", keyboard = KeyboardType.Decimal)
                TextInput(investor, { investor = it }, "Investor share (% of profit)", keyboard = KeyboardType.Decimal)
                if (p != null && i != null && p + i <= 10_000) {
                    Text("You keep ${rateToInput(10_000 - p - i)}%", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name.trim(), p ?: 0, i ?: 0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
