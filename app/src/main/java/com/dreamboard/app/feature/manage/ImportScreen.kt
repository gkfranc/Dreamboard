package com.dreamboard.app.feature.manage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material3.Switch
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
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.importer.ColumnMapping
import com.dreamboard.app.core.importer.CsvParser
import com.dreamboard.app.core.importer.Dedupe
import com.dreamboard.app.core.importer.ImportParsing
import com.dreamboard.app.core.importer.ParseResult
import com.dreamboard.app.core.importer.ParsedRow
import com.dreamboard.app.core.importer.RowError
import com.dreamboard.app.core.importer.XlsxReader
import com.dreamboard.app.domain.model.Account
import com.dreamboard.app.domain.model.BudgetCategory
import com.dreamboard.app.domain.model.ImportBatch
import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.TransactionSource
import com.dreamboard.app.domain.model.TransactionType
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

private const val MAX_FILE_BYTES = 25L * 1024 * 1024

/** One row of the preview with its duplicate verdict. */
data class PreviewRow(val row: ParsedRow, val hash: String, val duplicate: Boolean)

data class ImportUiState(
    val loading: Boolean = false,
    val fileName: String? = null,
    val isXlsx: Boolean = false,
    val sheets: List<XlsxReader.Sheet> = emptyList(),
    val sheetIndex: Int = 0,
    val headerRow: Int = 0,
    val mapping: ColumnMapping = ColumnMapping(),
    /** For a single amount column holding only positive spending figures (typical personal expense sheets). */
    val positiveIsSpending: Boolean = false,
    /** Import every sheet whose header row matches the selected one (e.g. one sheet per month). */
    val allMatchingSheets: Boolean = false,
    val accountId: String = "",
    val preview: List<PreviewRow> = emptyList(),
    val errors: List<RowError> = emptyList(),
    val skippedBlank: Int = 0,
    val message: String? = null,
    val result: ImportBatch? = null,
) {
    val rows: List<List<String>> get() = sheets.getOrNull(sheetIndex)?.rows.orEmpty()
    val header: List<String> get() = rows.getOrNull(headerRow).orEmpty()
    val newRows: List<PreviewRow> get() = preview.filterNot { it.duplicate }
    val matchingSheets: List<Int> get() {
        val key = headerKey(header)
        return sheets.indices.filter { i ->
            val r = sheets[i].rows
            headerKey(r.getOrNull(ImportParsing.detectHeaderRow(r)).orEmpty()) == key
        }
    }
}

private fun headerKey(h: List<String>) = h.map { it.trim().lowercase(Locale.ROOT) }.dropLastWhile { it.isEmpty() }

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state = _state.asStateFlow()
    val accounts get() = repo.accounts.value

    init {
        _state.update { it.copy(accountId = repo.accounts.value.firstOrNull()?.id.orEmpty()) }
    }

    fun open(uri: Uri) = viewModelScope.launch {
        _state.value = ImportUiState(loading = true, accountId = _state.value.accountId)
        try {
            val name = withContext(Dispatchers.IO) { displayName(uri) } ?: "spreadsheet"
            val mime = context.contentResolver.getType(uri).orEmpty()
            val xlsx = name.endsWith(".xlsx", true) || mime.contains("spreadsheetml")
            if (name.endsWith(".xls", true)) {
                fail("Old .xls files aren't supported. In Excel or Google Sheets, save or download as .xlsx or .csv and pick that file.")
                return@launch
            }
            val sheets = withContext(Dispatchers.IO) {
                val (stream, asXlsx) = openStream(uri, xlsx)
                stream.use { s ->
                    if (asXlsx) XlsxReader.read(s)
                    else {
                        val bytes = s.readNBytesCompat(MAX_FILE_BYTES)
                        listOf(XlsxReader.Sheet(name, CsvParser.parse(String(bytes, Charsets.UTF_8))))
                    }
                }
            }.filter { sh -> sh.rows.any { r -> r.any { it.isNotBlank() } } }
            if (sheets.isEmpty()) { fail("That file has no rows to import."); return@launch }
            _state.update { it.copy(loading = false, fileName = name, isXlsx = xlsx || isGoogleSheet(uri), sheets = sheets) }
            // Start on the first sheet that looks like a transaction log (skips dashboards and setup sheets).
            val first = sheets.indexOfFirst { sh ->
                ImportParsing.guessMapping(sh.rows.getOrNull(ImportParsing.detectHeaderRow(sh.rows)).orEmpty()).isComplete
            }.coerceAtLeast(0)
            selectSheet(first)
            if (_state.value.matchingSheets.size > 1) setAllMatchingSheets(true)
        } catch (e: Exception) {
            fail("Couldn't read the file: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fail(msg: String) = _state.update { it.copy(loading = false, message = msg) }

    fun selectSheet(index: Int) {
        val rows = _state.value.sheets.getOrNull(index)?.rows.orEmpty()
        val header = ImportParsing.detectHeaderRow(rows)
        _state.update { it.copy(sheetIndex = index, headerRow = header, mapping = ImportParsing.guessMapping(rows.getOrNull(header).orEmpty())) }
        analyse()
    }

    fun setHeaderRow(row: Int) {
        val rows = _state.value.rows
        _state.update { it.copy(headerRow = row, mapping = ImportParsing.guessMapping(rows.getOrNull(row).orEmpty())) }
        analyse()
    }

    fun setMapping(m: ColumnMapping) { _state.update { it.copy(mapping = m) }; analyse() }
    fun setAllMatchingSheets(v: Boolean) { _state.update { it.copy(allMatchingSheets = v) }; analyse() }
    fun setPositiveIsSpending(v: Boolean) { _state.update { it.copy(positiveIsSpending = v) }; analyse() }
    fun setAccount(id: String) { _state.update { it.copy(accountId = id) }; analyse() }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun analyse() = viewModelScope.launch {
        val s = _state.value
        if (!s.mapping.isComplete || s.accountId.isBlank()) {
            _state.update { it.copy(preview = emptyList(), errors = emptyList(), skippedBlank = 0) }
            return@launch
        }
        val parsed = withContext(Dispatchers.Default) {
            if (!s.allMatchingSheets) ImportParsing.parseRows(s.rows, s.headerRow, s.mapping)
            else {
                val parts = s.matchingSheets.map { i ->
                    val sh = s.sheets[i]
                    val hr = if (i == s.sheetIndex) s.headerRow else ImportParsing.detectHeaderRow(sh.rows)
                    sh.name to ImportParsing.parseRows(sh.rows, hr, s.mapping)
                }
                ParseResult(
                    parts.flatMap { it.second.rows },
                    parts.flatMap { (name, r) -> r.errors.map { e -> e.copy(reason = "$name: ${e.reason}") } },
                    parts.sumOf { it.second.skippedBlank },
                )
            }
        }
        val rows = if (s.positiveIsSpending && s.mapping.amount != null && s.mapping.direction == null) parsed.rows.map { it.copy(signedAmountMinor = -it.signedAmountMinor) } else parsed.rows
        val hashes = rows.map { Dedupe.hash(s.accountId, it.date, it.signedAmountMinor, it.description) }
        val existing = repo.existingHashCounts(hashes.toSet()).toMutableMap()
        // Multiset matching: two identical rows in the file only count as duplicates as many times as they already exist.
        val preview = rows.mapIndexed { i, r ->
            val left = existing[hashes[i]] ?: 0
            if (left > 0) existing[hashes[i]] = left - 1
            PreviewRow(r, hashes[i], left > 0)
        }
        _state.update { cur ->
            if (cur.sheetIndex != s.sheetIndex || cur.allMatchingSheets != s.allMatchingSheets || cur.mapping != s.mapping || cur.headerRow != s.headerRow || cur.accountId != s.accountId || cur.positiveIsSpending != s.positiveIsSpending) cur
            else cur.copy(preview = preview, errors = parsed.errors, skippedBlank = parsed.skippedBlank)
        }
    }

    fun import() = viewModelScope.launch {
        val s = _state.value
        val account: Account = repo.accounts.value.firstOrNull { it.id == s.accountId } ?: return@launch
        val rows = s.newRows
        val batchId = UUID.randomUUID().toString()
        val known = repo.budgetCategories.value.associateBy { it.name.lowercase(Locale.ROOT).trim() }.toMutableMap()
        val newCategories = ArrayList<BudgetCategory>()
        fun categoryFor(name: String?, spending: Boolean): String? {
            if (name.isNullOrBlank() || !spending) return null
            val key = name.lowercase(Locale.ROOT).trim()
            known[key]?.let { return it.id }
            val c = BudgetCategory(UUID.randomUUID().toString(), name.trim(), 0L)
            known[key] = c; newCategories += c
            return c.id
        }
        val source = if (s.isXlsx) TransactionSource.XLSX else TransactionSource.CSV
        val txs = rows.map { p ->
            val out = p.row.signedAmountMinor < 0
            Transaction(
                id = UUID.randomUUID().toString(), date = p.row.date, merchant = p.row.description,
                amountMinor = Math.abs(p.row.signedAmountMinor),
                type = if (out) TransactionType.EXPENSE else TransactionType.INCOME,
                categoryId = categoryFor(p.row.category, out), account = account.name, source = source,
                note = listOfNotNull(p.row.category?.takeIf { !out }?.let { "Sheet category: $it" }, p.row.note.ifBlank { null }).joinToString(" · "),
                accountId = account.id, importBatchId = batchId,
            )
        }
        val sheetName = s.sheets.getOrNull(s.sheetIndex)?.name
        val label = when {
            s.allMatchingSheets && s.matchingSheets.size > 1 -> "${s.fileName} › ${s.matchingSheets.size} sheets"
            s.isXlsx && sheetName != null -> "${s.fileName} › $sheetName"
            else -> s.fileName.orEmpty()
        }
        val batch = ImportBatch(
            id = batchId, fileName = label, importedAtMillis = System.currentTimeMillis(),
            rowCount = s.preview.size + s.errors.size, importedCount = txs.size,
            duplicateCount = s.preview.size - rows.size, errorCount = s.errors.size,
            errorReport = s.errors.joinToString("\n") { "Row ${it.rowNumber}: ${it.reason}" },
        )
        repo.importTransactions(batch, txs, rows.map { it.hash }, newCategories)
        _state.update { it.copy(result = batch) }
    }

    fun reset() { _state.value = ImportUiState(accountId = _state.value.accountId) }

    private fun isGoogleSheet(uri: Uri): Boolean =
        context.contentResolver.getType(uri).orEmpty().contains("google-apps.spreadsheet")

    /**
     * Opens the picked file. Google Sheets picked from the Drive app are "virtual" files with no
     * bytes of their own, so they are opened as an .xlsx (or CSV) export that Drive provides.
     */
    private fun openStream(uri: Uri, xlsx: Boolean): Pair<java.io.InputStream, Boolean> {
        val resolver = context.contentResolver
        val types = resolver.getStreamTypes(uri, "*/*")?.toList().orEmpty()
        val virtual = runCatching {
            android.provider.DocumentsContract.isDocumentUri(context, uri) &&
                resolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
                    c.moveToFirst() && (c.getInt(0) and android.provider.DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0
                } == true
        }.getOrDefault(false)
        if (virtual || isGoogleSheet(uri)) {
            val xlsxType = types.firstOrNull { it.contains("spreadsheetml") }
            val csvType = types.firstOrNull { it.contains("csv") }
            val pick = xlsxType ?: csvType ?: error("Drive can't export this file as .xlsx or .csv")
            val afd = resolver.openTypedAssetFileDescriptor(uri, pick, null) ?: error("Couldn't open the file")
            return afd.createInputStream() to (pick == xlsxType)
        }
        return (resolver.openInputStream(uri) ?: error("Couldn't open the file")) to xlsx
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}

private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > limit) throw IllegalArgumentException("File is larger than 25 MB")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private fun columnName(i: Int): String {
    var n = i + 1
    val sb = StringBuilder()
    while (n > 0) { val r = (n - 1) % 26; sb.append('A' + r); n = (n - 1) / 26 }
    return sb.reverse().toString()
}

@Composable
fun ImportScreen(onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.open(it) } }
    val launch = {
        picker.launch(arrayOf(
            "text/csv", "text/comma-separated-values", "text/plain", "application/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream",
            "application/vnd.google-apps.spreadsheet",
        ))
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Import spreadsheet", overline = "CSV or Excel (.xlsx)", onBack = onBack, showSettings = false) }
        val result = s.result
        if (result != null) {
            item {
                DbCard {
                    Text("Import complete", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Imported", result.importedCount.toString(), valueColor = DbTheme.colors.positive)
                    InfoRow("Skipped as duplicates", result.duplicateCount.toString())
                    InfoRow("Rows with errors", result.errorCount.toString(), valueColor = if (result.errorCount > 0) DbTheme.colors.negative else MaterialTheme.colorScheme.onSurface)
                    if (result.errorReport.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(result.errorReport.lines().take(20).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Imported rows are Expense (money out) or Income (money in). Open a transaction in Budget to reclassify it, e.g. as a transfer or card bill payment.",
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryAction("Import another", { viewModel.reset() }, Modifier.weight(1f))
                PrimaryAction("Done", onBack, Modifier.weight(1f))
            } }
            return@LazyColumn
        }
        item {
            Text(
                "Pick a CSV or .xlsx file from your phone or Google Drive. Everything is read on this phone; nothing is uploaded. " +
                    "For a Google Sheet, use File › Download › .xlsx or .csv first, or pick it from the Drive app.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
            )
        }
        item { PrimaryAction(if (s.fileName == null) "Choose file" else "Choose a different file", launch, Modifier.fillMaxWidth()) }
        s.message?.let { m -> item { Text(m, color = DbTheme.colors.negative, style = MaterialTheme.typography.bodyMedium) } }
        if (s.loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = DbTheme.colors.gold) } }
        if (s.fileName == null || s.sheets.isEmpty()) return@LazyColumn

        item { SectionHeader(s.fileName ?: "") }
        item {
            DbCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.sheets.size > 1) {
                        PickerInput(s.sheetIndex, s.sheets.indices.toList(), "Sheet", { s.sheets[it].name }, { viewModel.selectSheet(it) })
                        val matching = s.matchingSheets
                        if (matching.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("All ${matching.size} sheets with these columns", style = MaterialTheme.typography.bodyMedium)
                                    Text(matching.joinToString(", ") { s.sheets[it].name }, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                                }
                                Switch(s.allMatchingSheets, { viewModel.setAllMatchingSheets(it) })
                            }
                        }
                    }
                    val headerOptions = (0 until minOf(s.rows.size, 30)).toList()
                    PickerInput(s.headerRow, headerOptions, "Header row", { r ->
                        "Row ${r + 1}: " + s.rows[r].filter { it.isNotBlank() }.take(4).joinToString(" · ").take(60)
                    }, { viewModel.setHeaderRow(it) })
                    val accounts = viewModel.accounts
                    if (accounts.isNotEmpty()) {
                        PickerInput(s.accountId, accounts.map { it.id }, "Into account", { id -> accounts.firstOrNull { it.id == id }?.name ?: "Choose" }, { viewModel.setAccount(it) })
                    } else {
                        Text("Add an account first (Settings › Accounts).", color = DbTheme.colors.negative)
                    }
                }
            }
        }
        item { SectionHeader("Columns") }
        item {
            val cols: List<Int?> = listOf<Int?>(null) + s.header.indices.toList()
            fun label(i: Int?) = if (i == null) "Not used" else "${columnName(i)}: ${s.header.getOrNull(i).orEmpty().ifBlank { "(blank)" }}"
            val m = s.mapping
            DbCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerInput(m.date, cols, "Date", ::label, { viewModel.setMapping(m.copy(date = it)) })
                    PickerInput(m.description, cols, "Description", ::label, { viewModel.setMapping(m.copy(description = it)) })
                    PickerInput(m.amount, cols, "Amount (one column, signed)", ::label, { viewModel.setMapping(m.copy(amount = it, moneyIn = if (it != null) null else m.moneyIn, moneyOut = if (it != null) null else m.moneyOut)) })
                    if (m.amount == null) {
                        PickerInput(m.moneyOut, cols, "Money out", ::label, { viewModel.setMapping(m.copy(moneyOut = it)) })
                        PickerInput(m.moneyIn, cols, "Money in", ::label, { viewModel.setMapping(m.copy(moneyIn = it)) })
                    } else {
                        PickerInput(m.direction, cols, "Expense/Income column (optional)", ::label, { viewModel.setMapping(m.copy(direction = it)) })
                        if (m.direction == null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Positive amounts are spending", style = MaterialTheme.typography.bodyMedium)
                                Text("Turn on for an expense list where every amount is written as a positive number.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                            }
                            Switch(s.positiveIsSpending, { viewModel.setPositiveIsSpending(it) })
                        }
                    }
                    PickerInput(m.category, cols, "Category (optional)", ::label, { viewModel.setMapping(m.copy(category = it)) })
                    PickerInput(m.subcategory, cols, "Subcategory, kept in the note (optional)", ::label, { viewModel.setMapping(m.copy(subcategory = it)) })
                    PickerInput(m.notes, cols, "Notes (optional)", ::label, { viewModel.setMapping(m.copy(notes = it)) })
                    if (!m.isComplete) Text("Choose a date, description and at least one amount column.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.negative)
                }
            }
        }
        if (s.mapping.isComplete && s.accountId.isNotBlank()) {
            item { SectionHeader("Preview") }
            item {
                DbCard(contentPadding = 16.dp) {
                    InfoRow("Rows read", s.preview.size.toString())
                    InfoRow("New", s.newRows.size.toString(), valueColor = DbTheme.colors.positive)
                    InfoRow("Already imported (skipped)", (s.preview.size - s.newRows.size).toString())
                    InfoRow("Rows with errors", s.errors.size.toString(), valueColor = if (s.errors.isNotEmpty()) DbTheme.colors.negative else MaterialTheme.colorScheme.onSurface)
                    if (s.skippedBlank > 0) InfoRow("Blank rows ignored", s.skippedBlank.toString())
                    val out = s.newRows.filter { it.row.signedAmountMinor < 0 }.sumOf { -it.row.signedAmountMinor }
                    val inn = s.newRows.filter { it.row.signedAmountMinor > 0 }.sumOf { it.row.signedAmountMinor }
                    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Money out", style = MaterialTheme.typography.bodyMedium, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
                        MoneyText(out, style = MaterialTheme.typography.titleSmall)
                    }
                    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Money in", style = MaterialTheme.typography.bodyMedium, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
                        MoneyText(inn, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (s.errors.isNotEmpty()) {
                item {
                    Text(s.errors.take(5).joinToString("\n") { "Row ${it.rowNumber}: ${it.reason}" } + if (s.errors.size > 5) "\n…and ${s.errors.size - 5} more" else "",
                        style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
            }
            items(s.preview.take(25)) { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.row.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text("Row ${p.row.rowNumber} · ${p.row.date.format(SHORT_DATE)}" + (p.row.category?.let { " · $it" } ?: "") + if (p.duplicate) " · duplicate" else "",
                            style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                    }
                    MoneyText(p.row.signedAmountMinor, style = MaterialTheme.typography.bodyMedium, showSign = true,
                        color = if (p.duplicate) DbTheme.colors.textMuted else if (p.row.signedAmountMinor < 0) MaterialTheme.colorScheme.onSurface else DbTheme.colors.positive)
                }
            }
            if (s.preview.size > 25) item { Text("Showing the first 25 of ${s.preview.size} rows.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted) }
            item {
                val n = s.newRows.size
                if (n > 0) PrimaryAction("Import $n transaction${if (n == 1) "" else "s"}", { viewModel.import() }, Modifier.fillMaxWidth())
                else Text("Nothing new to import.", style = MaterialTheme.typography.bodyMedium, color = DbTheme.colors.textMuted)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
