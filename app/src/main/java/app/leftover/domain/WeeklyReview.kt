package app.leftover.domain

import app.leftover.data.db.TxType
import java.time.LocalDate

/** A short look back at the last seven days, written to inform and never to scold. */
data class WeeklyReview(
    val spent: Paise,
    val previousSpent: Paise,
    val topCategoryId: Long?,
    val topCategorySpent: Paise,
    val noSpendDays: Int,
    val daysToPayday: Int,
    val leftToSpend: Paise,
) {
    /** Positive when this week cost more than last week. */
    val change: Paise get() = spent - previousSpent

    companion object {
        fun of(ledger: Ledger): WeeklyReview {
            val today = ledger.today
            val thisWeek = (0..6).map { today.minusDays(it.toLong()) }.toSet()
            val lastWeek = (7..13).map { today.minusDays(it.toLong()) }.toSet()

            fun own(days: Set<LocalDate>) = ledger.transactions.filter { it.occurredAt.toLocalDate() in days && !it.excludeFromBudgets }
            fun amount(tx: app.leftover.data.db.TransactionEntity) = when (tx.type) {
                TxType.EXPENSE -> tx.amount - tx.splitOthers
                TxType.REFUND -> -tx.amount
                else -> 0L
            }

            val mine = own(thisWeek)
            val byCategory = mine.filter { it.categoryId != null }.groupBy { it.categoryId!! }.mapValues { (_, list) -> list.sumOf(::amount) }
            val top = byCategory.maxByOrNull { it.value }?.takeIf { it.value > 0 }
            val spendDays = mine.filter { it.type == TxType.EXPENSE }.map { it.occurredAt.toLocalDate() }.toSet()
            return WeeklyReview(
                spent = mine.sumOf(::amount).coerceAtLeast(0),
                previousSpent = own(lastWeek).sumOf(::amount).coerceAtLeast(0),
                topCategoryId = top?.key,
                topCategorySpent = top?.value ?: 0,
                noSpendDays = thisWeek.count { it !in spendDays },
                daysToPayday = ledger.cycle.daysUntilPayday(today),
                leftToSpend = ledger.leftToSpend.left,
            )
        }
    }
}
