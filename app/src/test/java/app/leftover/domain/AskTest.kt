package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxFeeling
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val today = LocalDate.of(2026, 10, 25)
    private val eating = CategoryEntity(id = 1, name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)
    private val groceries = CategoryEntity(id = 2, name = "Groceries", icon = "groceries", color = "mint", kind = CategoryKind.EXPENSE)
    private val transport = CategoryEntity(id = 3, name = "Transport", icon = "transport", color = "sky", kind = CategoryKind.EXPENSE)
    private val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(10_000), includeInLeft = true)

    private fun spend(date: LocalDate, rupees: Long, category: Long, merchant: String = "", feeling: TxFeeling? = null) =
        TransactionEntity(type = TxType.EXPENSE, amount = rupees(rupees), accountId = 1, categoryId = category, merchant = merchant, occurredAt = at(date), feeling = feeling)

    private val txs = listOf(
        spend(today, 420, 1, "Swiggy", TxFeeling.REGRET),
        spend(today.minusDays(3), 1120, 2, "BigBasket", TxFeeling.NEEDED),
        spend(today.minusDays(4), 236, 3, "Uber"),
        spend(today.minusDays(40), 999, 1, "Swiggy"),
    )
    private fun ledger(goals: List<GoalEntity> = emptyList(), bills: List<BillEntity> = emptyList()) =
        Ledger(listOf(bank), listOf(eating, groceries, transport), txs, goals, bills, PayCycle.forPayday(1, today), today)

    @Test fun `food covers eating out and groceries this month`() {
        val a = Ask.answer("How much did I spend on food this month?", ledger(), hidden = false)
        assertEquals(Money.format(rupees(420 + 1120)), a.headline)
        assertEquals(2, a.entries.size)
        assertEquals(2, a.lines.size)
    }

    @Test fun `a merchant name wins over categories`() {
        val a = Ask.answer("how much at swiggy this year", ledger(), hidden = false)
        assertEquals(Money.format(rupees(420 + 999)), a.headline)
        assertTrue(a.entries.all { it.merchant == "Swiggy" })
    }

    @Test fun `periods are read from the question`() {
        val cycle = PayCycle.forPayday(1, today)
        assertEquals(DateSpan(today.minusDays(1), today.minusDays(1)), Ask.period("what did i spend yesterday", today, cycle).span)
        assertEquals(DateSpan(today.minusDays(13), today), Ask.period("last 14 days", today, cycle).span)
        assertEquals(DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), Ask.period("spent last month", today, cycle).span)
        // Not "kal" inside another word.
        assertEquals("this cycle", Ask.period("kalyan shop", today, cycle).label)
    }

    @Test fun `regrets add up only regretted spends`() {
        val a = Ask.answer("What did I regret this month?", ledger(), hidden = false)
        assertEquals(Money.format(rupees(420)), a.headline)
        assertEquals(1, a.entries.size)
    }

    @Test fun `bills due this week`() {
        val bill = BillEntity(id = 1, name = "Spotify", amount = rupees(119), dueDay = 27, icon = "fun", isSubscription = true)
        val later = BillEntity(id = 2, name = "Rent", amount = rupees(9000), dueDay = 15, icon = "home")
        val a = Ask.answer("Which bills are due this week?", ledger(bills = listOf(bill, later)), hidden = false)
        assertEquals(listOf("Spotify · Tue 27 Oct"), a.lines.map { it.first })
    }

    @Test fun `goals by name`() {
        val goa = GoalEntity(id = 1, name = "Goa trip", target = rupees(10_000), perCycle = rupees(2_000), cover = "beach")
        val a = Ask.answer("Am I on track for Goa?", ledger(goals = listOf(goa)), hidden = false)
        assertTrue(a.summary.startsWith("Goa trip"))
    }

    @Test fun `hidden answers keep amounts out`() {
        val a = Ask.answer("How much did I spend on food this month?", ledger(), hidden = true)
        assertFalse(a.headline.any { it.isDigit() })
        assertFalse(a.summary.contains("₹"))
    }

    @Test fun `unknown questions say so`() {
        assertFalse(Ask.answer("what is the meaning of life", ledger(), hidden = false).understood)
    }
}
