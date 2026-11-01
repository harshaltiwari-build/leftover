package app.leftover.domain

/**
 * The words around the safe-today number. Kept apart from the screen so every state reads right, including "nothing
 * left" and "spent past what's left", and so Hide balances keeps every amount out.
 */
data class SafeTodayCopy(val title: String, val amount: String, val of: String, val line: String, val warning: Boolean) {
    companion object {
        fun of(safe: SafeToday, hidden: Boolean): SafeTodayCopy {
            fun m(p: Paise) = Money.display(p, hidden)
            val tomorrow = safe.tomorrow
            return when {
                safe.leftNow < 0 -> SafeTodayCopy("Safe to spend today", m(0), "", "Essentials only until payday", true)
                safe.leftNow == 0L -> SafeTodayCopy(
                    "Nothing left to spend", m(0), "",
                    "Essentials only until payday. Add money that came in to update this.", true,
                )
                safe.over -> SafeTodayCopy(
                    "Over today's target", m(-safe.leftToday), if (hidden) "" else "over ${Money.format(safe.target)}",
                    when {
                        tomorrow == null -> "Payday tomorrow: a fresh cycle starts"
                        hidden -> "Tomorrow's amount is a little lower"
                        else -> "Spread over the ${safe.days - 1} days left, tomorrow is ${Money.format(tomorrow)}"
                    },
                    true,
                )
                else -> SafeTodayCopy(
                    "Safe to spend today", m(safe.leftToday), if (hidden) "" else "of ${Money.format(safe.target)}",
                    when {
                        tomorrow == null -> "Payday tomorrow: a fresh cycle starts"
                        hidden -> "Spend less today and tomorrow grows"
                        else -> "Spend nothing more and tomorrow is ${Money.format(tomorrow)}"
                    },
                    false,
                )
            }
        }

        /** Quick add's line: how this amount changes today's safe amount, before saving. Hide balances keeps amounts out. */
        fun impact(safe: SafeToday, amount: Paise, hidden: Boolean = false): String = when {
            safe.over -> if (hidden) "Already over today's target" else "Already ${Money.format(-safe.leftToday)} over today's target"
            amount <= 0 -> if (hidden) "Counts against today's safe amount" else "Safe today ${Money.format(safe.leftToday)}"
            safe.leftToday - amount >= 0 -> if (hidden) "Fits in today's safe amount" else "Safe today ${Money.format(safe.leftToday)} → ${Money.format(safe.leftToday - amount)}"
            else -> if (hidden) "Takes you over today's safe amount" else "Safe today ${Money.format(safe.leftToday)} → ${Money.format(amount - safe.leftToday)} over"
        }

        /** The line on a bank-text notification: what saving it would do to today. Null with Hide balances on (lock screen). */
        fun notificationLine(safe: SafeToday, amount: Paise, hidden: Boolean): String? = when {
            hidden -> null
            safe.over -> "Today is already past its safe amount"
            safe.leftToday - amount >= 0 -> "Save it and today goes ${Money.format(safe.leftToday)} → ${Money.format(safe.leftToday - amount)}"
            else -> "Save it and today goes ${Money.format(safe.leftToday)} → ${Money.format(amount - safe.leftToday)} over"
        }

        /** Whether this amount, spent today, goes past today's safe amount (or there was none to begin with). */
        fun goesOver(safe: SafeToday, amount: Paise): Boolean = safe.over || safe.leftNow <= 0 || amount > safe.leftToday
    }
}
