package com.dreamboard.app.core.importer

/** RFC 4180-style CSV reader: quoted fields, escaped quotes, newlines inside quotes, auto delimiter. */
object CsvParser {
    fun parse(text: String): List<List<String>> {
        val clean = text.removePrefix("﻿")
        val delimiter = detectDelimiter(clean)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < clean.length && clean[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                delimiter -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = ArrayList() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.map { r -> r.map { it.trim() } }.filter { r -> r.any { it.isNotEmpty() } }
    }

    private fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().take(10).joinToString("\n")
        return listOf(',', ';', '\t', '|').maxByOrNull { d -> sample.count { it == d } } ?: ','
    }
}
