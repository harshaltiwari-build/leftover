package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType

enum class ActivityGroup(val label: String) {
    ALL("All"),
    EXPENSES("Expenses"),
    INCOME("Income"),
    MONEY_BACK("Money back"),
    TRANSFERS("Transfers");

    fun matches(type: TxType): Boolean = when (this) {
        ALL -> true
        EXPENSES -> type == TxType.EXPENSE
        INCOME -> type == TxType.INCOME
        MONEY_BACK -> type == TxType.REFUND || type == TxType.REPAYMENT
        TRANSFERS -> type == TxType.TRANSFER || type == TxType.SETTLE_OUT || type == TxType.GOAL_SAVE || type == TxType.GOAL_WITHDRAW
    }
}

/** Search and filter used by the Activity list. A query matches merchant, note, category name or a rupee amount. */
object ActivityFilter {
    fun apply(transactions: List<TransactionEntity>, group: ActivityGroup, query: String, ledger: Ledger): List<TransactionEntity> {
        val needle = query.trim().lowercase()
        val digits = needle.replace(",", "").toLongOrNull()
        return transactions.filter { tx ->
            group.matches(tx.type) && (needle.isEmpty() ||
                tx.merchant.lowercase().contains(needle) ||
                tx.note.lowercase().contains(needle) ||
                (ledger.category(tx.categoryId)?.name?.lowercase()?.contains(needle) == true) ||
                (digits != null && tx.amount == digits * 100))
        }
    }

    /** Sum of the user's own spending on a day: expenses minus refunds, ignoring transfers and excluded entries. */
    fun daySpend(transactions: List<TransactionEntity>): Long = transactions.filter { !it.excludeFromBudgets }.sumOf {
        when (it.type) {
            TxType.EXPENSE -> it.amount - it.splitOthers
            TxType.REFUND -> -it.amount
            else -> 0L
        }
    }
}
