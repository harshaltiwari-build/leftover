package app.leftover.domain

import app.leftover.data.db.AccountType
import java.time.format.TextStyle
import java.util.Locale

enum class HeroKind { NORMAL, NO_BALANCE, OVER, PAYDAY }

/**
 * What Home's main card says when the numbers can't be normal. Each state says what happened and gives one way
 * forward: no balance yet (add one, no made-up number), over until payday (calm, and what would cover it), and payday
 * (the one day Leftover celebrates, pointing to last cycle's recap).
 */
data class HeroState(val kind: HeroKind, val title: String, val line: String?, val action: String?) {
    companion object {
        fun of(ledger: Ledger, usualIncome: Paise, hidden: Boolean): HeroState {
            val left = ledger.leftToSpend
            val noBalance = ledger.accounts.none { it.includeInLeft && it.type != AccountType.CARD }
            if (noBalance) return HeroState(
                HeroKind.NO_BALANCE, "Left until payday", "Add roughly what's in your accounts and Leftover works out what's safe.", "Add balance",
            )
            if (left.left < 0) {
                val goal = left.goals.maxByOrNull { it.amount }
                val way = when {
                    goal == null -> " Adding money that came in covers it."
                    hidden -> " Pausing ${goal.label} this cycle would help."
                    else -> " Pausing ${goal.label} this cycle frees ${Money.format(goal.amount)}."
                }
                return HeroState(HeroKind.OVER, "Over until payday", "This would come out of bill or goal money.$way", "See options")
            }
            // Only once the app was in use last cycle: a first day that happens to be payday has nothing to celebrate yet.
            val usedBefore = ledger.transactions.any { it.occurredAt.toLocalDate().isBefore(ledger.cycle.start) }
            if (ledger.today == ledger.cycle.start && usedBefore) {
                val month = ledger.cycle.start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                val previous = ledger.cycle.previous()
                val recap = if (ledger.totalSpent(previous) > 0) "See ${previous.start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}'s recap" else null
                val perDay = if (hidden) "" else " About ${Money.format(left.perDay)} a day."
                return HeroState(HeroKind.PAYDAY, "It's payday", "Yours for $month after bills and goals.$perDay", recap)
            }
            return HeroState(HeroKind.NORMAL, "Left until payday", null, null)
        }
    }
}
