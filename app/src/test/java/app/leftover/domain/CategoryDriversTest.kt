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
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDriversTest {
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val food = CategoryEntity(id = 1, name = "Food delivery", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)
    private val travel = CategoryEntity(id = 2, name = "Travel", icon = "bus", color = "sky", kind = CategoryKind.EXPENSE)
    private val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(50_000), includeInLeft = true)
    private fun tx(d: LocalDate, rupees: Long, cat: Long) = TransactionEntity(type = TxType.EXPENSE, amount = rupees(rupees), accountId = 1, categoryId = cat, occurredAt = at(d))
    private val today = LocalDate.of(2026, 10, 20)

    private fun ledger(txs: List<TransactionEntity>) = Ledger(listOf(bank), listOf(food, travel), txs, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)

    @Test fun `only one category up is named`() {
        val l = ledger(listOf(tx(LocalDate.of(2026, 10, 5), 1180, 1), tx(LocalDate.of(2026, 9, 5), 1000, 1), tx(LocalDate.of(2026, 9, 6), 2000, 2), tx(LocalDate.of(2026, 10, 6), 2000, 2)))
        assertEquals("Food delivery is the only category up: 18% more than at this point last cycle.", CategoryDrivers.headline(CategoryDrivers.changes(l)))
    }
    @Test fun `small changes are ignored`() {
        val l = ledger(listOf(tx(LocalDate.of(2026, 10, 5), 1050, 1), tx(LocalDate.of(2026, 9, 5), 1000, 1)))
        assertNull(CategoryDrivers.headline(CategoryDrivers.changes(l)))
    }
    @Test fun `spending after the same day last cycle is not compared`() {
        val l = ledger(listOf(tx(LocalDate.of(2026, 10, 5), 1000, 1), tx(LocalDate.of(2026, 9, 28), 5000, 1)))
        val change = CategoryDrivers.changes(l).single { it.category.id == 1L }
        assertEquals(rupees(1000), change.now); assertEquals(0L, change.before)
    }
    @Test fun `a category that dropped is mentioned when nothing rose`() {
        val l = ledger(listOf(tx(LocalDate.of(2026, 10, 5), 500, 1), tx(LocalDate.of(2026, 9, 5), 1500, 1)))
        assertTrue(CategoryDrivers.headline(CategoryDrivers.changes(l))!!.contains("down 67%"))
    }
    @Test fun `explain hides amounts when asked`() {
        val change = CategoryChange(food, rupees(1500), rupees(1000))
        assertTrue(CategoryDrivers.explain(change, false).contains("₹500 more"))
        assertEquals("Up on this point last cycle.", CategoryDrivers.explain(change, true))
    }
}
