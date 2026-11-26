package com.dreamboard.app.core

import com.dreamboard.app.core.importer.CarName
import com.dreamboard.app.core.importer.FlipTracker
import com.dreamboard.app.domain.model.DealExpenseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlipTrackerTest {
    private val header = listOf(
        "#", "Car", "Amount Sent to Peter", "Date Bought", "Date Sold", "Cycle Time", "Buying Price", "Brokers", "Transfer",
        "Marketing", "Repairs", "Mileage (Fuel)", "Logbook", "Showroom", "Selling Price", "Profit", "Peter", "Net Profit", "Peter to send back",
    )

    private fun row(vararg cells: String) = cells.toList()

    @Test fun carNames() {
        assertEquals(CarName("Toyota", "Fielder", 2014, "KDD"), CarName.parse("Toyota Fielder 2014 (KDD)"))
        assertEquals(CarName("Nissan", "Note", 0, "Blue KCR"), CarName.parse("Nissan Note (Blue KCR)"))
        assertEquals(CarName("Honda", "Fit Shuttle", 0, ""), CarName.parse("Honda Fit Shuttle"))
    }

    @Test fun columnsFromTrackerHeader() {
        val c = FlipTracker.columns(header)!!
        assertEquals(1, c.car); assertEquals(6, c.buying); assertEquals(14, c.selling)
        assertEquals(15, c.profit); assertEquals(16, c.partner); assertEquals(17, c.netProfit); assertEquals(18, c.toSendBack)
        assertEquals("Peter", c.partnerName)
        assertEquals(7, c.expenses[DealExpenseKind.BROKER_COMMISSION]); assertEquals(11, c.expenses[DealExpenseKind.FUEL])
        assertEquals(12, c.expenses[DealExpenseKind.LOGBOOK]); assertEquals(13, c.expenses[DealExpenseKind.SHOWROOM])
    }

    @Test fun parsesDealsAndCrossChecksProfit() {
        val rows = listOf(
            header,
            row("2", "Toyota Fielder 2014 (KDD)", "", "03-Mar-26", "18-Mar-26", "15", "1,020,000", "20,000", "2,650", "10,000", "10,500", "1,000", "-", "8,000", "1,200,000", "127,850", "38,355", "89,495", ""),
            row("1", "Suzuki Wagon R (KDQ)", "", "25-Feb-26", "02-Mar-26", "5", "530,000", "10,000", "2,650", "-", "-", "", "", "8,000", "620,000", "72,000", "21,600", "50,400", ""),
            row("14", "Toyota Fielder KCH", "850000", "08-Oct-26", "", "", "780,000", "20,000", "2,650", "", "31,700", "", "", "8,000", "", "", "", "", ""),
            row("13", "Toyota Premio (KCD)", "Paid direct to Mahugu", "06-Oct-26", "13-Sept-26", "", "900,000", "", "", "", "5,000", "", "2,500", "8,000", "1,050,000", "134,500", "", "", ""),
        )
        val r = FlipTracker.parse(rows)
        assertTrue(r.errors.isEmpty())
        val fielder = r.rows[0]
        assertEquals(12_785_000L, fielder.computedProfitMinor)
        assertFalse(fielder.profitMismatch)
        assertEquals(3_000L, fielder.partnerShareBps)
        assertTrue(fielder.isSold)
        assertEquals(LocalDate.of(2026, 3, 3), fielder.bought)

        val wagon = r.rows[1]
        assertEquals(6_935_000L, wagon.computedProfitMinor) // sheet says 72,000: transfer not deducted there
        assertTrue(wagon.profitMismatch)

        val kch = r.rows[2]
        assertFalse(kch.isSold)
        assertNull(kch.partnerShareBps)
        assertEquals("Amount sent to Peter: KES 850,000", kch.notes)

        val premio = r.rows[3]
        assertEquals("Amount sent: Paid direct to Mahugu", premio.notes)
        assertEquals(LocalDate.of(2026, 9, 13), premio.sold)
        assertEquals(250_000L, premio.expenses[DealExpenseKind.LOGBOOK])
    }
}
