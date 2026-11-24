package com.dreamboard.app.core

import com.dreamboard.app.core.importer.CsvParser
import com.dreamboard.app.core.importer.Dedupe
import com.dreamboard.app.core.importer.ImportParsing
import com.dreamboard.app.core.importer.XlsxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportParsingTest {
    @Test fun amounts() {
        assertEquals(123_450L, ImportParsing.parseAmount("KES 1,234.50"))
        assertEquals(-50_000L, ImportParsing.parseAmount("(500)"))
        assertEquals(-50_000L, ImportParsing.parseAmount("-500.00"))
        assertEquals(-50_000L, ImportParsing.parseAmount("500 DR"))
        assertEquals(123_450L, ImportParsing.parseAmount("1.234,50"))
        assertEquals(123_400_00L, ImportParsing.parseAmount("123,400"))
        assertEquals(150_000L, ImportParsing.parseAmount("Ksh 1500"))
        assertNull(ImportParsing.parseAmount("abc"))
        assertNull(ImportParsing.parseAmount(""))
    }

    @Test fun dates() {
        val d = LocalDate.of(2026, 11, 3)
        assertEquals(d, ImportParsing.parseDate("2026-11-03"))
        assertEquals(d, ImportParsing.parseDate("03/11/2026"))
        assertEquals(d, ImportParsing.parseDate("3/11/2026"))
        assertEquals(d, ImportParsing.parseDate("2026-11-03 14:22:10"))
        assertEquals(d, ImportParsing.parseDate("03 Nov 2026"))
        assertEquals(d, ImportParsing.parseDate("3-Nov-26"))
        assertEquals(d, ImportParsing.parseDate("46329")) // Excel serial
        assertNull(ImportParsing.parseDate("not a date"))
    }

    @Test fun csvQuotesAndDelimiters() {
        val rows = CsvParser.parse("﻿Date,Details,Amount\r\n2026-11-03,\"Naivas, Westlands\",\"-1,250.00\"\n\n2026-11-04,\"Say \"\"hi\"\"\",500\n")
        assertEquals(3, rows.size)
        assertEquals("Naivas, Westlands", rows[1][1])
        assertEquals("-1,250.00", rows[1][2])
        assertEquals("Say \"hi\"", rows[2][1])
        assertEquals(listOf("a", "b"), CsvParser.parse("a;b\n1;2")[0])
    }

    @Test fun mpesaStatementMapping() {
        val rows = listOf(
            listOf("M-PESA STATEMENT", "", "", "", "", "", ""),
            listOf("Receipt No.", "Completion Time", "Details", "Transaction Status", "Paid In", "Withdrawn", "Balance"),
            listOf("SKA1", "2026-11-03 08:10:00", "Pay Bill to Naivas", "Completed", "", "-1,250.00", "10,000.00"),
            listOf("SKA2", "2026-11-04 09:00:00", "Funds received from Jane", "Completed", "5,000.00", "", "15,000.00"),
            listOf("SKA3", "garbage", "x", "Completed", "1", "", ""),
        )
        val header = ImportParsing.detectHeaderRow(rows)
        assertEquals(1, header)
        val m = ImportParsing.guessMapping(rows[header])
        assertEquals(1, m.date); assertEquals(2, m.description); assertEquals(4, m.moneyIn); assertEquals(5, m.moneyOut)
        val r = ImportParsing.parseRows(rows, header, m)
        assertEquals(2, r.rows.size)
        assertEquals(-125_000L, r.rows[0].signedAmountMinor)
        assertEquals(500_000L, r.rows[1].signedAmountMinor)
        assertEquals(1, r.errors.size)
        assertEquals(5, r.errors[0].rowNumber)
    }

    @Test fun dedupeIgnoresCaseAndPunctuation() {
        val d = LocalDate.of(2026, 11, 3)
        assertEquals(Dedupe.hash("a", d, -100, "Naivas  Westlands!"), Dedupe.hash("a", d, -100, "naivas westlands"))
        assertNotEquals(Dedupe.hash("a", d, -100, "Naivas"), Dedupe.hash("a", d, -101, "Naivas"))
    }

    @Test fun xlsxReaderReadsSharedStringsAndNumbers() {
        val files = mapOf(
            "xl/workbook.xml" to """<workbook xmlns:r="r"><sheets><sheet name="Spending" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst><si><t>Date</t></si><si><t>Item</t></si><si><t>Amount</t></si><si><r><t>Fu</t></r><r><t>el</t></r></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                <row r="2"><c r="A2"><v>46329</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><f>SUM(1)</f><v>-6000</v></c></row>
                <row r="4"><c r="C4" t="inlineStr"><is><t>note</t></is></c></row>
            </sheetData></worksheet>""",
        )
        val bytes = ByteArrayOutputStream().also { bo ->
            ZipOutputStream(bo).use { z -> files.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        }.toByteArray()
        val sheets = XlsxReader.read(ByteArrayInputStream(bytes))
        assertEquals(1, sheets.size)
        assertEquals("Spending", sheets[0].name)
        val rows = sheets[0].rows
        assertEquals(listOf("Date", "Item", "Amount"), rows[0])
        assertEquals(listOf("46329", "Fuel", "-6000"), rows[1])
        assertEquals(emptyList<String>(), rows[2])
        assertEquals("note", rows[3][2])
        val m = ImportParsing.guessMapping(rows[0])
        val parsed = ImportParsing.parseRows(rows, 0, m)
        assertEquals(LocalDate.of(2026, 11, 3), parsed.rows[0].date)
        assertEquals(-600_000L, parsed.rows[0].signedAmountMinor)
        assertTrue(parsed.errors.any { it.rowNumber == 4 })
    }

    @Test fun columnIndex() {
        assertEquals(0, XlsxReader.columnIndex("A1"))
        assertEquals(27, XlsxReader.columnIndex("AB12"))
    }
}
