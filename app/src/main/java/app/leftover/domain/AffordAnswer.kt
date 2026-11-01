package app.leftover.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line of what a purchase changes: [from] → [to], with a short [note]. [warning] lines are shown in the warm colour. */
data class ImpactRow(val name: String, val from: String, val to: String, val note: String, val warning: Boolean)

/**
 * "Can I afford this?" in today's terms. A price inside today's safe amount fits; past it but inside Left to spend is
 * a stretch, shown as how much less every day until payday gets; past Left to spend waits for payday. A category going
 * over its limit also makes it a stretch. Hide balances keeps every amount out of the words.
 */
data class AffordAnswer(val verdict: Verdict, val title: String, val body: String, val rows: List<ImpactRow>) {
    companion object {
        private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        private fun roundDown(p: Paise): Paise = p / 100 * 100

        fun of(
            safe: SafeToday, price: Paise, payday: LocalDate, categoryName: String?, categorySpent: Paise, categoryLimit: Paise?, hidden: Boolean,
        ): AffordAnswer {
            fun m(p: Paise) = Money.display(p, hidden)
            val freeToday = safe.leftToday.coerceAtLeast(0)
            val leftAfter = safe.leftNow - price
            val daysAfter = safe.days - 1
            val tomorrowBefore = safe.tomorrow
            val tomorrowAfter = if (daysAfter > 0) roundDown(leftAfter.coerceAtLeast(0) / daysAfter) else null
            val categoryOver = categoryLimit?.let { categorySpent + price - it }?.takeIf { it > 0 }
            val pastToday = safe.over || price > freeToday
            val verdict = when {
                leftAfter < 0 -> Verdict.NOT_BEFORE_PAYDAY
                pastToday || categoryOver != null -> Verdict.STRETCH
                else -> Verdict.COMFORTABLE
            }

            val body = when (verdict) {
                Verdict.NOT_BEFORE_PAYDAY ->
                    (if (hidden) "It's more than you have until payday." else "It's ${m(-leftAfter)} more than you have until payday.") +
                        " It would come out of bill or goal money."
                Verdict.COMFORTABLE -> {
                    val first = if (hidden) "It's within today's safe amount." else "It's within today's ${m(freeToday)}."
                    val next = when {
                        tomorrowBefore == null || tomorrowAfter == null -> " Payday is tomorrow."
                        hidden -> " Tomorrow's would be a little lower."
                        else -> " Tomorrow would be ${m(tomorrowAfter)} instead of ${m(tomorrowBefore)}."
                    }
                    first + next
                }
                Verdict.STRETCH -> buildString {
                    val perDayLess = if (tomorrowBefore != null && tomorrowAfter != null) tomorrowBefore - tomorrowAfter else null
                    when {
                        !pastToday -> append(if (hidden) "It's within today's safe amount." else "It's within today's ${m(freeToday)}.")
                        perDayLess == null -> append("It uses most of what's left until payday.")
                        safe.over -> append(
                            if (hidden) "Today is already past its safe amount, so this comes out of the days ahead."
                            else "Today is already past its safe amount, so the next $daysAfter days each get ${m(perDayLess)} less.",
                        )
                        else -> append(
                            if (hidden) "It's more than today's safe amount, so the days ahead each get a little less."
                            else "It's ${m(price - freeToday)} more than today's safe amount, so the next $daysAfter days each get ${m(perDayLess)} less.",
                        )
                    }
                    if (categoryOver != null && categoryName != null) {
                        append(if (hidden) " $categoryName would go over its limit." else " $categoryName would go ${m(categoryOver)} over its limit.")
                    } else append(" Waiting until payday costs you nothing.")
                }
            }

            val rows = buildList {
                if (tomorrowBefore != null && tomorrowAfter != null && tomorrowBefore > 0) add(
                    ImpactRow(
                        "Safe each day after today", m(tomorrowBefore), m(tomorrowAfter),
                        if (hidden) "Spread over the days to payday" else "Spread over the $daysAfter ${if (daysAfter == 1) "day" else "days"} to ${payday.format(dayMonth)}",
                        warning = verdict != Verdict.COMFORTABLE,
                    ),
                )
                add(
                    ImpactRow(
                        "Left until payday", m(safe.leftNow), if (leftAfter >= 0) m(leftAfter) else "${m(-leftAfter)} short",
                        if (leftAfter >= 0) "Bills and card dues stay covered" else "Dips into money kept for bills and goals",
                        warning = leftAfter < 0,
                    ),
                )
                if (categoryLimit != null && categoryName != null) {
                    fun state(remaining: Paise) = when {
                        hidden -> if (remaining >= 0) "Within limit" else "Over limit"
                        remaining >= 0 -> "${m(remaining)} left"
                        else -> "${m(-remaining)} over"
                    }
                    val after = categoryLimit - categorySpent - price
                    add(ImpactRow("$categoryName budget", state(categoryLimit - categorySpent), state(after), if (hidden) "This cycle" else "Of ${m(categoryLimit)} this cycle", warning = after < 0))
                }
            }

            val title = when (verdict) {
                Verdict.COMFORTABLE -> "Yes, it fits in today"
                Verdict.STRETCH -> "You can, but it's a stretch"
                Verdict.NOT_BEFORE_PAYDAY -> "Not before payday"
            }
            return AffordAnswer(verdict, title, body, rows)
        }
    }
}
