package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InsightsTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val cycle = PayCycle.forPayday(28, today)
    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val bank = AccountEntity(id = 1, name = "HDFC, main", type = AccountType.BANK, openingBalance = rupees(50000), includeInLeft = true)
    private val food = CategoryEntity(id = 1, name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)
    private val shop = CategoryEntity(id = 2, name = "Shopping", icon = "shopping", color = "lavender", kind = CategoryKind.EXPENSE)

    private fun spend(amount: Long, date: LocalDate, category: Long = 1) =
        TransactionEntity(type = TxType.EXPENSE, amount = rupees(amount), accountId = 1, categoryId = category, occurredAt = millis(date), merchant = "Shop \"A\"")

    private fun ledger(vararg tx: TransactionEntity) = Ledger(listOf(bank), listOf(food, shop), tx.toList(), emptyList(), emptyList(), cycle, today)

    @Test fun comparesTheSameDayOfThePreviousCycle() {
        // Current cycle is 28 Sep – 27 Oct; the previous one is 28 Aug – 27 Sep.
        val l = ledger(
            spend(1000, LocalDate.of(2026, 10, 20)),
            spend(9000, LocalDate.of(2026, 9, 10)),
            spend(50000, LocalDate.of(2026, 9, 26)),
        )
        val i = Insights.build(l)
        assertEquals(rupees(1000), i.comparison.spentNow)
        assertEquals(28, i.comparison.dayOfCycle)
        assertTrue(i.comparison.hasHistory)
        // 26 Sep is day 30 of the previous cycle, after day 28, so it must not count against today's pace.
        assertEquals(rupees(9000), i.comparison.spentBefore)
    }

    @Test fun sortsCategoriesBySpend() {
        val l = ledger(spend(500, LocalDate.of(2026, 10, 20), 1), spend(2500, LocalDate.of(2026, 10, 21), 2))
        val i = Insights.build(l)
        assertEquals(listOf("Shopping", "Eating out"), i.categories.map { it.category.name })
        assertEquals("Shopping and Eating out are 100% of your spending.", InsightText.topCategories(i.categories, i.spent))
    }

    @Test fun trendHasSixCyclesEndingWithTheCurrentOne() {
        val i = Insights.build(ledger())
        assertEquals(6, i.trend.size)
        assertTrue(i.trend.last().isCurrent)
        assertEquals(1, i.trend.count { it.isCurrent })
    }

    @Test fun keptPercentNeedsIncome() {
        assertNull(Insights.build(ledger(spend(100, today))).keptPercent)
        val income = TransactionEntity(type = TxType.INCOME, amount = rupees(1000), accountId = 1, occurredAt = millis(today))
        val i = Insights.build(ledger(income, spend(250, today)))
        assertEquals(75, i.keptPercent)
    }

    @Test fun hiddenModeNeverPrintsAmounts() {
        val c = SameDayComparison(27, rupees(100), rupees(500))
        assertEquals("You're spending less than at this point last cycle.", InsightText.comparison(c, hidden = true))
        assertEquals("By day 27 you've spent ₹400 less than at this point last cycle.", InsightText.comparison(c, hidden = false))
    }

    @Test fun csvQuotesCommasAndQuotes() {
        val csv = CsvExport.build(ledger(spend(180, LocalDate.of(2026, 10, 20))))
        val lines = csv.trim().lines()
        assertEquals("date,type,amount_inr,account,to_account,category,merchant,note", lines[0])
        assertEquals("2026-10-20,expense,180.00,\"HDFC, main\",,Eating out,\"Shop \"\"A\"\"\",", lines[1])
    }
}
