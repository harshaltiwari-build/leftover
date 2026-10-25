package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TransactionTagEntity
import app.leftover.data.db.TxType

/** What a tag (a trip, a wedding) has cost: your own share of every tagged expense, less refunds. */
object TagTotals {
    fun transactionsOf(tagId: Long, transactions: List<TransactionEntity>, links: List<TransactionTagEntity>): List<TransactionEntity> {
        val ids = links.filter { it.tagId == tagId }.map { it.transactionId }.toSet()
        return transactions.filter { it.id in ids }
    }

    fun spent(tagId: Long, transactions: List<TransactionEntity>, links: List<TransactionTagEntity>): Paise =
        transactionsOf(tagId, transactions, links).sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amount - it.splitOthers
                TxType.REFUND -> -it.amount
                else -> 0L
            }
        }.coerceAtLeast(0)

    /** Tag ids on one transaction. */
    fun tagsOf(transactionId: Long, links: List<TransactionTagEntity>): Set<Long> = links.filter { it.transactionId == transactionId }.map { it.tagId }.toSet()
}
