package com.dreamboard.app.core.importer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Which column holds what. Indices are zero-based; null means not present. */
data class ColumnMapping(
    val date: Int? = null,
    val description: Int? = null,
    /** Single signed amount column: negative = money out. */
    val amount: Int? = null,
    /** Money out (debit / withdrawn / paid out). */
    val moneyOut: Int? = null,
    /** Money in (credit / deposit / paid in). */
    val moneyIn: Int? = null,
    val category: Int? = null,
    /** A column saying "Expense"/"Income" (or Debit/Credit) that sets the direction of a positive amount. */
    val direction: Int? = null,
    val subcategory: Int? = null,
    val notes: Int? = null,
) {
    val isComplete: Boolean get() = date != null && description != null && (amount != null || moneyOut != null || moneyIn != null)
}

data class ParsedRow(
    val rowNumber: Int,
    val date: LocalDate,
    val description: String,
    /** Positive = money in, negative = money out. */
    val signedAmountMinor: Long,
    val category: String?,
    val note: String = "",
)

data class RowError(val rowNumber: Int, val reason: String)

data class ParseResult(val rows: List<ParsedRow>, val errors: List<RowError>, val skippedBlank: Int)

object ImportParsing {

    private val DATE_HINTS = listOf("date", "completion time", "time", "posted", "value date", "transaction date", "trans date")
    private val DESC_HINTS = listOf("description", "details", "narration", "narrative", "particulars", "merchant", "payee", "memo", "item", "name")
    private val AMOUNT_HINTS = listOf("amount", "value", "kes", "ksh")
    private val OUT_HINTS = listOf("withdrawn", "withdrawal", "debit", "money out", "paid out", "dr", "out", "expense", "spent")
    private val IN_HINTS = listOf("paid in", "deposit", "credit", "money in", "cr", "in", "income", "received")
    private val CATEGORY_HINTS = listOf("category", "type of expense", "expense type", "class")
    private val DIRECTION_HINTS = listOf("type", "transaction type", "dr/cr", "debit/credit", "in/out", "direction")
    private val SUBCATEGORY_HINTS = listOf("subcategory", "sub-category", "sub category")
    private val NOTES_HINTS = listOf("notes", "note", "remarks", "comment", "comments")

    private val IN_WORDS = listOf("income", "received", "receipt", "credit", "deposit", "refund", "money in", "paid in", "in", "cr")
    private val OUT_WORDS = listOf("expense", "spent", "spend", "debit", "withdraw", "payment", "money out", "paid out", "out", "dr")

    /** +1 for money in, -1 for money out, null if the cell doesn't say. */
    fun directionOf(cell: String): Int? {
        val c = cell.lowercase(Locale.ROOT).trim()
        if (c.isEmpty()) return null
        val words = c.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        fun hit(list: List<String>) = list.any { w -> if (w.length <= 3) w in words else c.contains(w) }
        val inn = hit(IN_WORDS)
        val out = hit(OUT_WORDS)
        return when {
            inn && !out -> 1
            out && !inn -> -1
            else -> null
        }
    }

    /** Picks the header row: the first of the first 15 rows whose cells look like column names. */
    fun detectHeaderRow(rows: List<List<String>>): Int {
        rows.take(15).forEachIndexed { i, r ->
            val m = guessMapping(r)
            if (m.date != null && (m.amount != null || m.moneyIn != null || m.moneyOut != null)) return i
        }
        return 0
    }

    fun guessMapping(header: List<String>): ColumnMapping {
        val h = header.map { it.lowercase(Locale.ROOT).trim() }
        fun find(hints: List<String>, exclude: Set<Int> = emptySet()): Int? {
            for (hint in hints) {
                h.forEachIndexed { i, cell -> if (i !in exclude && (cell == hint)) return i }
            }
            for (hint in hints.filter { it.length > 2 }) {
                h.forEachIndexed { i, cell -> if (i !in exclude && cell.contains(hint)) return i }
            }
            return null
        }
        val date = find(DATE_HINTS)
        val used = setOfNotNull(date)
        val out = find(OUT_HINTS, used)
        val inn = find(IN_HINTS, used + setOfNotNull(out))
        val amount = if (out == null && inn == null) find(AMOUNT_HINTS, used) else null
        val taken = used + setOfNotNull(out, inn, amount)
        val desc = find(DESC_HINTS, taken)
        val category = find(CATEGORY_HINTS, taken + setOfNotNull(desc) + subIdx(h))
        val used2 = taken + setOfNotNull(desc, category)
        val direction = if (amount != null) find(DIRECTION_HINTS, used2) else null
        val sub = find(SUBCATEGORY_HINTS, used2 + setOfNotNull(direction))
        val notes = find(NOTES_HINTS, used2 + setOfNotNull(direction, sub))
        return ColumnMapping(date, desc, amount, out, inn, category, direction, sub, notes)
    }

    private fun subIdx(h: List<String>): Set<Int> = h.indices.filter { i -> SUBCATEGORY_HINTS.any { h[i].contains(it) } }.toSet()

    fun parseRows(rows: List<List<String>>, headerRow: Int, m: ColumnMapping): ParseResult {
        val parsed = ArrayList<ParsedRow>()
        val errors = ArrayList<RowError>()
        var blank = 0
        for (i in (headerRow + 1) until rows.size) {
            val r = rows[i]
            val rowNumber = i + 1
            fun cell(idx: Int?) = idx?.let { r.getOrNull(it) }?.trim().orEmpty()
            if (r.all { it.isBlank() }) { blank++; continue }
            val date = parseDate(cell(m.date))
            if (date == null) { errors += RowError(rowNumber, "Unreadable date \"${cell(m.date)}\""); continue }
            val amount: Long? = when {
                m.amount != null -> parseAmount(cell(m.amount))?.let { a ->
                    when (m.direction?.let { directionOf(cell(it)) }) {
                        1 -> Math.abs(a)
                        -1 -> -Math.abs(a)
                        else -> a
                    }
                }
                else -> {
                    val out = cell(m.moneyOut).takeIf { it.isNotEmpty() }?.let { parseAmount(it) }
                    val inn = cell(m.moneyIn).takeIf { it.isNotEmpty() }?.let { parseAmount(it) }
                    if (out == null && inn == null) null else (inn?.let { Math.abs(it) } ?: 0L) - (out?.let { Math.abs(it) } ?: 0L)
                }
            }
            if (amount == null) { errors += RowError(rowNumber, "No readable amount"); continue }
            if (amount == 0L) { errors += RowError(rowNumber, "Amount is zero"); continue }
            val desc = cell(m.description).ifEmpty { "(no description)" }
            val note = listOf(cell(m.subcategory), cell(m.notes)).filter { it.isNotEmpty() && it != "-" }.joinToString(" · ")
            parsed += ParsedRow(rowNumber, date, desc, amount, cell(m.category).ifEmpty { null }, note)
        }
        return ParseResult(parsed, errors, blank)
    }

    /** "KES 1,234.50", "(500)", "-500", "500 DR", "1 234,50" -> minor units. Null if unreadable. */
    fun parseAmount(raw: String): Long? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        val upper = s.uppercase(Locale.ROOT)
        if (upper.endsWith("DR")) { negative = true; s = s.dropLast(2) } else if (upper.endsWith("CR")) s = s.dropLast(2)
        s = s.replace(Regex("(?i)kshs|ksh|kes|sh"), "").replace(" ", "").replace(" ", "")
        if (s.endsWith("-")) { negative = !negative; s = s.dropLast(1) }
        if (s.startsWith("-")) { negative = !negative; s = s.drop(1) }
        if (s.startsWith("+")) s = s.drop(1)
        // Decimal comma ("1.234,50" or "1234,50") vs thousands comma ("1,234.50").
        s = if (s.contains(',') && (!s.contains('.') && Regex(",\\d{1,2}$").containsMatchIn(s) || s.lastIndexOf(',') > s.lastIndexOf('.') && s.contains('.'))) {
            s.replace(".", "").replace(',', '.')
        } else s.replace(",", "")
        if (!Regex("^\\d+(\\.\\d+)?$").matches(s)) return null
        val minor = BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        return if (negative) -minor else minor
    }

    private val FORMATS: List<DateTimeFormatter> = listOf(
        "yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "d-M-yyyy", "dd.MM.yyyy", "d.M.yyyy",
        "dd MMM yyyy", "d MMM yyyy", "dd-MMM-yyyy", "d-MMM-yyyy", "dd MMMM yyyy", "d MMMM yyyy", "MMM d, yyyy", "MMMM d, yyyy",
        "dd/MM/yy", "d/M/yy", "dd-MMM-yy", "d-MMM-yy",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

    /**
     * Day-first (Kenyan convention) dates, ISO dates, month names, and Excel serial numbers.
     * A trailing time ("2026-11-03 14:22:10") is ignored.
     */
    fun parseDate(raw: String): LocalDate? {
        val s = raw.trim().replace(Regex("(?i)\\bsept\\b"), "Sep")
        if (s.isEmpty()) return null
        s.toDoubleOrNull()?.let { serial ->
            if (serial in 20_000.0..80_000.0) return LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
            return null
        }
        val candidates = listOf(s, s.substringBefore('T'), s.substringBefore(' '), s.split(' ').take(3).joinToString(" "))
        for (c in candidates.distinct()) for (f in FORMATS) {
            val d = runCatching { LocalDate.parse(c, f) }.getOrNull()
            if (d != null) return if (d.year < 100) d.plusYears(2000) else d
        }
        return null
    }
}
