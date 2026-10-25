package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TransactionTagEntity
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class TagTotalsTest {
    private fun tx(id: Long, type: TxType, rupees: Long, split: Long = 0) =
        TransactionEntity(id = id, type = type, amount = rupees(rupees), accountId = 1, splitOthers = rupees(split), occurredAt = 0)

    private val txs = listOf(tx(1, TxType.EXPENSE, 1000), tx(2, TxType.EXPENSE, 2000, split = 1500), tx(3, TxType.REFUND, 200), tx(4, TxType.EXPENSE, 700), tx(5, TxType.TRANSFER, 5000))
    private val links = listOf(TransactionTagEntity(1, 10), TransactionTagEntity(2, 10), TransactionTagEntity(3, 10), TransactionTagEntity(5, 10), TransactionTagEntity(4, 11))

    @Test fun aTagAddsYourShareAndSubtractsRefunds() {
        // 1000 + (2000 − 1500) − 200; the transfer isn't spending
        assertEquals(rupees(1300), TagTotals.spent(10, txs, links))
        assertEquals(rupees(700), TagTotals.spent(11, txs, links))
    }

    @Test fun anUnusedTagIsZero() {
        assertEquals(0L, TagTotals.spent(99, txs, links))
    }

    @Test fun tagsOfOneTransaction() {
        assertEquals(setOf(10L), TagTotals.tagsOf(1, links))
        assertEquals(emptySet<Long>(), TagTotals.tagsOf(9, links))
    }
}
