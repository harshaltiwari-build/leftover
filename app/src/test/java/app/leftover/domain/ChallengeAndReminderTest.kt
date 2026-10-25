package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.ChallengeEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeAndReminderTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun expense(date: LocalDate, rupees: Long, category: Long? = 1) =
        TransactionEntity(type = TxType.EXPENSE, amount = rupees(rupees), accountId = 1, categoryId = category, occurredAt = at(date))

    private val food = CategoryEntity(id = 1, name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE, budgetLimit = rupees(1000))
    private val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(20_000), includeInLeft = true)

    private fun ledger(today: LocalDate, txs: List<TransactionEntity> = emptyList(), bills: List<BillEntity> = emptyList(), payday: Int = 1) =
        Ledger(listOf(bank), listOf(food), txs, emptyList(), bills, PayCycle.forPayday(payday, today), today)

    @Test fun `challenge counts clean days and lists spend days`() {
        val start = LocalDate.of(2026, 10, 1)
        val c = ChallengeEntity(name = "No eating out", categoryId = 1, startEpochDay = start.toEpochDay(), endEpochDay = start.plusDays(6).toEpochDay())
        val p = ChallengeProgress.of(c, listOf(expense(start.plusDays(2), 200), expense(start.plusDays(2), 50), expense(start.plusDays(3), 100, category = 9)), start.plusDays(4))
        assertEquals(7, p.totalDays); assertEquals(5, p.daysSoFar); assertEquals(listOf(start.plusDays(2)), p.spendDays); assertEquals(4, p.cleanDays)
        assertFalse(p.finished)
    }
    @Test fun `challenge for any spending counts every category`() {
        val start = LocalDate.of(2026, 10, 1)
        val c = ChallengeEntity(name = "No spend week", categoryId = null, startEpochDay = start.toEpochDay(), endEpochDay = start.plusDays(6).toEpochDay())
        val p = ChallengeProgress.of(c, listOf(expense(start.plusDays(1), 100, category = 9)), start.plusDays(10))
        assertTrue(p.finished); assertFalse(p.kept); assertEquals(6, p.cleanDays)
    }
    @Test fun `a finished challenge with no spend is kept`() {
        val start = LocalDate.of(2026, 10, 1)
        val c = ChallengeEntity(name = "x", categoryId = 1, startEpochDay = start.toEpochDay(), endEpochDay = start.plusDays(2).toEpochDay())
        assertTrue(ChallengeProgress.of(c, emptyList(), start.plusDays(5)).kept)
    }
    @Test fun `challenge not started yet`() {
        val start = LocalDate.of(2026, 10, 10)
        val c = ChallengeEntity(name = "x", categoryId = null, startEpochDay = start.toEpochDay(), endEpochDay = start.plusDays(2).toEpochDay())
        val p = ChallengeProgress.of(c, emptyList(), LocalDate.of(2026, 10, 5))
        assertTrue(p.notStarted); assertEquals(0, p.daysSoFar)
    }

    @Test fun `bill reminder fires inside its lead time and only once`() {
        val today = LocalDate.of(2026, 10, 14)
        val bill = BillEntity(id = 5, name = "Rent", amount = rupees(9000), dueDay = 15, icon = "home", remindDaysBefore = 1)
        val l = ledger(today, bills = listOf(bill))
        val first = ReminderPlanner.plan(l, ReminderPrefs(bills = true), false, emptySet())
        assertEquals(1, first.size); assertEquals("Rent is due tomorrow", first[0].title)
        assertTrue(ReminderPlanner.plan(l, ReminderPrefs(bills = true), false, setOf(first[0].key)).isEmpty())
        assertTrue(ReminderPlanner.plan(ledger(today.minusDays(3), bills = listOf(bill)), ReminderPrefs(bills = true), false, emptySet()).isEmpty())
    }
    @Test fun `hidden balances keep the amount out of the notice`() {
        val today = LocalDate.of(2026, 10, 15)
        val bill = BillEntity(id = 5, name = "Rent", amount = rupees(9000), dueDay = 15, icon = "home", remindDaysBefore = 0)
        val r = ReminderPlanner.plan(ledger(today, bills = listOf(bill)), ReminderPrefs(bills = true), true, emptySet()).single()
        assertFalse(r.body.contains("9"))
    }
    @Test fun `budget warning at ninety percent`() {
        val today = LocalDate.of(2026, 10, 20)
        val l = ledger(today, listOf(expense(today.minusDays(2), 950)))
        val r = ReminderPlanner.plan(l, ReminderPrefs(budgets = true), false, emptySet()).single()
        assertEquals(ReminderKind.BUDGET, r.kind)
        assertTrue(ReminderPlanner.plan(ledger(today, listOf(expense(today.minusDays(2), 400))), ReminderPrefs(budgets = true), false, emptySet()).isEmpty())
    }
    @Test fun `payday reminder only on payday and weekly only on sunday`() {
        val payday = LocalDate.of(2026, 10, 1)
        assertEquals(1, ReminderPlanner.plan(ledger(payday), ReminderPrefs(payday = true), false, emptySet()).size)
        assertTrue(ReminderPlanner.plan(ledger(payday.plusDays(1)), ReminderPrefs(payday = true), false, emptySet()).isEmpty())
        val sunday = LocalDate.of(2026, 10, 25)
        assertEquals(java.time.DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(1, ReminderPlanner.plan(ledger(sunday), ReminderPrefs(weekly = true), false, emptySet()).size)
        assertTrue(ReminderPlanner.plan(ledger(sunday.plusDays(1)), ReminderPrefs(weekly = true), false, emptySet()).isEmpty())
    }
    @Test fun `old sent keys are pruned`() {
        val today = LocalDate.of(2026, 10, 25)
        val kept = "a|${today.minusDays(3).toEpochDay()}"; val old = "b|${today.minusDays(100).toEpochDay()}"
        assertEquals(setOf(kept), ReminderPlanner.prune(setOf(kept, old), today))
    }
    @Test fun `weekly review compares with the week before`() {
        val today = LocalDate.of(2026, 10, 25)
        val l = ledger(today, listOf(expense(today.minusDays(1), 300), expense(today.minusDays(2), 200), expense(today.minusDays(9), 100)))
        val w = WeeklyReview.of(l)
        assertEquals(rupees(500), w.spent); assertEquals(rupees(100), w.previousSpent); assertEquals(1L, w.topCategoryId); assertEquals(5, w.noSpendDays)
    }
}
