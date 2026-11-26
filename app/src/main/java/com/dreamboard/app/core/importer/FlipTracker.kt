package com.dreamboard.app.core.importer

import com.dreamboard.app.domain.model.DealExpenseKind
import java.time.LocalDate
import java.util.Locale

/** "Toyota Fielder 2014 (KDD)" -> make Toyota, model Fielder, year 2014, registration KDD. */
data class CarName(val make: String, val model: String, val year: Int, val registration: String) {
    companion object {
        fun parse(raw: String): CarName {
            val text = raw.trim()
            val reg = Regex("\\(([^)]*)\\)").find(text)?.groupValues?.get(1)?.trim().orEmpty()
            var rest = text.replace(Regex("\\([^)]*\\)"), " ")
            val year = Regex("\\b(19[5-9]\\d|20\\d\\d)\\b").find(rest)?.value?.toInt() ?: 0
            if (year > 0) rest = rest.replaceFirst(year.toString(), " ")
            val words = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
            return CarName(words.firstOrNull().orEmpty(), words.drop(1).joinToString(" "), year, reg)
        }
    }
}

/** Column positions in a car-flipping tracker; null when the sheet doesn't have that column. */
data class FlipColumns(
    val car: Int,
    val amountSent: Int? = null,
    val bought: Int? = null,
    val sold: Int? = null,
    val buying: Int,
    val selling: Int? = null,
    val expenses: Map<DealExpenseKind, Int> = emptyMap(),
    val profit: Int? = null,
    val partner: Int? = null,
    val netProfit: Int? = null,
    val toSendBack: Int? = null,
    val partnerName: String = "Partner",
)

data class FlipRow(
    val rowNumber: Int,
    val name: String,
    val car: CarName,
    val bought: LocalDate?,
    val sold: LocalDate?,
    val buyingMinor: Long,
    val sellingMinor: Long?,
    val expenses: Map<DealExpenseKind, Long>,
    /** Profit as written in the sheet, for cross-checking. */
    val sheetProfitMinor: Long?,
    val partnerMinor: Long?,
    val notes: String,
) {
    val totalExpensesMinor: Long get() = expenses.values.sum()
    val computedProfitMinor: Long? get() = sellingMinor?.let { it - buyingMinor - totalExpensesMinor }
    val isSold: Boolean get() = sold != null && sellingMinor != null && sellingMinor > 0
    /** Partner share as basis points of profit, rounded to whole percent; null when the sheet doesn't show it. */
    val partnerShareBps: Long? get() {
        val p = partnerMinor ?: return null
        val profit = sheetProfitMinor ?: computedProfitMinor ?: return null
        if (profit <= 0 || p <= 0) return null
        return ((p * 100 + profit / 2) / profit) * 100
    }
    val profitMismatch: Boolean get() {
        val s = sheetProfitMinor ?: return false
        val c = computedProfitMinor ?: return false
        return s != c
    }
}

data class FlipParseResult(val columns: FlipColumns?, val headerRow: Int, val rows: List<FlipRow>, val errors: List<RowError>)

object FlipTracker {
    private fun norm(s: String) = s.lowercase(Locale.ROOT).trim()

    /** Finds the header row (a "Car" column plus a buying or selling price) and maps the columns. */
    fun detect(rows: List<List<String>>): Pair<Int, FlipColumns>? {
        rows.take(15).forEachIndexed { i, r -> columns(r)?.let { return i to it } }
        return null
    }

    fun columns(header: List<String>): FlipColumns? {
        val h = header.map(::norm)
        fun idx(pred: (String) -> Boolean): Int? = h.indexOfFirst(pred).takeIf { it >= 0 }
        val car = idx { it == "car" || it == "vehicle" || it == "car name" || it == "make & model" } ?: idx { it.startsWith("car") } ?: return null
        val buying = idx { it.startsWith("buying") || it.contains("purchase price") || it.contains("buy price") || it == "cost" } ?: return null
        val netProfit = idx { it.startsWith("net profit") }
        val profit = idx { (it == "profit" || it.startsWith("profit") || it == "gross profit") && !it.startsWith("net") }
        val toSendBack = idx { it.contains("to send") }
        val amountSent = idx { it.startsWith("amount sent") || it.contains("sent to") }
        // The partner's column: named after a person, sitting between Profit and Net Profit (e.g. "Peter").
        val partner = if (profit != null && netProfit != null && netProfit - profit == 2) profit + 1
        else idx { it.contains("partner") || it.contains("share") }
        val partnerName = partner?.let { header[it].trim() }?.takeIf { it.isNotEmpty() } ?: "Partner"
        val expenses = linkedMapOf<DealExpenseKind, Int>()
        idx { it.startsWith("broker") }?.let { expenses[DealExpenseKind.BROKER_COMMISSION] = it }
        idx { it.startsWith("transfer") }?.let { expenses[DealExpenseKind.TRANSFER_FEES] = it }
        idx { it.startsWith("marketing") || it.startsWith("advert") }?.let { expenses[DealExpenseKind.MARKETING] = it }
        idx { it.startsWith("repair") || it.startsWith("service") }?.let { expenses[DealExpenseKind.REPAIRS] = it }
        idx { it.contains("mileage") || it.contains("fuel") }?.let { expenses[DealExpenseKind.FUEL] = it }
        idx { it.startsWith("logbook") || it.startsWith("log book") }?.let { expenses[DealExpenseKind.LOGBOOK] = it }
        idx { it.startsWith("showroom") || it.startsWith("parking") }?.let { expenses[DealExpenseKind.SHOWROOM] = it }
        return FlipColumns(
            car = car, amountSent = amountSent,
            bought = idx { it.contains("bought") || it.contains("purchase date") || it == "date in" },
            sold = idx { it.contains("date sold") || it == "sold" || it.contains("sale date") || it == "date out" },
            buying = buying,
            selling = idx { it.startsWith("selling") || it.contains("sale price") || it.contains("sold for") },
            expenses = expenses, profit = profit, partner = partner, netProfit = netProfit, toSendBack = toSendBack,
            partnerName = partnerName,
        )
    }

    /** Amounts in tracker cells: blank or "-" means nothing spent. Null when the cell holds other text. */
    fun amount(cell: String): Long? {
        val t = cell.trim()
        if (t.isEmpty() || t.all { it == '-' || it == '–' || it == '—' }) return 0L
        return ImportParsing.parseAmount(t)
    }

    fun parse(rows: List<List<String>>): FlipParseResult {
        val (headerRow, c) = detect(rows) ?: return FlipParseResult(null, 0, emptyList(), listOf(RowError(0, "Couldn't find a header row with Car and Buying Price columns")))
        val out = ArrayList<FlipRow>()
        val errors = ArrayList<RowError>()
        for (i in (headerRow + 1) until rows.size) {
            val r = rows[i]
            val n = i + 1
            fun cell(idx: Int?) = idx?.let { r.getOrNull(it) }?.trim().orEmpty()
            val name = cell(c.car)
            if (name.isEmpty() || name.lowercase(Locale.ROOT).startsWith("total")) continue
            val buying = amount(cell(c.buying))
            if (buying == null || buying <= 0) { errors += RowError(n, "$name: no buying price"); continue }
            val expenses = linkedMapOf<DealExpenseKind, Long>()
            var bad: String? = null
            c.expenses.forEach { (k, idx) ->
                val a = amount(cell(idx))
                if (a == null) bad = "$name: can't read ${k.label} \"${cell(idx)}\"" else if (a != 0L) expenses[k] = a
            }
            val problem = bad
            if (problem != null) { errors += RowError(n, problem); continue }
            val selling = c.selling?.let { amount(cell(it)) }?.takeIf { it > 0 }
            val boughtRaw = cell(c.bought)
            val soldRaw = cell(c.sold)
            val bought = ImportParsing.parseDate(boughtRaw)
            val sold = ImportParsing.parseDate(soldRaw)
            if (boughtRaw.isNotEmpty() && bought == null) { errors += RowError(n, "$name: unreadable date bought \"$boughtRaw\""); continue }
            if (soldRaw.isNotEmpty() && sold == null) { errors += RowError(n, "$name: unreadable date sold \"$soldRaw\""); continue }
            val notes = buildList {
                val sent = cell(c.amountSent)
                if (sent.isNotEmpty()) {
                    val a = amount(sent)
                    add(if (a != null && a > 0) "Amount sent to ${c.partnerName}: KES ${MoneyText.plain(a)}" else "Amount sent: $sent")
                }
                val back = cell(c.toSendBack)
                if (back.isNotEmpty() && amount(back) != 0L) add("${c.partnerName} to send back: $back")
            }.joinToString("\n")
            out += FlipRow(
                rowNumber = n, name = name, car = CarName.parse(name), bought = bought, sold = sold,
                buyingMinor = buying, sellingMinor = selling, expenses = expenses,
                sheetProfitMinor = c.profit?.let { cell(it) }?.takeIf { it.isNotEmpty() }?.let { amount(it) },
                partnerMinor = c.partner?.let { cell(it) }?.takeIf { it.isNotEmpty() }?.let { amount(it) }?.takeIf { it > 0 },
                notes = notes,
            )
        }
        return FlipParseResult(c, headerRow, out, errors)
    }

    fun importKey(name: String, bought: LocalDate?): String = Dedupe.normalize(name) + "|" + (bought?.toString() ?: "")
}

/** Whole-shilling formatting for notes, without depending on Android or the UI layer. */
internal object MoneyText {
    fun plain(minor: Long): String = String.format(Locale.US, "%,d", minor / 100)
}
