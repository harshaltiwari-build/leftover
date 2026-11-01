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
import org.junit.Test

/**
 * When a shared payment screenshot saves itself. Only a clearly read, successful payment you made today or yesterday,
 * that isn't already in the books, saves without asking. Money received always asks.
 */
class ScreenshotAutoSaveTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 15)
    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(20_000), includeInLeft = true)
    private val savings = AccountEntity(id = 2, name = "Goals", type = AccountType.SAVINGS, openingBalance = 0, includeInLeft = false)
    private val coffee = CategoryEntity(id = 3, name = "Coffee", icon = "coffee", color = "sand", kind = CategoryKind.EXPENSE)
    private val food = CategoryEntity(id = 4, name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)
    private val other = CategoryEntity(id = 9, name = "Other", icon = "other", color = "grey", kind = CategoryKind.EXPENSE)
    private fun at(d: LocalDate, h: Int = 12) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ledger(vararg txs: TransactionEntity) = Ledger(listOf(savings, bank), listOf(coffee, food, other), txs.toList(), emptyList(), emptyList(), PayCycle.forPayday(1, today), today)

    private fun shot(
        amount: Long? = rupees(250), merchant: String = "Blue Tokai", date: LocalDate? = today, status: ScreenshotStatus = ScreenshotStatus.SUCCESS,
        read: Boolean = true, income: Boolean = false, ref: String = "UPI ref 412345678901",
    ) = ScreenshotAutoSave.Shot(read, income, status, amount, merchant, date, LocalTime.of(10, 30), ref, receipt = "receipt-1.jpg")

    private fun decide(s: ScreenshotAutoSave.Shot = shot(), l: Ledger = ledger(), enabled: Boolean = true, locked: Boolean = false, now: Long = at(today, 18)) =
        ScreenshotAutoSave.decide(s, l, enabled, locked, zone, now)

    private fun reason(d: ScreenshotAutoSave.Decision) = (d as ScreenshotAutoSave.Decision.Ask).reason
    private fun saved(d: ScreenshotAutoSave.Decision) = (d as ScreenshotAutoSave.Decision.Save).tx

    @Test fun `a clear successful payment saves itself`() {
        val tx = saved(decide())
        assertEquals(TxType.EXPENSE, tx.type)
        assertEquals(rupees(250), tx.amount)
        assertEquals("Blue Tokai", tx.merchant)
        assertEquals(1L, tx.accountId)
        assertEquals(TxSource.SCAN, tx.source)
        assertEquals("receipt-1.jpg", tx.receiptFile)
        assertEquals("UPI ref 412345678901", tx.note)
        assertEquals(today.atTime(10, 30).atZone(zone).toInstant().toEpochMilli(), tx.occurredAt)
    }

    @Test fun `the category is the one you last used for that payee, else a guess from the name, else Other`() {
        val before = TransactionEntity(id = 5, type = TxType.EXPENSE, amount = rupees(90), accountId = 1, categoryId = 4, merchant = "Blue Tokai", occurredAt = at(today.minusDays(5)))
        assertEquals(4L, saved(decide(l = ledger(before))).categoryId)
        assertEquals(3L, saved(decide(shot(merchant = "Third Wave Coffee"))).categoryId)
        assertEquals(9L, saved(decide(shot(merchant = "Ramesh Kumar"))).categoryId)
    }

    @Test fun `yesterday's payment saves too`() {
        saved(decide(shot(date = today.minusDays(1))))
    }

    @Test fun `anything unclear asks first`() {
        assertEquals("Automatic saving is off", reason(decide(enabled = false)))
        assertEquals("Leftover is locked", reason(decide(locked = true)))
        assertEquals("Couldn't read it clearly", reason(decide(shot(read = false))))
        assertEquals("Money you received: check it first", reason(decide(shot(income = true))))
        assertEquals("Not a successful payment", reason(decide(shot(status = ScreenshotStatus.PENDING))))
        assertEquals("Not a successful payment", reason(decide(shot(status = ScreenshotStatus.FAILED))))
        assertEquals("Not a successful payment", reason(decide(shot(status = ScreenshotStatus.UNKNOWN))))
        assertEquals("No amount found", reason(decide(shot(amount = null))))
        assertEquals("No payee found", reason(decide(shot(merchant = " "))))
        assertEquals("No date found", reason(decide(shot(date = null))))
        assertEquals("Older than yesterday", reason(decide(shot(date = today.minusDays(2)))))
        assertEquals("Older than yesterday", reason(decide(shot(date = today.plusDays(1)))))
    }

    @Test fun `a payment already in the books asks instead of saving twice`() {
        val sameAmount = TransactionEntity(id = 6, type = TxType.EXPENSE, amount = rupees(250), accountId = 1, categoryId = 3, merchant = "Coffee", occurredAt = at(today.minusDays(1)))
        assertEquals("Looks like one you already have", reason(decide(l = ledger(sameAmount))))
        val sameRef = TransactionEntity(id = 7, type = TxType.EXPENSE, amount = rupees(999), accountId = 1, categoryId = 3, note = "UPI ref 412345678901", occurredAt = at(today.minusDays(20)))
        assertEquals("Looks like one you already have", reason(decide(l = ledger(sameRef))))
        // A different amount two days away is not a match.
        val unrelated = sameAmount.copy(amount = rupees(260), occurredAt = at(today.minusDays(3)))
        saved(decide(l = ledger(unrelated)))
    }

    @Test fun `with nowhere to put it, it asks`() {
        val l = Ledger(listOf(savings), listOf(coffee, other), emptyList(), emptyList(), emptyList(), PayCycle.forPayday(1, today), today)
        assertEquals("No spending account to save it to", reason(decide(l = l)))
    }

    // Attack: any app can hand Leftover an image. Big amounts and floods of screenshots always ask.
    @Test fun `large amounts ask first`() {
        saved(decide(shot(amount = rupees(50_000))))
        assertEquals("Large amount: check it first", reason(decide(shot(amount = rupees(50_001)))))
    }

    @Test fun `after ten saved screenshots in a day, the rest ask`() {
        val ten = (1..10).map { i ->
            TransactionEntity(id = 100L + i, type = TxType.EXPENSE, amount = rupees(10L + i), accountId = 1, categoryId = 3, source = TxSource.SCAN, occurredAt = at(today.minusDays(3)), createdAt = at(today, 9))
        }
        assertEquals("Lots of screenshots today: check this one", reason(decide(l = ledger(*ten.toTypedArray()), now = at(today, 18))))
        saved(decide(l = ledger(*ten.take(9).toTypedArray()), now = at(today, 18)))
    }
}
