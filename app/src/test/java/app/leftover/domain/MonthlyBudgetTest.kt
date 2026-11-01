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
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyBudgetTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val today = LocalDate.of(2026, 11, 15)
    private val food = CategoryEntity(id = 1, name = "Food", icon = "food", color = "peach", kind = CategoryKind.EXPENSE, budgetLimit = rupees(4_000))
    private val shop = CategoryEntity(id = 2, name = "Shopping", icon = "shopping", color = "lavender", kind = CategoryKind.EXPENSE, budgetLimit = rupees(3_000))
    private val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(50_000), includeInLeft = true)
    private fun spend(rupees: Long, category: Long = 1) = TransactionEntity(type = TxType.EXPENSE, amount = rupees(rupees), accountId = 1, categoryId = category, occurredAt = at(today.minusDays(2)))
    private fun ledger(vararg txs: TransactionEntity) = Ledger(listOf(bank), listOf(food, shop), txs.toList(), emptyList(), emptyList(), PayCycle.forPayday(1, today), today)

    @Test fun `the set amount wins over category limits`() {
        val b = MonthlyBudget.of(ledger(spend(6_812)), setAmount = rupees(12_000))!!
        assertEquals(rupees(12_000), b.budget)
        assertEquals(rupees(6_812), b.spent)
        assertEquals(rupees(5_188), b.left)
        assertEquals(true, b.fromSetting)
    }

    @Test fun `without a set amount the category limits add up`() {
        val b = MonthlyBudget.of(ledger(spend(1_000)), setAmount = 0)!!
        assertEquals(rupees(7_000), b.budget)
        assertEquals(false, b.fromSetting)
    }

    @Test fun `no amount and no limits means no budget`() {
        val l = Ledger(listOf(bank), listOf(food.copy(budgetLimit = null)), emptyList(), emptyList(), emptyList(), PayCycle.forPayday(1, today), today)
        assertNull(MonthlyBudget.of(l, setAmount = 0))
    }

    @Test fun `pace compares share used with share of the cycle gone`() {
        // Day 15 of 30: half the cycle. ₹6,812 of ₹12,000 is 57%: a little ahead.
        val b = MonthlyBudget.of(ledger(spend(6_812)), setAmount = rupees(12_000))!!
        assertEquals(0.5f, b.pace, 0.001f)
        assertEquals(BudgetPace.WATCH, b.status)
        // ₹454 a day for 30 days is ₹13,624: about ₹1,600 over.
        assertEquals(rupees(1_600), b.projectedOver)
        // ₹5,188 over the 16 days left is ₹324 a day.
        assertEquals(rupees(324), b.perDayLeft)
    }

    @Test fun `on pace and over`() {
        assertEquals(BudgetPace.ON_PACE, MonthlyBudget.of(ledger(spend(5_000)), rupees(12_000))!!.status)
        assertEquals(BudgetPace.OVER, MonthlyBudget.of(ledger(spend(9_000)), rupees(12_000))!!.status)
        val over = MonthlyBudget.of(ledger(spend(13_000)), rupees(12_000))!!
        assertEquals(BudgetPace.OVER, over.status)
        assertEquals(rupees(-1_000), over.left)
        assertEquals(0L, over.perDayLeft)
    }

    @Test fun `share used is clamped for rings`() {
        assertEquals(1f, MonthlyBudget.of(ledger(spend(13_000)), rupees(12_000))!!.used, 0.001f)
        assertEquals(0f, MonthlyBudget.of(ledger(), rupees(12_000))!!.used, 0.001f)
    }

    @Test fun `a suggestion comes from what the last cycle cost, rounded up to the next 500`() {
        val lastCycle = PayCycle.forPayday(1, today).previous()
        val tx = TransactionEntity(type = TxType.EXPENSE, amount = rupees(11_730), accountId = 1, categoryId = 1, occurredAt = at(lastCycle.start.plusDays(3)))
        assertEquals(rupees(12_000), MonthlyBudget.suggestion(ledger(tx)))
        assertNull(MonthlyBudget.suggestion(ledger()))
    }

    // Attack: the typed amount must be what gets saved. 9 digits used to show "Set ₹99,99,99,999" and quietly save ₹1 crore.
    @Test fun `typed amounts are read as whole rupees and refused above the limit`() {
        assertEquals(rupees(12_000), MonthlyBudget.parseInput("12000"))
        assertEquals(rupees(12_000), MonthlyBudget.parseInput("₹12,000"))
        assertNull(MonthlyBudget.parseInput(""))
        assertNull(MonthlyBudget.parseInput("000"))
        assertEquals(MonthlyBudget.MAX, MonthlyBudget.parseInput("10000000"))
        assertNull(MonthlyBudget.parseInput("10000001"))
        assertNull(MonthlyBudget.parseInput("99999999999999999999999"))
    }

    // Attack: a very large history still gives sane numbers, without overflow, and quickly.
    @Test fun `a hundred thousand entries at the largest amount`() {
        val big = List(100_000) { TransactionEntity(type = TxType.EXPENSE, amount = rupees(99_99_999), accountId = 1, categoryId = 1, occurredAt = at(today.minusDays((it % 14).toLong()))) }
        val l = Ledger(listOf(bank), listOf(food, shop), big, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)
        val start = System.nanoTime()
        val b = MonthlyBudget.of(l, MonthlyBudget.MAX)!!
        MonthlyBudget.suggestion(l)
        assert((System.nanoTime() - start) / 1_000_000 < 2_000)
        assertEquals(BudgetPace.OVER, b.status)
        assert(b.projected > b.spent) { "projected overflowed: ${b.projected}" }
        assertEquals(1f, b.used, 0.001f)
    }
}
