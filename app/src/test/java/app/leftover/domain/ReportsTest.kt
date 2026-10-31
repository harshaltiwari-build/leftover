package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxFeeling
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportsTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val today = LocalDate.of(2026, 10, 25)
    private val food = CategoryEntity(id = 1, name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)
    private val shop = CategoryEntity(id = 2, name = "Shopping", icon = "shopping", color = "lavender", kind = CategoryKind.EXPENSE)
    private val salary = CategoryEntity(id = 3, name = "Salary", icon = "income", color = "lime", kind = CategoryKind.INCOME)
    private val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = 0, includeInLeft = true)

    private fun tx(date: LocalDate, rupees: Long, type: TxType = TxType.EXPENSE, category: Long = 1, merchant: String = "", feeling: TxFeeling? = null, splitOthers: Long = 0) =
        TransactionEntity(type = type, amount = rupees(rupees), accountId = 1, categoryId = category, merchant = merchant, occurredAt = at(date), feeling = feeling, splitOthers = rupees(splitOthers))

    private fun ledger(txs: List<TransactionEntity>) = Ledger(listOf(bank), listOf(food, shop, salary), txs, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)

    @Test fun `presets cover the right days`() {
        val cycle = PayCycle.forPayday(28, today)
        assertEquals(DateSpan(today.minusDays(6), today), RangePreset.LAST_7.span(today, cycle))
        assertEquals(DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), RangePreset.LAST_MONTH.span(today, cycle))
        assertEquals(DateSpan(LocalDate.of(2026, 8, 1), today), RangePreset.LAST_3_MONTHS.span(today, cycle))
        assertEquals(DateSpan(LocalDate.of(2026, 1, 1), today), RangePreset.THIS_YEAR.span(today, cycle))
        assertEquals(DateSpan(LocalDate.of(2026, 9, 28), today), RangePreset.THIS_CYCLE.span(today, cycle))
        assertEquals(DateSpan(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 27)), RangePreset.LAST_CYCLE.span(today, cycle))
    }

    @Test fun `report totals use your own share, refunds and the period before`() {
        val span = DateSpan(today.minusDays(6), today)
        val l = ledger(listOf(
            tx(today, 500, merchant = "Swiggy", feeling = TxFeeling.REGRET),
            tx(today.minusDays(1), 1200, merchant = "Swiggy", splitOthers = 600, feeling = TxFeeling.WORTH_IT),
            tx(today.minusDays(2), 300, type = TxType.REFUND, category = 2),
            tx(today.minusDays(2), 2000, category = 2, merchant = "Myntra"),
            tx(today.minusDays(3), 40_000, type = TxType.INCOME, category = 3),
            tx(today.minusDays(9), 999), // the week before
        ))
        val r = RangeReport.build(l, span)
        assertEquals(rupees(500 + 600 + 2000 - 300), r.spent)
        assertEquals(rupees(40_000), r.income)
        assertEquals(rupees(999), r.previousSpent)
        assertEquals(listOf(2L, 1L), r.categories.map { it.category.id })
        assertEquals(rupees(500), r.regret)
        assertEquals(rupees(500 + 600), r.marked)
        assertEquals(listOf("Myntra", "Swiggy"), r.merchants.map { it.name })
        assertEquals(2, r.merchants[1].count)
        assertEquals(rupees(1100), r.merchants[1].spent)
        assertEquals(7, r.buckets.size)
        assertEquals(r.spent, r.buckets.sumOf { it.spent })
        assertEquals(93, r.savingsRate)
    }

    @Test fun `no income means no savings rate`() {
        val r = RangeReport.build(ledger(listOf(tx(today, 100))), DateSpan(today, today))
        assertNull(r.savingsRate)
        assertEquals(rupees(100), r.perDay)
    }

    @Test fun `bucket size follows the span length`() {
        assertEquals(14, RangeReport.buckets(DateSpan(today.minusDays(13), today)).size)
        // 1 Jul – 25 Oct is longer than three months, so it goes by month.
        assertEquals(listOf("Jul", "Aug", "Sep", "Oct"), RangeReport.buckets(DateSpan(LocalDate.of(2026, 7, 1), today)).map { it.first })
        val weeks = RangeReport.buckets(DateSpan(today.minusDays(29), today))
        assertEquals(today.minusDays(29), weeks.first().second.start)
        assertEquals(today, weeks.last().second.end)
        assertEquals(30, weeks.sumOf { it.second.days })
    }

    @Test fun `csv for a span only has that span`() {
        val l = ledger(listOf(tx(today, 100, merchant = "In"), tx(today.minusDays(40), 100, merchant = "Out")))
        val csv = ReportCsv.build(l, DateSpan(today.minusDays(6), today))
        assertEquals(2, csv.trim().lines().size)
        assert(csv.contains("In") && !csv.contains("Out"))
    }
}
