package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeTodayTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val today = LocalDate.of(2026, 11, 15)
    private val food = CategoryEntity(id = 1, name = "Food", icon = "food", color = "peach", kind = CategoryKind.EXPENSE, budgetLimit = rupees(4000))
    private fun bank(opening: Long) = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(opening), includeInLeft = true)
    private fun spend(date: LocalDate, rupees: Long) = TransactionEntity(type = TxType.EXPENSE, amount = rupees(rupees), accountId = 1, categoryId = 1, occurredAt = at(date))
    private fun ledger(opening: Long, txs: List<TransactionEntity>, day: LocalDate = today) =
        Ledger(listOf(bank(opening)), listOf(food), txs, emptyList(), emptyList(), PayCycle.forPayday(1, day), day)

    @Test fun `target is this morning's left shared over the days to payday`() {
        // 15 Nov with payday on the 1st: 16 days including today. ₹9,840 this morning, ₹230 spent today.
        val s = SafeToday.of(ledger(9_840, listOf(spend(today, 230))))
        assertEquals(16, s.days)
        assertEquals(rupees(615), s.target)
        assertEquals(rupees(230), s.spentToday)
        assertEquals(rupees(385), s.leftToday)
        assertEquals(rupees(9_610), s.leftNow)
        assertEquals(rupees(640), s.tomorrow)
        assertFalse(s.over)
    }

    @Test fun `going over spreads the difference across the days left`() {
        val s = SafeToday.of(ledger(9_840, listOf(spend(today, 1_000))))
        assertTrue(s.over)
        assertEquals(rupees(-385), s.leftToday)
        assertEquals(rupees(589), s.tomorrow)
        assertEquals(1f, s.fraction)
    }

    @Test fun `the last day before payday has no tomorrow`() {
        val s = SafeToday.of(ledger(500, emptyList(), day = LocalDate.of(2026, 11, 30)))
        assertEquals(1, s.days)
        assertEquals(rupees(500), s.target)
        assertNull(s.tomorrow)
    }

    @Test fun `an empty account gives a zero target`() {
        assertEquals(0, SafeToday.of(ledger(0, listOf(spend(today, 50)))).target)
    }

    @Test fun `pace warning names the category heading over`() {
        // ₹3,020 of ₹4,000 by day 15 of 30 projects to ₹6,040, about ₹2,000 over.
        val w = PaceWarning.worst(ledger(20_000, listOf(spend(today.minusDays(3), 3_020))))!!
        assertEquals("Food", w.category.name)
        assertEquals(rupees(2_000), w.projectedOver)
        assertEquals(rupees(61), w.perDayToHold)
        assertTrue(w.text(hidden = false).startsWith("At this pace Food ends about ₹2,000 over"))
        assertFalse(w.text(hidden = true).contains("₹"))
    }

    @Test fun `no warning when on pace or too early in the cycle`() {
        assertNull(PaceWarning.worst(ledger(20_000, listOf(spend(today.minusDays(3), 1_000)))))
        assertNull(PaceWarning.worst(ledger(20_000, listOf(spend(LocalDate.of(2026, 11, 1), 3_000)), day = LocalDate.of(2026, 11, 2))))
    }
}
