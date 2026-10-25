package app.leftover.widget

import app.leftover.data.Snapshot
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.Ledger
import app.leftover.domain.Paise
import app.leftover.domain.toLocalDate

/** A repeat spend the widget can log with one tap. */
data class QuickPreset(val label: String, val amount: Paise, val categoryId: Long?, val accountId: Long, val merchant: String = "")

object QuickPresets {
    const val LOOKBACK_DAYS = 90L
    const val MIN_REPEATS = 2
    const val MAX_AMOUNT: Paise = 100_000

    /**
     * The user's most repeated small expenses: same category and amount at least [MIN_REPEATS] times in the last
     * [LOOKBACK_DAYS] days, most frequent first. Nothing is guessed, so a new user simply gets no presets.
     */
    fun from(ledger: Ledger, limit: Int = 2): List<QuickPreset> {
        val since = ledger.today.minusDays(LOOKBACK_DAYS)
        val fallbackAccount = ledger.accounts.firstOrNull { it.includeInLeft }?.id ?: ledger.accounts.firstOrNull()?.id ?: return emptyList()
        val candidates = ledger.transactions.filter {
            it.type == TxType.EXPENSE && it.categoryId != null && it.amount in 1..MAX_AMOUNT && !it.occurredAt.toLocalDate().isBefore(since)
        }
        return candidates.groupBy { it.categoryId to it.amount }
            .filter { it.value.size >= MIN_REPEATS }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Pair<Long?, Long>, List<TransactionEntity>>> { it.value.size }.thenByDescending { entry -> entry.value.maxOf { it.occurredAt } })
            .take(limit)
            .map { (key, list) ->
                val merchants = list.map { it.merchant }.distinct()
                val label = merchants.singleOrNull()?.takeIf { it.isNotBlank() } ?: ledger.category(key.first)?.name ?: "Expense"
                val account = list.groupBy { it.accountId }.maxByOrNull { it.value.size }?.key ?: fallbackAccount
                QuickPreset(label = label, amount = key.second, categoryId = key.first, accountId = account, merchant = merchants.singleOrNull().orEmpty())
            }
    }
}

data class BudgetLine(val name: String, val spent: Paise, val limit: Paise)

data class LastEntry(val title: String, val amount: Paise)

/** Everything a widget draws, computed once so every size shows the same numbers. */
data class WidgetModel(
    val ready: Boolean,
    val hidden: Boolean,
    val left: Paise,
    val perDay: Paise,
    val daysToPayday: Int,
    val presets: List<QuickPreset>,
    val budgets: List<BudgetLine>,
    val last: LastEntry?,
) {
    companion object {
        val Empty = WidgetModel(false, false, 0, 0, 0, emptyList(), emptyList(), null)

        fun from(snapshot: Snapshot): WidgetModel {
            val ledger = snapshot.ledger
            val left = ledger.leftToSpend
            val budgets = snapshot.categories
                .filter { !it.hidden && (it.budgetLimit ?: 0L) > 0 }
                .map { BudgetLine(it.name, ledger.spent(it.id).coerceAtLeast(0), it.budgetLimit!!) }
                .sortedByDescending { it.spent.toDouble() / it.limit }
                .take(3)
            val last = snapshot.transactions.firstOrNull { it.type == TxType.EXPENSE || it.type == TxType.INCOME }?.let { tx ->
                LastEntry(tx.merchant.ifBlank { ledger.category(tx.categoryId)?.name ?: "Entry" }, if (tx.type == TxType.EXPENSE) -tx.amount else tx.amount)
            }
            return WidgetModel(
                ready = snapshot.settings.onboarded,
                hidden = snapshot.settings.hideBalances,
                left = left.left,
                perDay = left.perDay,
                daysToPayday = ledger.cycle.daysUntilPayday(ledger.today),
                presets = QuickPresets.from(ledger),
                budgets = budgets,
                last = last,
            )
        }
    }
}
