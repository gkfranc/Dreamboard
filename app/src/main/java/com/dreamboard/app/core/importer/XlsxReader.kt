package com.dreamboard.app.core.importer

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Minimal, dependency-free XLSX reader. An .xlsx file is a zip of XML parts; this reads the
 * workbook's sheet names, shared strings and cell values. Formulas yield their cached values.
 * Dates arrive as Excel serial numbers and are converted later by [ImportParsing.parseDate].
 */
object XlsxReader {
    data class Sheet(val name: String, val rows: List<List<String>>)

    private const val MAX_PART_BYTES = 50L * 1024 * 1024

    fun read(input: InputStream): List<Sheet> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.removePrefix("/")
                if (!e.isDirectory && (name.startsWith("xl/") && name.endsWith(".xml") || name.endsWith(".rels"))) {
                    parts[name] = readLimited(zip)
                }
            }
        }
        val workbook = parts["xl/workbook.xml"] ?: throw IllegalArgumentException("Not an Excel .xlsx workbook")
        val rels = parts["xl/_rels/workbook.xml.rels"]?.let { relationships(it) } ?: emptyMap()
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        return sheets(workbook).mapNotNull { (name, rid) ->
            val target = rels[rid] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            parts[path]?.let { Sheet(name, cells(it, shared)) }
        }
    }

    private fun readLimited(zip: ZipInputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            require(total <= MAX_PART_BYTES) { "Spreadsheet part is too large" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        // Harden against XML external entity attacks from untrusted files.
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        f.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    private fun local(qName: String) = qName.substringAfter(':')

    private fun sheets(bytes: ByteArray): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        parse(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                if (local(qName) == "sheet") {
                    var rid: String? = null
                    for (i in 0 until a.length) if (local(a.getQName(i)) == "id") rid = a.getValue(i)
                    out += (a.getValue("name") ?: "Sheet") to (rid ?: "")
                }
            }
        })
        return out
    }

    private fun relationships(bytes: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        parse(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                if (local(qName) == "Relationship") out[a.getValue("Id") ?: ""] = a.getValue("Target") ?: ""
            }
        })
        return out
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        parse(bytes, object : DefaultHandler() {
            val sb = StringBuilder()
            var inT = false
            var inRph = false
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) { "si" -> sb.setLength(0); "t" -> inT = true; "rPh" -> inRph = true }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) { "si" -> out += sb.toString(); "t" -> inT = false; "rPh" -> inRph = false }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inT && !inRph) sb.append(ch, start, length) }
        })
        return out
    }

    private fun cells(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = java.util.TreeMap<Int, java.util.TreeMap<Int, String>>()
        parse(bytes, object : DefaultHandler() {
            var rowIndex = 0
            var col = 0
            var type: String? = null
            val text = StringBuilder()
            var capture = false
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) {
                    "row" -> rowIndex = a.getValue("r")?.toIntOrNull() ?: (rowIndex + 1)
                    "c" -> {
                        val ref = a.getValue("r")
                        col = if (ref != null) columnIndex(ref) else col + 1
                        type = a.getValue("t"); text.setLength(0)
                    }
                    "v", "t" -> capture = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = text.toString()
                        val value = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        if (value.isNotEmpty()) rows.getOrPut(rowIndex) { java.util.TreeMap() }[col] = value.trim()
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (capture) text.append(ch, start, length) }
        })
        if (rows.isEmpty()) return emptyList()
        // Keep list index = Excel row number - 1, so error reports name the real row.
        val width = rows.values.maxOf { it.lastKey() } + 1
        return (1..rows.lastKey()).map { n -> rows[n]?.let { r -> List(width) { r[it] ?: "" } } ?: emptyList() }
    }

    /** "C12" -> 2 (zero-based). */
    fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break }
        return n - 1
    }
}
