package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "You and Zepto" and "Similar spends" on an entry's page. */
class MerchantStatsTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 15)
    private fun at(d: LocalDate) = d.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = 0, includeInLeft = true)
    private val groceries = CategoryEntity(id = 1, name = "Groceries", icon = "groceries", color = "mint", kind = CategoryKind.EXPENSE)
    private val fun_ = CategoryEntity(id = 2, name = "Fun", icon = "fun", color = "rose", kind = CategoryKind.EXPENSE)
    private fun spend(id: Long, merchant: String, rupees: Long, daysAgo: Long, cat: Long = 1) =
        TransactionEntity(id = id, type = TxType.EXPENSE, amount = rupees(rupees), accountId = 1, categoryId = cat, merchant = merchant, occurredAt = at(today.minusDays(daysAgo)))
    private val txs = listOf(
        spend(1, "Zepto", 612, 1),
        spend(2, "zepto ", 384, 7),
        spend(3, "ZEPTO.", 520, 13),
        spend(4, "Blinkit", 455, 16),
        spend(5, "Zepto", 300, 400),
        spend(6, "PVR", 600, 3, cat = 2),
        spend(7, "Big Basket", 2_000, 4),
    )
    private val ledger = Ledger(listOf(bank), listOf(groceries, fun_), txs, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)

    @Test fun `this year's visits, total and usual amount, matching the name loosely`() {
        val s = MerchantStats.of(txs[0], ledger)!!
        assertEquals("Zepto", s.name)
        assertEquals(3, s.count)
        assertEquals(rupees(1_516), s.total)
        assertEquals(rupees(520), s.usual)
    }

    @Test fun `a one-off payee has no stats`() {
        assertNull(MerchantStats.of(txs[3], ledger))
        assertNull(MerchantStats.of(txs[0].copy(merchant = ""), ledger))
    }

    @Test fun `similar spends are the same payee first, then the same category at a similar price`() {
        assertEquals(listOf(2L, 3L, 4L), MerchantStats.similar(txs[0], ledger).map { it.id })
    }

    @Test fun `similar spends never include the entry itself, and stop at three`() {
        val many = txs + (10L..20L).map { spend(it, "Zepto", 500, it) }
        val l = Ledger(listOf(bank), listOf(groceries, fun_), many, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)
        val similar = MerchantStats.similar(many[0], l)
        assertEquals(3, similar.size)
        assert(similar.none { it.id == 1L })
    }

    @Test fun `what a screenshot gave, as label and value`() {
        val scanned = txs[0].copy(source = TxSource.SCAN, note = "UPI ref 412345678901")
        assertEquals(
            listOf("Paid to" to "Zepto", "Amount" to "₹612", "When" to "14 Nov, 6:00 pm", "UPI ref" to "412345678901"),
            MerchantStats.readFields(scanned, zone),
        )
        assertEquals(emptyList<Pair<String, String>>(), MerchantStats.readFields(txs[0], zone))
    }

    // Attack: a punctuation-only name must not match every other punctuation-only name.
    @Test fun `a payee with no letters has no stats or matches`() {
        val dots = txs.map { it.copy(merchant = "...") }
        val l = Ledger(listOf(bank), listOf(groceries, fun_), dots, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)
        assertNull(MerchantStats.of(dots[0], l))
        assert(MerchantStats.similar(dots[0], l).none { it.categoryId != dots[0].categoryId })
    }

    // Attack: years of entries still open the page quickly.
    @Test fun `fifty thousand entries`() {
        val big = (1L..50_000L).map { spend(it, if (it % 3 == 0L) "Zepto" else "Shop $it", 100 + it % 900, it % 300) }
        val l = Ledger(listOf(bank), listOf(groceries, fun_), big, emptyList(), emptyList(), PayCycle.forPayday(1, today), today)
        val start = System.nanoTime()
        MerchantStats.of(big[2], l); MerchantStats.similar(big[2], l)
        assert((System.nanoTime() - start) / 1_000_000 < 1_500)
    }
}
