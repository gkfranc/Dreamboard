package com.dreamboard.app.feature.manage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.dreamboard.app.core.importer.CsvParser
import com.dreamboard.app.core.importer.XlsxReader
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads a CSV, .xlsx or Google Sheet picked with the system file picker. Everything stays on the phone. */
object SpreadsheetFiles {
    private const val MAX_FILE_BYTES = 25L * 1024 * 1024

    val PICKER_TYPES = arrayOf(
        "text/csv", "text/comma-separated-values", "text/plain", "application/csv",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream",
        "application/vnd.google-apps.spreadsheet",
    )

    data class File(val name: String, val isXlsx: Boolean, val sheets: List<XlsxReader.Sheet>)

    /** Blocking: call off the main thread. Throws with a readable message on failure. */
    fun read(context: Context, uri: Uri): File {
        val resolver = context.contentResolver
        val name = displayName(context, uri) ?: "spreadsheet"
        val mime = resolver.getType(uri).orEmpty()
        if (name.endsWith(".xls", true) || mime == "application/vnd.ms-excel") {
            throw IllegalArgumentException("old .xls files aren't supported. Save or download it as .xlsx or .csv and pick that file.")
        }
        val xlsx = name.endsWith(".xlsx", true) || mime.contains("spreadsheetml")
        val (stream, asXlsx) = openStream(context, uri, xlsx)
        val sheets = stream.use { s ->
            if (asXlsx) XlsxReader.read(s)
            else listOf(XlsxReader.Sheet(name, CsvParser.parse(String(s.readLimited(), Charsets.UTF_8))))
        }.filter { sh -> sh.rows.any { r -> r.any { it.isNotBlank() } } }
        return File(name, asXlsx, sheets)
    }

    /**
     * Google Sheets picked from the Drive app are "virtual" files with no bytes of their own,
     * so they are opened as the .xlsx (or CSV) export that Drive provides.
     */
    private fun openStream(context: Context, uri: Uri, xlsx: Boolean): Pair<InputStream, Boolean> {
        val resolver = context.contentResolver
        val googleSheet = resolver.getType(uri).orEmpty().contains("google-apps.spreadsheet")
        val virtual = runCatching {
            DocumentsContract.isDocumentUri(context, uri) &&
                resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
                    c.moveToFirst() && (c.getInt(0) and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0
                } == true
        }.getOrDefault(false)
        if (virtual || googleSheet) {
            val types = resolver.getStreamTypes(uri, "*/*")?.toList().orEmpty()
            val xlsxType = types.firstOrNull { it.contains("spreadsheetml") }
            val csvType = types.firstOrNull { it.contains("csv") }
            val pick = xlsxType ?: csvType ?: error("Drive can't export this file as .xlsx or .csv")
            val afd = resolver.openTypedAssetFileDescriptor(uri, pick, null) ?: error("couldn't open the file")
            return afd.createInputStream() to (pick == xlsxType)
        }
        return (resolver.openInputStream(uri) ?: error("couldn't open the file")) to xlsx
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun InputStream.readLimited(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_FILE_BYTES) throw IllegalArgumentException("the file is larger than 25 MB")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
