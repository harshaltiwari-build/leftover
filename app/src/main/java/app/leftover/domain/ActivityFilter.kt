package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxFeeling
import app.leftover.data.db.TxType

enum class ActivityGroup(val label: String) {
    ALL("All"),
    EXPENSES("Expenses"),
    INCOME("Income"),
    MONEY_BACK("Money back"),
    TRANSFERS("Transfers"),
    REGRETS("Regrets"),
    RECEIPTS("Has receipt");

    fun matches(tx: TransactionEntity): Boolean {
        val type = tx.type
        return when (this) {
            ALL -> true
            EXPENSES -> type == TxType.EXPENSE
            INCOME -> type == TxType.INCOME
            MONEY_BACK -> type == TxType.REFUND || type == TxType.REPAYMENT
            TRANSFERS -> type == TxType.TRANSFER || type == TxType.SETTLE_OUT || type == TxType.GOAL_SAVE || type == TxType.GOAL_WITHDRAW
            REGRETS -> tx.feeling == TxFeeling.REGRET
            RECEIPTS -> tx.receiptFile != null
        }
    }
}

enum class FilterPeriod(val label: String) { ANY("Any time"), THIS_CYCLE("This cycle"), LAST_CYCLE("Last cycle") }

/** Activity's "More filters". Empty means no narrowing. Amounts are whole rupees and inclusive. */
data class MoreFilters(
    val categoryIds: Set<Long> = emptySet(),
    val accountId: Long? = null,
    val period: FilterPeriod = FilterPeriod.ANY,
    val minRupees: Long? = null,
    val maxRupees: Long? = null,
) {
    /** How many filters are on, for the "Filters · 2" label. A min and max together count once. */
    val count: Int get() = listOf(categoryIds.isNotEmpty(), accountId != null, period != FilterPeriod.ANY, minRupees != null || maxRupees != null).count { it }

    /** Drops categories and accounts that no longer exist, so a stale filter can't hide everything. */
    fun prunedTo(ledger: Ledger): MoreFilters = copy(
        categoryIds = categoryIds.filter { id -> ledger.categories.any { it.id == id } }.toSet(),
        accountId = accountId?.takeIf { id -> ledger.accounts.any { it.id == id } },
    )

    fun matches(tx: TransactionEntity, ledger: Ledger): Boolean {
        if (categoryIds.isNotEmpty() && tx.categoryId !in categoryIds) return false
        if (accountId != null && tx.accountId != accountId && tx.toAccountId != accountId) return false
        val cycle = when (period) {
            FilterPeriod.ANY -> null
            FilterPeriod.THIS_CYCLE -> ledger.cycle
            FilterPeriod.LAST_CYCLE -> ledger.cycle.previous()
        }
        if (cycle != null) {
            val day = tx.occurredAt.toLocalDate()
            if (day.isBefore(cycle.start) || day.isAfter(cycle.end)) return false
        }
        if (minRupees != null && tx.amount < minRupees * 100) return false
        if (maxRupees != null && tx.amount > maxRupees * 100) return false
        return true
    }
}

/** Search and filter used by the Activity list. A query matches merchant, note, category name or a rupee amount. */
object ActivityFilter {
    fun apply(transactions: List<TransactionEntity>, group: ActivityGroup, query: String, ledger: Ledger, more: MoreFilters = MoreFilters()): List<TransactionEntity> {
        val needle = query.trim().lowercase()
        val digits = needle.replace(",", "").toLongOrNull()
        return transactions.filter { tx ->
            group.matches(tx) && more.matches(tx, ledger) && (needle.isEmpty() ||
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
