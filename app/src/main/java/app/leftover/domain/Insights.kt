package app.leftover.domain

import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind

data class CycleTotal(val cycle: PayCycle, val spent: Paise, val isCurrent: Boolean)

data class CategorySpend(val category: CategoryEntity, val spent: Paise)

data class SameDayComparison(val dayOfCycle: Int, val spentNow: Paise, val spentBefore: Paise) {
    val difference: Paise get() = spentNow - spentBefore
    val hasHistory: Boolean get() = spentBefore > 0
}

data class Insights(
    val trend: List<CycleTotal>,
    val categories: List<CategorySpend>,
    val comparison: SameDayComparison,
    val income: Paise,
    val spent: Paise,
) {
    /** Share of income kept, 0..100, or null when no income was logged this cycle. */
    val keptPercent: Int? get() = if (income <= 0) null else (((income - spent).toDouble() / income) * 100).toInt().coerceIn(0, 100)

    val averageSpent: Paise get() = trend.filter { !it.isCurrent && it.spent > 0 }.map { it.spent }.average().let { if (it.isNaN()) 0L else it.toLong() }

    companion object {
        const val CYCLES_SHOWN = 6

        fun build(ledger: Ledger): Insights {
            val cycles = generateSequence(ledger.cycle) { it.previous() }.take(CYCLES_SHOWN).toList().reversed()
            val trend = cycles.map { CycleTotal(it, ledger.totalSpent(it), isCurrent = it == ledger.cycle) }
            val categories = ledger.categories
                .filter { it.kind == CategoryKind.EXPENSE }
                .map { CategorySpend(it, ledger.spent(it.id).coerceAtLeast(0)) }
                .filter { it.spent > 0 }
                .sortedByDescending { it.spent }
            val day = ledger.cycle.dayOfCycle(ledger.today)
            val comparison = SameDayComparison(day, ledger.totalSpent(ledger.cycle), ledger.totalSpent(ledger.cycle.previous(), upToDayOfCycle = day))
            return Insights(trend, categories, comparison, ledger.income(), ledger.totalSpent())
        }
    }
}

/** Plain-language one-liners shown above each chart. */
object InsightText {
    fun comparison(c: SameDayComparison, hidden: Boolean): String = when {
        !c.hasHistory -> "This is your first full cycle — comparisons get sharper next month."
        hidden -> if (c.difference <= 0) "You're spending less than at this point last cycle." else "You're spending more than at this point last cycle."
        c.difference <= 0 -> "By day ${c.dayOfCycle} you've spent ${Money.format(-c.difference)} less than at this point last cycle."
        else -> "By day ${c.dayOfCycle} you've spent ${Money.format(c.difference)} more than at this point last cycle."
    }

    fun topCategories(categories: List<CategorySpend>, total: Paise): String {
        if (categories.isEmpty() || total <= 0) return "Add a few expenses and your biggest categories will show up here."
        val top = categories.take(2)
        val share = (top.sumOf { it.spent }.toDouble() / total * 100).toInt()
        return if (top.size == 1) "${top[0].category.name} is $share% of your spending." else "${top[0].category.name} and ${top[1].category.name} are $share% of your spending."
    }

    fun kept(percent: Int?): String = if (percent == null) "Log your income to see how much of it you keep." else "You kept $percent% of what came in this cycle."
}

object CsvExport {
    private fun cell(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    fun build(ledger: Ledger): String {
        val accounts = ledger.accounts.associateBy { it.id }
        val rows = ledger.transactions.sortedBy { it.occurredAt }.map { tx ->
            listOf(
                tx.occurredAt.toLocalDate().toString(),
                tx.type.name.lowercase(),
                "%.2f".format(tx.amount / 100.0),
                accounts[tx.accountId]?.name.orEmpty(),
                accounts[tx.toAccountId]?.name.orEmpty(),
                ledger.category(tx.categoryId)?.name.orEmpty(),
                tx.merchant,
                tx.note,
            ).joinToString(",") { cell(it) }
        }
        return (listOf("date,type,amount_inr,account,to_account,category,merchant,note") + rows).joinToString("\n") + "\n"
    }
}
