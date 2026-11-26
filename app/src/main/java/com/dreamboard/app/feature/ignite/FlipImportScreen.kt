package com.dreamboard.app.feature.ignite

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.PickerInput
import com.dreamboard.app.core.designsystem.component.PrimaryAction
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SecondaryAction
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.importer.FlipParseResult
import com.dreamboard.app.core.importer.FlipRow
import com.dreamboard.app.core.importer.FlipTracker
import com.dreamboard.app.core.importer.XlsxReader
import com.dreamboard.app.core.money.MoneyFormatter
import com.dreamboard.app.domain.model.DealExpense
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.FundingType
import com.dreamboard.app.domain.model.Vehicle
import com.dreamboard.app.domain.model.VehicleStatus
import com.dreamboard.app.domain.repository.FinanceRepository
import com.dreamboard.app.feature.manage.SpreadsheetFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

private val SHORT = DateTimeFormatter.ofPattern("d MMM yy")

enum class FlipAction(val label: String) { NEW("New"), UPDATE("Updated"), SAME("Unchanged") }

data class FlipLine(val row: FlipRow, val vehicle: Vehicle, val key: String, val action: FlipAction)

data class FlipImportState(
    val loading: Boolean = false,
    val fileName: String? = null,
    val sheets: List<XlsxReader.Sheet> = emptyList(),
    val sheetIndex: Int = 0,
    val parse: FlipParseResult? = null,
    val lines: List<FlipLine> = emptyList(),
    /** Sharing rule for cars whose sheet row has no partner amount yet (e.g. just sold). */
    val defaultArrangementId: String = "",
    val newArrangements: List<FundingArrangement> = emptyList(),
    val message: String? = null,
    val done: Boolean = false,
) {
    val toWrite: List<FlipLine> get() = lines.filter { it.action != FlipAction.SAME }
    val mismatches: List<FlipRow> get() = lines.map { it.row }.filter { it.profitMismatch }
}

@HiltViewModel
class FlipImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FlipImportState())
    val state = _state.asStateFlow()
    val arrangements: List<FundingArrangement> get() = repo.arrangements.value + _state.value.newArrangements

    fun open(uri: Uri) = viewModelScope.launch {
        _state.value = FlipImportState(loading = true, defaultArrangementId = _state.value.defaultArrangementId)
        val file = try {
            withContext(Dispatchers.IO) { SpreadsheetFiles.read(context, uri) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, message = "Couldn't read the file: ${e.message ?: e.javaClass.simpleName}") }
            return@launch
        }
        val first = file.sheets.indexOfFirst { FlipTracker.detect(it.rows) != null }
        if (first < 0) {
            _state.update { it.copy(loading = false, message = "No sheet in ${file.name} has a header row with Car and Buying Price columns.") }
            return@launch
        }
        _state.update { it.copy(loading = false, fileName = file.name, sheets = file.sheets) }
        selectSheet(first)
    }

    fun selectSheet(i: Int) = viewModelScope.launch {
        val sheet = _state.value.sheets.getOrNull(i) ?: return@launch
        val parse = withContext(Dispatchers.Default) { FlipTracker.parse(sheet.rows) }
        _state.update { it.copy(sheetIndex = i, parse = parse) }
        rebuild()
    }

    fun setDefaultArrangement(id: String) { _state.update { it.copy(defaultArrangementId = id) }; viewModelScope.launch { rebuild() } }

    /** Turns parsed rows into vehicles, matching sharing rules and existing cars. */
    private suspend fun rebuild() {
        val s = _state.value
        val parse = s.parse ?: return
        val partner = parse.columns?.partnerName ?: "Partner"
        val known = repo.arrangements.value.toMutableList()
        val created = ArrayList<FundingArrangement>()
        fun ruleFor(bps: Long): FundingArrangement =
            (known + created).firstOrNull { it.investorShareBps == 0L && it.partnerShareBps == bps }
                ?: FundingArrangement(UUID.randomUUID().toString(), "$partner partnership (${MoneyFormatter.percent(bps, 0)} to $partner)",
                    FundingType.PARTNERSHIP, 0L, bps, 10_000L - bps).also { created += it }
        val detected = parse.rows.mapNotNull { it.partnerShareBps }
        val commonBps = detected.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val defaultId = s.defaultArrangementId.takeIf { id -> (known + created).any { it.id == id } }
            ?: commonBps?.let { ruleFor(it).id }
            ?: known.firstOrNull()?.id.orEmpty()
        val keys = parse.rows.map { FlipTracker.importKey(it.name, it.bought) }
        val existing = repo.vehiclesByImportKey(keys)
        val lines = parse.rows.mapIndexed { i, r ->
            val key = keys[i]
            val old = existing[key]
            val arrangementId = r.partnerShareBps?.let { ruleFor(it).id } ?: old?.arrangementId ?: defaultId
            val v = Vehicle(
                id = old?.id ?: UUID.randomUUID().toString(),
                registration = r.car.registration, make = r.car.make, model = r.car.model, year = r.car.year,
                purchaseDate = r.bought, purchasePriceMinor = r.buyingMinor, arrangementId = arrangementId,
                condition = old?.condition.orEmpty(),
                expenses = r.expenses.map { (k, a) -> DealExpense(k, a) }.sortedBy { it.kind.ordinal },
                listingPriceMinor = old?.listingPriceMinor,
                expectedPriceMinor = if (r.isSold) old?.expectedPriceMinor else r.sellingMinor ?: old?.expectedPriceMinor,
                actualPriceMinor = if (r.isSold) r.sellingMinor else null,
                saleDate = if (r.isSold) r.sold else null,
                status = when {
                    r.isSold -> VehicleStatus.SOLD
                    old != null && old.status.inStock -> old.status
                    else -> VehicleStatus.PURCHASED
                },
                notes = r.notes,
            )
            FlipLine(r, v, key, when { old == null -> FlipAction.NEW; old == v -> FlipAction.SAME; else -> FlipAction.UPDATE })
        }
        _state.update { it.copy(lines = lines, newArrangements = created.filter { c -> lines.any { l -> l.vehicle.arrangementId == c.id } }, defaultArrangementId = defaultId) }
    }

    fun import() = viewModelScope.launch {
        val s = _state.value
        repo.importVehicles(s.toWrite.map { it.vehicle to it.key }, s.newArrangements)
        _state.update { it.copy(done = true) }
    }
}

@Composable
fun FlipImportScreen(onBack: () -> Unit, viewModel: FlipImportViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.open(it) } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Import flipping tracker", overline = "Ignite Motors", onBack = onBack, showSettings = false) }
        if (s.done) {
            item {
                DbCard {
                    Text("Import complete", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("New cars", s.lines.count { it.action == FlipAction.NEW }.toString(), valueColor = DbTheme.colors.positive)
                    InfoRow("Updated", s.lines.count { it.action == FlipAction.UPDATE }.toString())
                    InfoRow("Unchanged", s.lines.count { it.action == FlipAction.SAME }.toString())
                    Spacer(Modifier.height(8.dp))
                    Text("Import the tracker again whenever you update it: cars are matched by name and date bought, so nothing is duplicated.",
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
            item { PrimaryAction("Done", onBack, Modifier.fillMaxWidth()) }
            return@LazyColumn
        }
        item {
            Text(
                "Pick your flipping tracker (.xlsx, .csv, or the Google Sheet from Drive). Dreamboard reads the Car, dates, Buying Price, " +
                    "each cost column, Selling Price, Profit and your partner's column. Profit is recalculated from the costs and checked against the sheet.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
            )
        }
        item { PrimaryAction(if (s.fileName == null) "Choose file" else "Choose a different file", { picker.launch(SpreadsheetFiles.PICKER_TYPES) }, Modifier.fillMaxWidth()) }
        s.message?.let { m -> item { Text(m, color = DbTheme.colors.negative, style = MaterialTheme.typography.bodyMedium) } }
        if (s.loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = DbTheme.colors.gold) } }
        val parse = s.parse ?: return@LazyColumn

        item { SectionHeader(s.fileName.orEmpty()) }
        item {
            DbCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.sheets.size > 1) PickerInput(s.sheetIndex, s.sheets.indices.toList(), "Sheet", { s.sheets[it].name }, { viewModel.selectSheet(it) })
                    val rules = viewModel.arrangements
                    if (rules.isNotEmpty()) {
                        PickerInput(s.defaultArrangementId, rules.map { it.id }, "Sharing rule when the sheet shows no partner amount",
                            { id -> rules.firstOrNull { it.id == id }?.name ?: "Choose" }, { viewModel.setDefaultArrangement(it) })
                    }
                    InfoRow("Cars found", s.lines.size.toString())
                    InfoRow("New", s.lines.count { it.action == FlipAction.NEW }.toString(), valueColor = DbTheme.colors.positive)
                    InfoRow("Will update", s.lines.count { it.action == FlipAction.UPDATE }.toString())
                    InfoRow("Already up to date", s.lines.count { it.action == FlipAction.SAME }.toString())
                    InfoRow("Rows skipped", parse.errors.size.toString(), valueColor = if (parse.errors.isNotEmpty()) DbTheme.colors.negative else MaterialTheme.colorScheme.onSurface)
                    parse.errors.take(8).forEach { e -> Text("Row ${e.rowNumber}: ${e.reason}", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted) }
                }
            }
        }
        if (s.mismatches.isNotEmpty()) {
            item { SectionHeader("Profit check") }
            item {
                DbCard(contentPadding = 16.dp, color = DbTheme.colors.gold.copy(alpha = 0.08f)) {
                    Text("Dreamboard works out profit as selling price minus buying price minus every cost. These rows differ from the Profit written in the sheet, usually because a cost wasn't included there:",
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                    s.mismatches.forEach { r ->
                        val diff = (r.sheetProfitMinor ?: 0L) - (r.computedProfitMinor ?: 0L)
                        Text("${r.name}: sheet ${formatMoney(r.sheetProfitMinor ?: 0L)}, costs give ${formatMoney(r.computedProfitMinor ?: 0L)} (${formatMoney(diff, showSign = true)})",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Dreamboard uses the recalculated figure. Fix the sheet or the car afterwards if a cost is wrong.",
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
        }
        item { SectionHeader("Cars") }
        items(s.lines) { l ->
            val r = l.row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        listOfNotNull(
                            r.bought?.format(SHORT)?.let { "bought $it" },
                            r.sold?.takeIf { r.isSold }?.format(SHORT)?.let { "sold $it" } ?: "in stock",
                            r.partnerShareBps?.let { "partner ${MoneyFormatter.percent(it, 0)}" },
                            l.action.label,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (l.action == FlipAction.SAME) DbTheme.colors.textMuted else DbTheme.colors.gold,
                    )
                }
                r.computedProfitMinor?.let { p ->
                    MoneyText(p, style = MaterialTheme.typography.bodyMedium, showSign = true,
                        color = if (!r.isSold) DbTheme.colors.textMuted else if (p >= 0) DbTheme.colors.positive else DbTheme.colors.negative)
                }
            }
        }
        item {
            val n = s.toWrite.size
            if (n > 0) PrimaryAction("Import $n car${if (n == 1) "" else "s"}", { viewModel.import() }, Modifier.fillMaxWidth())
            else Text("Everything is already up to date.", style = MaterialTheme.typography.bodyMedium, color = DbTheme.colors.textMuted)
        }
        item { SecondaryAction("Cancel", onBack, Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
