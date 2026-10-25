package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CsvImportTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun quotesCommasAndLineBreaksInsideCellsSurvive() {
        val rows = CsvImport.parse("Date,Narration,Amount\r\n25/10/2026,\"Dinner, with \"\"friends\"\"\",-1200.50\r\n26/10/2026,\"two\nlines\",-5\r\n")
        assertEquals(3, rows.size)
        assertEquals("Dinner, with \"friends\"", rows[1][1])
        assertEquals("two\nlines", rows[2][1])
    }

    @Test fun semicolonAndTabFilesAreRead() {
        assertEquals(listOf("a", "b", "c"), CsvImport.parse("a;b;c\n1;2;3")[0])
        assertEquals(listOf("a", "b"), CsvImport.parse("a\tb\n1\t2")[0])
    }

    @Test fun amountsInBankStyles() {
        assertEquals(120050L, CsvImport.amount("₹1,200.50"))
        assertEquals(-45000L, CsvImport.amount("(450)"))
        assertEquals(-120000L, CsvImport.amount("1200 Dr"))
        assertEquals(50000L, CsvImport.amount("500 Cr"))
        assertEquals(-9900L, CsvImport.amount("-99"))
        assertNull(CsvImport.amount("n/a"))
        assertNull(CsvImport.amount(""))
    }

    @Test fun theMappingIsGuessedFromTheHeader() {
        val table = CsvImport.parse("Txn Date,Narration,Withdrawal Amt,Deposit Amt,Balance\n25/10/2026,UPI-SWIGGY,450.00,,9000\n26/10/2026,SALARY,,42000,51000")
        val m = CsvImport.guess(table)!!
        assertTrue(m.hasHeader)
        assertEquals(0, m.date)
        assertEquals(1, m.description)
        assertEquals(2, m.debit)
        assertEquals(3, m.credit)
        assertEquals(DateStyle.DAY_MONTH_YEAR, m.dateStyle)
    }

    @Test fun debitCreditColumnsSetTheDirection() {
        val table = CsvImport.parse("Date,Narration,Debit,Credit\n25/10/2026,Swiggy,450,\n26/10/2026,Salary,,42000\nbad,row,,")
        val preview = CsvImport.preview(table, CsvImport.guess(table)!!, emptyList(), zone)
        assertEquals(2, preview.rows.size)
        assertEquals(1, preview.unreadable)
        assertTrue(preview.rows[0].isExpense)
        assertFalse(preview.rows[1].isExpense)
        assertEquals(rupees(450), preview.rows[0].paise)
    }

    @Test fun aSingleAmountColumnUsesItsSign() {
        val table = CsvImport.parse("Date,Description,Amount\n2026-10-25,Coffee,-180\n2026-10-26,Refund,99")
        val m = CsvImport.guess(table)!!
        assertEquals(DateStyle.YEAR_MONTH_DAY, m.dateStyle)
        val rows = CsvImport.preview(table, m, emptyList(), zone).rows
        assertTrue(rows[0].isExpense)
        assertFalse(rows[1].isExpense)
        val flipped = CsvImport.preview(table, m.copy(positiveIsSpending = true), emptyList(), zone).rows
        assertFalse(flipped[0].isExpense)
        assertTrue(flipped[1].isExpense)
    }

    @Test fun rowsThatMatchWhatIsAlreadyStoredAreFlagged() {
        val existing = TransactionEntity(type = TxType.EXPENSE, amount = rupees(180), accountId = 1, occurredAt = LocalDate.of(2026, 10, 25).atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        val table = CsvImport.parse("Date,Description,Amount\n25/10/2026,Coffee,-180\n25/10/2026,Coffee again,-180\n26/10/2026,Lunch,-300")
        val preview = CsvImport.preview(table, CsvImport.guess(table)!!, listOf(existing), zone)
        assertEquals(1, preview.duplicates)
        assertEquals(2, preview.fresh)
        assertTrue(preview.rows[0].duplicate)
        assertFalse(preview.rows[1].duplicate)
    }

    @Test fun indianDatesAreReadDayFirst() {
        val table = CsvImport.parse("Date,Description,Amount\n03/04/2026,x,-1\n12/04/2026,y,-1")
        assertEquals(DateStyle.DAY_MONTH_YEAR, CsvImport.guess(table)!!.dateStyle)
        assertEquals(LocalDate.of(2026, 4, 3), DateStyle.DAY_MONTH_YEAR.parse("03/04/2026"))
        assertNotNull(DateStyle.DAY_MON_YEAR.parse("05-Nov-2026"))
    }

    @Test fun tooLittleDataGivesNoGuess() {
        assertNull(CsvImport.guess(CsvImport.parse("Date,Amount")))
    }
}
