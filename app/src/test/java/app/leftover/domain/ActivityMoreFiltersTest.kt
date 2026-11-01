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
import org.junit.Test

/** Activity's "More filters": category, account, cycle and amount, on top of search and the type chips. */
class ActivityMoreFiltersTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 15)
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = 0, includeInLeft = true)
    private val cash = AccountEntity(id = 2, name = "Cash", type = AccountType.CASH, openingBalance = 0, includeInLeft = true)
    private val food = CategoryEntity(id = 1, name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)
    private val coffee = CategoryEntity(id = 2, name = "Coffee", icon = "coffee", color = "sand", kind = CategoryKind.EXPENSE)
    private val txs = listOf(
        TransactionEntity(id = 1, type = TxType.EXPENSE, amount = rupees(450), accountId = 1, categoryId = 1, merchant = "Swiggy", occurredAt = at(today)),
        TransactionEntity(id = 2, type = TxType.EXPENSE, amount = rupees(160), accountId = 2, categoryId = 2, merchant = "Blue Tokai", occurredAt = at(today.minusDays(2))),
        TransactionEntity(id = 3, type = TxType.EXPENSE, amount = rupees(1_200), accountId = 1, categoryId = 1, merchant = "Zomato", occurredAt = at(today.minusDays(20))),
        TransactionEntity(id = 4, type = TxType.INCOME, amount = rupees(45_000), accountId = 1, categoryId = null, merchant = "Salary", occurredAt = at(today.minusDays(14))),
    )
    private val ledger = Ledger(listOf(bank, cash), listOf(food, coffee), txs, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)
    private fun ids(more: MoreFilters, group: ActivityGroup = ActivityGroup.ALL, query: String = "") =
        ActivityFilter.apply(txs, group, query, ledger, more).map { it.id }

    @Test fun `no extra filters keeps everything`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(MoreFilters()))
        assertEquals(0, MoreFilters().count)
    }

    @Test fun `by category`() = assertEquals(listOf(1L, 3L), ids(MoreFilters(categoryIds = setOf(1))))

    @Test fun `by account`() = assertEquals(listOf(2L), ids(MoreFilters(accountId = 2)))

    @Test fun `by cycle`() {
        assertEquals(listOf(1L, 2L, 4L), ids(MoreFilters(period = FilterPeriod.THIS_CYCLE)))
        assertEquals(listOf(3L), ids(MoreFilters(period = FilterPeriod.LAST_CYCLE)))
    }

    @Test fun `by amount, inclusive`() {
        assertEquals(listOf(1L, 3L), ids(MoreFilters(minRupees = 450, maxRupees = 1_200)))
        assertEquals(listOf(2L), ids(MoreFilters(maxRupees = 200)))
    }

    @Test fun `filters combine with search and the type chips`() {
        val more = MoreFilters(categoryIds = setOf(1), period = FilterPeriod.THIS_CYCLE, minRupees = 100)
        assertEquals(3, more.count)
        assertEquals(listOf(1L), ids(more, ActivityGroup.EXPENSES, "swig"))
        assertEquals(emptyList<Long>(), ids(more, ActivityGroup.INCOME))
    }

    @Test fun `a minimum above the maximum matches nothing rather than crashing`() =
        assertEquals(emptyList<Long>(), ids(MoreFilters(minRupees = 500, maxRupees = 100)))

    // Attack: a filtered category or account that's since been removed must not leave an empty list you can't clear.
    @Test fun `filters for things that no longer exist are dropped`() {
        val stale = MoreFilters(categoryIds = setOf(1, 99), accountId = 42)
        val pruned = stale.prunedTo(ledger)
        assertEquals(setOf(1L), pruned.categoryIds)
        assertEquals(null, pruned.accountId)
        assertEquals(1, pruned.count)
    }
}
