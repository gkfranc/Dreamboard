package com.dreamboard.app.feature.manage

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.EditorScaffold
import com.dreamboard.app.core.designsystem.component.EmptyState
import com.dreamboard.app.core.designsystem.component.MoneyInput
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.PickerInput
import com.dreamboard.app.core.designsystem.component.PrimaryAction
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SecondaryAction
import com.dreamboard.app.core.designsystem.component.Tag
import com.dreamboard.app.core.designsystem.component.TextInput
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.component.moneyToInput
import com.dreamboard.app.core.designsystem.component.parseMoneyInput
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.domain.model.Asset
import com.dreamboard.app.domain.model.AssetCategory
import com.dreamboard.app.domain.model.ValueType
import com.dreamboard.app.domain.repository.FinanceRepository
import com.dreamboard.app.domain.repository.SnapshotMethod
import com.dreamboard.app.domain.repository.ValueHistoryLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

internal val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@HiltViewModel
class AssetsViewModel @Inject constructor(private val repo: FinanceRepository) : ViewModel() {
    val assets = repo.assets
    val snapshots = repo.netWorthHistory
    fun recordSnapshot() = viewModelScope.launch { repo.recordSnapshot(SnapshotMethod.MANUAL) }
}

@Composable
fun AssetsScreen(onBack: () -> Unit, onEdit: (String?) -> Unit, viewModel: AssetsViewModel = hiltViewModel()) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Assets", overline = "What you own", onBack = onBack) }
        item {
            DbCard {
                Overline("Total assets", color = DbTheme.colors.gold)
                MoneyText(assets.sumOf { it.valueMinor }, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                PrimaryAction("Add asset", { onEdit(null) }, Modifier.fillMaxWidth())
            }
        }
        if (assets.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.AccountBalanceWallet, "No assets yet",
                    "Add bank and M-Pesa balances, SACCO savings, investments, property, vehicles and your own Ignite equity.")
            }
        }
        AssetCategory.entries.forEach { cat ->
            val inCat = assets.filter { it.category == cat }
            if (inCat.isNotEmpty()) {
                item(key = "h-${cat.name}") { Overline(cat.label, modifier = Modifier.padding(top = 8.dp)) }
                items(inCat, key = { it.id }) { a ->
                    DbCard(onClick = { onEdit(a.id) }, contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Tag(a.valueType.label)
                                    if (a.isGoalFund) Tag("Porsche fund", color = DbTheme.colors.gold)
                                }
                            }
                            MoneyText(a.valueMinor, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
        item {
            DbCard {
                Overline("Net-worth snapshots")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dreamboard saves a snapshot of your totals automatically once a week. Record one now after a big update.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryAction("Record snapshot now", { viewModel.recordSnapshot() }, Modifier.fillMaxWidth())
                snapshots.takeLast(6).reversed().forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(s.date.format(SHORT_DATE), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
                        MoneyText(s.netWorthMinor, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@HiltViewModel
class AssetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val existing: Asset? = savedStateHandle.get<String>("assetId")?.let { id -> repo.assets.value.firstOrNull { it.id == id } }
    val isNew = existing == null
    var name by mutableStateOf(existing?.name ?: "")
    var category by mutableStateOf(existing?.category ?: AssetCategory.CASH_BANK)
    var institution by mutableStateOf(existing?.institution ?: "")
    var value by mutableStateOf(moneyToInput(existing?.valueMinor))
    var valueType by mutableStateOf(existing?.valueType ?: ValueType.ACTUAL)
    var isGoalFund by mutableStateOf(existing?.isGoalFund ?: false)
    var reason by mutableStateOf("")

    private val _history = MutableStateFlow<List<ValueHistoryLine>>(emptyList())
    val history = _history.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    init {
        existing?.let { a -> viewModelScope.launch { _history.value = repo.assetValuations(a.id) } }
    }

    val canSave: Boolean get() = name.isNotBlank() && parseMoneyInput(value) != null

    fun save() = viewModelScope.launch {
        val minor = parseMoneyInput(value) ?: return@launch
        repo.saveAsset(
            Asset(existing?.id ?: UUID.randomUUID().toString(), name, category, minor, institution, valueType, isGoalFund),
            reason.ifBlank { "Value updated" },
        )
        _done.value = true
    }

    fun delete() = viewModelScope.launch {
        existing?.let { repo.deleteAsset(it.id) }
        _done.value = true
    }
}

@Composable
fun AssetEditorScreen(onBack: () -> Unit, viewModel: AssetEditorViewModel = hiltViewModel()) {
    val done by viewModel.done.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    val vm = viewModel
    EditorScaffold(
        title = if (vm.isNew) "Add asset" else "Edit asset", overline = "Assets", onBack = onBack,
        canSave = vm.canSave, onSave = { vm.save() }, onDelete = if (vm.isNew) null else ({ vm.delete() }),
    ) {
        TextInput(vm.name, { vm.name = it }, "Name", supporting = "e.g. Equity current account, KCB MMF, Plot in Kitengela")
        PickerInput(vm.category, AssetCategory.entries, "Category", { it.label }, { vm.category = it })
        TextInput(vm.institution, { vm.institution = it }, "Institution (optional)")
        MoneyInput(vm.value, { vm.value = it }, "Current value")
        PickerInput(vm.valueType, ValueType.entries.filter { it != ValueType.FORECAST }, "Value is", { it.label }, { vm.valueType = it })
        if (!vm.isNew) TextInput(vm.reason, { vm.reason = it }, "Reason for change (optional)", supporting = "Kept in the audit history")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dedicated Porsche fund", style = MaterialTheme.typography.titleSmall)
                Text("Only assets marked here count as Porsche fund progress.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
            }
            Spacer(Modifier.width(8.dp))
            Switch(vm.isGoalFund, { vm.isGoalFund = it }, colors = SwitchDefaults.colors(checkedTrackColor = DbTheme.colors.gold))
        }
        if (history.isNotEmpty()) {
            Overline("Value history", modifier = Modifier.padding(top = 8.dp))
            history.forEach { h ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(h.date.format(SHORT_DATE), style = MaterialTheme.typography.bodySmall)
                        Text(h.note, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                    }
                    Text(formatMoney(h.valueMinor), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
