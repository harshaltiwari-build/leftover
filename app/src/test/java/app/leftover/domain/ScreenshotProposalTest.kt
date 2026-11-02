package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the confirm screen shows after a payment screenshot is read: everything that was found, a guessed category and
 * account, and a warning for anything worth a second look. Nothing is saved from here; that's the person's tap.
 */
class ScreenshotProposalTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 2)
    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(20_000), includeInLeft = true)
    private val coffee = CategoryEntity(id = 3, name = "Coffee", icon = "coffee", color = "sand", kind = CategoryKind.EXPENSE)
    private val other = CategoryEntity(id = 9, name = "Other", icon = "other", color = "grey", kind = CategoryKind.EXPENSE)
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ledger(vararg txs: TransactionEntity, accounts: List<AccountEntity> = listOf(bank)) =
        Ledger(accounts, listOf(coffee, other), txs.toList(), emptyList(), emptyList(), PayCycle.forPayday(1, today), today)

    private fun shot(
        amount: Long? = rupees(1_000), merchant: String = "Rohan Mehta", date: LocalDate? = today.minusDays(1), time: LocalTime? = LocalTime.of(22, 32),
        status: ScreenshotStatus = ScreenshotStatus.SUCCESS, read: Boolean = true, income: Boolean = false, ref: String = "UPI ref 123456789012",
    ) = ScreenshotAutoSave.Shot(read, income, status, amount, merchant, date, time, ref, receipt = "receipt-1.jpg")

    private fun propose(s: ScreenshotAutoSave.Shot = shot(), l: Ledger = ledger()) = ScreenshotProposal.of(s, l, zone, now = at(today))

    @Test fun `everything read is in the proposal, in your account, with a guessed category`() {
        val p = propose(shot(merchant = "Third Wave Coffee"))!!
        assertEquals(rupees(1_000), p.tx.amount)
        assertEquals("Third Wave Coffee", p.tx.merchant)
        assertEquals(TxType.EXPENSE, p.tx.type)
        assertEquals(TxSource.SCAN, p.tx.source)
        assertEquals("UPI ref 123456789012", p.tx.note)
        assertEquals("receipt-1.jpg", p.tx.receiptFile)
        assertEquals(today.minusDays(1).atTime(22, 32).atZone(zone).toInstant().toEpochMilli(), p.tx.occurredAt)
        assertEquals("HDFC", p.accountName)
        assertEquals("Coffee", p.categoryName)
        assertEquals(emptyList<ScreenshotProposal.Warning>(), p.warnings)
    }

    @Test fun `only a successful payment you made becomes a proposal`() {
        assertNull(propose(shot(read = false)))
        assertNull(propose(shot(income = true)))
        assertNull(propose(shot(status = ScreenshotStatus.FAILED)))
        assertNull(propose(shot(status = ScreenshotStatus.PENDING)))
        assertNull(propose(shot(status = ScreenshotStatus.UNKNOWN)))
        assertNull(propose(shot(amount = null)))
        assertNull(propose(shot(amount = 0)))
    }

    @Test fun `with nowhere to put it there is no proposal`() {
        val savingsOnly = AccountEntity(id = 2, name = "Goals", type = AccountType.SAVINGS, openingBalance = 0, includeInLeft = false)
        assertNull(propose(l = ledger(accounts = listOf(savingsOnly))))
    }

    @Test fun `missing pieces are filled in and flagged, not guessed silently`() {
        val p = propose(shot(merchant = "  ", date = null, time = null))!!
        assertEquals("", p.tx.merchant)
        assertEquals(today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() / 86_400_000, p.tx.occurredAt / 86_400_000)
        assertTrue(ScreenshotProposal.Warning.NO_PAYEE in p.warnings)
        assertTrue(ScreenshotProposal.Warning.DATE_ASSUMED in p.warnings)
    }

    @Test fun `a payment you already have is flagged`() {
        val mine = TransactionEntity(id = 7, type = TxType.EXPENSE, amount = rupees(1_000), accountId = 1, categoryId = 3, merchant = "Rohan", occurredAt = at(today.minusDays(1)))
        assertTrue(ScreenshotProposal.Warning.DUPLICATE in propose(l = ledger(mine))!!.warnings)
        val sameRef = mine.copy(id = 8, amount = rupees(5), note = "UPI ref 123456789012", occurredAt = at(today.minusDays(30)))
        assertTrue(ScreenshotProposal.Warning.DUPLICATE in propose(l = ledger(sameRef))!!.warnings)
    }

    @Test fun `old, large and non-recent payments are flagged but still proposed`() {
        assertTrue(ScreenshotProposal.Warning.OLD in propose(shot(date = today.minusDays(9)))!!.warnings)
        assertFalse(ScreenshotProposal.Warning.OLD in propose(shot(date = today.minusDays(1)))!!.warnings)
        assertTrue(ScreenshotProposal.Warning.LARGE in propose(shot(amount = rupees(75_000)))!!.warnings)
    }

    @Test fun `with Hide balances the same warnings carry no amounts`() {
        val mine = TransactionEntity(id = 7, type = TxType.EXPENSE, amount = rupees(1_000), accountId = 1, categoryId = 3, merchant = "Rohan", occurredAt = at(today.minusDays(1)))
        ScreenshotProposal.Warning.entries.forEach { w -> assertFalse(w.text(hidden = true), w.text(hidden = true).contains("₹")) }
        assertEquals("Looks like one you already have", ScreenshotProposal.Warning.DUPLICATE.text(false))
        assertTrue(propose(l = ledger(mine)) != null)
    }

    // Attack: hundreds of thousands of entries must not make the confirm screen slow to appear.
    @Test fun `fifty thousand entries`() {
        val big = (1L..50_000L).map { TransactionEntity(id = it, type = TxType.EXPENSE, amount = rupees(100 + it % 900), accountId = 1, categoryId = 3, merchant = "Shop $it", occurredAt = at(today.minusDays(it % 300))) }
        val start = System.nanoTime()
        propose(l = ledger(*big.toTypedArray()))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1_500)
    }

    // Attack: a hostile screenshot can claim a huge payee or note; what's kept is bounded.
    @Test fun `long names and notes are cut`() {
        val p = propose(shot(merchant = "x".repeat(500), ref = "UPI ref " + "9".repeat(500)))!!
        assertEquals(40, p.tx.merchant.length)
        assertEquals(60, p.tx.note.length)
    }
}
