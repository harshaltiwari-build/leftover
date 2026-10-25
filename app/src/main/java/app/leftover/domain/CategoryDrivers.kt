package app.leftover.domain

import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TxType

/** How one category compares with the same point in the previous cycle. */
data class CategoryChange(val category: CategoryEntity, val now: Paise, val before: Paise) {
    val difference: Paise get() = now - before
    /** Whole percent change, or null when there was nothing to compare with. */
    val percent: Int? get() = if (before <= 0) null else kotlin.math.round(difference * 100.0 / before).toInt()
}

/** Finds what moved your spending, in plain words. Small changes are not worth mentioning. */
object CategoryDrivers {
    private const val MIN_CHANGE = 100_00L
    private const val MIN_PERCENT = 15

    fun changes(ledger: Ledger): List<CategoryChange> {
        val day = ledger.cycle.dayOfCycle(ledger.today)
        val previous = ledger.cycle.previous()
        fun spent(cycle: PayCycle, upTo: Int?): Map<Long, Paise> = ledger.transactions
            .filter {
                val d = it.occurredAt.toLocalDate()
                it.categoryId != null && !it.excludeFromBudgets && cycle.contains(d) && (upTo == null || cycle.dayOfCycle(d) <= upTo)
            }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.sumOf { if (it.type == TxType.EXPENSE) it.amount - it.splitOthers else if (it.type == TxType.REFUND) -it.amount else 0L } }
        val now = spent(ledger.cycle, null)
        val before = spent(previous, day)
        return ledger.categories.filter { it.kind == CategoryKind.EXPENSE }
            .map { CategoryChange(it, (now[it.id] ?: 0).coerceAtLeast(0), (before[it.id] ?: 0).coerceAtLeast(0)) }
            .filter { it.now > 0 || it.before > 0 }
    }

    private fun significant(change: CategoryChange): Boolean =
        kotlin.math.abs(change.difference) >= MIN_CHANGE && (change.percent?.let { kotlin.math.abs(it) >= MIN_PERCENT } ?: true)

    /** One sentence for Home: the category pushing spending up, or the one bringing it down. Null when nothing stands out. */
    fun headline(changes: List<CategoryChange>): String? {
        val ups = changes.filter { it.difference > 0 && it.before > 0 && significant(it) }.sortedByDescending { it.difference }
        if (ups.isNotEmpty()) {
            val top = ups.first()
            return if (ups.size == 1) "${top.category.name} is the only category up: ${top.percent}% more than at this point last cycle."
            else "${top.category.name} is up the most: ${top.percent}% more than at this point last cycle."
        }
        val down = changes.filter { it.difference < 0 && significant(it) }.minByOrNull { it.difference }
        return down?.let { "${it.category.name} is down ${-(it.percent ?: 0)}% on this point last cycle." }
    }

    /** What to say when a category is tapped in Insights. */
    fun explain(change: CategoryChange?, hidden: Boolean): String = when {
        change == null -> "Nothing in this category last cycle to compare with."
        change.before <= 0 -> "Nothing in this category at this point last cycle, so this is new."
        hidden -> if (change.difference > 0) "Up on this point last cycle." else if (change.difference < 0) "Down on this point last cycle." else "The same as this point last cycle."
        change.difference > 0 -> "${Money.format(change.difference)} more than at this point last cycle (${change.percent}% up)."
        change.difference < 0 -> "${Money.format(-change.difference)} less than at this point last cycle (${-(change.percent ?: 0)}% down)."
        else -> "The same as at this point last cycle."
    }
}
