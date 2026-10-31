package app.leftover.domain

import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxFeeling
import app.leftover.data.db.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * One answer. [headline] is the number or short answer, [summary] says it in a sentence, and [entries] are the
 * transactions behind it, so every answer can be checked. [hidden] answers keep amounts out of [headline] and [summary].
 */
data class AskAnswer(
    val question: String,
    val headline: String,
    val summary: String,
    val period: String? = null,
    val lines: List<Pair<String, String>> = emptyList(),
    val entries: List<TransactionEntity> = emptyList(),
    val understood: Boolean = true,
)

/**
 * "Ask about your money", worked out on the phone from fixed question shapes rather than a language model: Leftover
 * has no internet permission. It understands amounts spent on a category or at a place over a period, what changed,
 * bills coming up, goals, the biggest spends, what's left, and regrets. Anything else gets a list of what to try.
 */
object Ask {
    val suggestions = listOf(
        "How much did I spend on food this month?",
        "What changed vs last cycle?",
        "Which bills are due this week?",
        "Am I on track for my goals?",
        "Biggest spends this cycle",
        "How much can I spend today?",
        "What did I regret this month?",
    )

    /** Broad words people use that cover several categories, matched by category name. */
    private val groups = mapOf(
        "food" to listOf("eating", "grocer", "snack", "coffee", "food", "restaurant", "dining"),
        "khana" to listOf("eating", "grocer", "snack", "coffee", "food"),
        "travel" to listOf("transport", "travel", "cab", "fuel"),
        "transport" to listOf("transport", "travel", "cab", "fuel"),
        "cab" to listOf("transport"),
        "subscription" to listOf("subscription", "fun"),
        "shopping" to listOf("shopping"),
        "coffee" to listOf("coffee"),
        "chai" to listOf("coffee", "snack"),
    )

    data class Period(val label: String, val span: DateSpan)

    /** The period a question is about. Defaults to the current pay cycle. */
    fun period(q: String, today: LocalDate, cycle: PayCycle): Period {
        val lastN = Regex("""last (\d{1,3}) days?""").find(q)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            lastN != null -> Period("in the last $lastN days", DateSpan(today.minusDays(lastN - 1L), today))
            has(q, "yesterday") || has(q, "kal") -> Period("yesterday", DateSpan(today.minusDays(1), today.minusDays(1)))
            has(q, "today") || has(q, "aaj") -> Period("today", DateSpan(today, today))
            "last week" in q -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1).let { Period("last week", DateSpan(it, it.plusDays(6))) }
            "this week" in q || "week" in q -> Period("this week", DateSpan(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today))
            "last month" in q -> YearMonth.from(today).minusMonths(1).let { Period("in ${it.month.name.lowercase().replaceFirstChar(Char::titlecase)}", DateSpan(it.atDay(1), it.atEndOfMonth())) }
            "this month" in q || "month" in q -> Period("this month", DateSpan(today.withDayOfMonth(1), today))
            "last cycle" in q || "last payday" in q -> cycle.previous().let { Period("last cycle", DateSpan(it.start, it.end)) }
            "this year" in q || "year" in q -> Period("this year", DateSpan(today.withDayOfYear(1), today))
            else -> Period("this cycle", DateSpan(cycle.start, minOf(today, cycle.end)))
        }
    }

    /** Whole-word match, so "top" doesn't fire on "stop" or "kal" on "kalyan". */
    private fun has(q: String, word: String) = Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(q)

    fun answer(question: String, ledger: Ledger, hidden: Boolean): AskAnswer {
        val q = question.lowercase(Locale.ENGLISH).replace("’", "'").trim()
        val period = period(q, ledger.today, ledger.cycle)
        fun money(p: Paise) = Money.display(p, hidden)
        return when {
            q.isBlank() -> fallback(question)
            "regret" in q || "wasted" in q || has(q, "waste") -> regrets(question, ledger, period, ::money)
            "change" in q || "different" in q || has(q, "vs") || "compare" in q -> changes(question, ledger, hidden)
            has(q, "bill") || has(q, "bills") || has(q, "due") || "renew" in q || ("subscription" in q && ("week" in q || "coming" in q)) -> bills(question, ledger, q, ::money)
            "goal" in q || "on track" in q || "save for" in q || (goalNamed(q, ledger) != null && "spen" !in q) -> goals(question, ledger, q, ::money)
            "biggest" in q || "largest" in q || "most expensive" in q || has(q, "top") -> biggest(question, ledger, period, ::money)
            has(q, "left") || "can i spend" in q || "afford today" in q || "per day" in q -> left(question, ledger, ::money)
            "income" in q || "earn" in q || "came in" in q || has(q, "salary") -> income(question, ledger, period, ::money)
            "spend" in q || "spent" in q || "how much" in q || "kitna" in q || "kharch" in q -> spent(question, ledger, q, period, hidden, ::money)
            else -> fallback(question)
        }
    }

    private fun spends(ledger: Ledger, span: DateSpan) = ledger.transactions.filter {
        (it.type == TxType.EXPENSE || it.type == TxType.REFUND) && !it.excludeFromBudgets && span.contains(it.occurredAt.toLocalDate())
    }

    /** Categories named in the question, either directly or through a broad word like "food". */
    fun categoriesIn(q: String, ledger: Ledger): List<CategoryEntity> {
        val expense = ledger.categories.filter { it.kind == CategoryKind.EXPENSE }
        val direct = expense.filter { cat -> cat.name.lowercase().split(" ", "&").filter { it.length > 2 }.any { word -> Regex("""\b${Regex.escape(word)}""").containsMatchIn(q) } }
        val viaGroups = groups.filterKeys { Regex("""\b$it""").containsMatchIn(q) }.values.flatten().toSet()
            .flatMap { stem -> expense.filter { it.name.lowercase().contains(stem) } }
        return (direct + viaGroups).distinctBy { it.id }
    }

    private fun merchantIn(q: String, ledger: Ledger): String? = ledger.transactions.asSequence()
        .map { it.merchant.trim() }.filter { it.length >= 3 }.distinct()
        .firstOrNull { Regex("""\b${Regex.escape(it.lowercase())}\b""").containsMatchIn(q) }

    private fun spent(question: String, ledger: Ledger, q: String, period: Period, hidden: Boolean, money: (Paise) -> String): AskAnswer {
        val all = spends(ledger, period.span)
        val merchant = merchantIn(q, ledger)
        val cats = if (merchant == null) categoriesIn(q, ledger) else emptyList()
        val chosen = when {
            merchant != null -> all.filter { it.merchant.trim().equals(merchant, ignoreCase = true) }
            cats.isNotEmpty() -> all.filter { tx -> cats.any { it.id == tx.categoryId } }
            else -> all
        }
        val total = chosen.sumOf { RangeReport.ownShare(it) }
        val everything = all.sumOf { RangeReport.ownShare(it) }
        val what = merchant ?: cats.joinToString(" + ") { it.name }.ifEmpty { "everything" }
        val share = if (everything > 0 && chosen.size != all.size) " That's ${total * 100 / everything}% of your spending." else ""
        val perDay = if (hidden || period.span.days == 1) "" else " About ${money(total / period.span.days)} a day."
        val lines = if (cats.size > 1) cats.map { cat -> val list = chosen.filter { it.categoryId == cat.id }; "${cat.name} · ${list.size}" to money(list.sumOf { RangeReport.ownShare(it) }) } else emptyList()
        return AskAnswer(
            question, money(total),
            if (chosen.isEmpty()) "Nothing on ${what.lowercase()} ${period.label}." else "${chosen.size} ${if (chosen.size == 1) "payment" else "payments"} on ${if (merchant != null) what else what.lowercase()} ${period.label}.$share$perDay",
            period.span.label(), lines, chosen.sortedByDescending { it.occurredAt },
        )
    }

    private fun income(question: String, ledger: Ledger, period: Period, money: (Paise) -> String): AskAnswer {
        val list = ledger.transactions.filter { it.type == TxType.INCOME && period.span.contains(it.occurredAt.toLocalDate()) }
        val total = list.sumOf { it.amount }
        return AskAnswer(question, money(total), if (list.isEmpty()) "No income logged ${period.label}." else "${list.size} ${if (list.size == 1) "payment" else "payments"} came in ${period.label}. Money friends paid back isn't counted as income.", period.span.label(), entries = list.sortedByDescending { it.occurredAt })
    }

    private fun biggest(question: String, ledger: Ledger, period: Period, money: (Paise) -> String): AskAnswer {
        val list = spends(ledger, period.span).filter { it.type == TxType.EXPENSE }.sortedByDescending { RangeReport.ownShare(it) }.take(5)
        val top = list.firstOrNull()
        return AskAnswer(
            question, top?.let { money(RangeReport.ownShare(it)) } ?: "—",
            if (top == null) "No spending ${period.label}." else "Your biggest spend ${period.label} was ${top.merchant.ifBlank { ledger.category(top.categoryId)?.name ?: "an expense" }}. Here are the top ${list.size}.",
            period.span.label(), entries = list,
        )
    }

    private fun left(question: String, ledger: Ledger, money: (Paise) -> String): AskAnswer {
        val l = ledger.leftToSpend
        return AskAnswer(
            question, money(l.perDay) + " a day",
            "You have ${money(l.left)} left to spend over ${l.daysUntilPayday} ${if (l.daysUntilPayday == 1) "day" else "days"} until payday, after bills, card dues and goal money.",
            null,
            listOf("In your accounts" to money(l.inAccounts), "Bills before payday" to "− ${money(l.billsTotal)}", "Card dues" to "− ${money(l.cardDuesTotal)}", "Set aside for goals" to "− ${money(l.goalsTotal)}"),
        )
    }

    private fun bills(question: String, ledger: Ledger, q: String, money: (Paise) -> String): AskAnswer {
        val days = when { has(q, "today") -> 0L; "month" in q || "cycle" in q -> 31L; else -> 7L }
        val due = ledger.bills.filter { !it.archived && (!"subscription".let { s -> s in q } || it.isSubscription) }
            .map { it to ledger.billNextDue(it) }
            .filter { ChronoUnit.DAYS.between(ledger.today, it.second) in 0..days }
            .sortedBy { it.second }
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
        val window = when (days) { 0L -> "today"; 7L -> "in the next 7 days"; else -> "in the next month" }
        return AskAnswer(
            question, if (due.isEmpty()) "None" else money(due.sumOf { it.first.amount }),
            if (due.isEmpty()) "Nothing due $window." else "${due.size} ${if (due.size == 1) "bill" else "bills"} due $window. These are already set aside in Left to spend.",
            null, due.map { (bill, date) -> "${bill.name} · ${fmt.format(date)}" to money(bill.amount) },
        )
    }

    private fun goalNamed(q: String, ledger: Ledger) = ledger.goals.filter { !it.archived }.firstOrNull { g ->
        g.name.lowercase().split(" ").filter { it.length > 2 }.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(q) }
    }

    private fun goals(question: String, ledger: Ledger, q: String, money: (Paise) -> String): AskAnswer {
        val named = goalNamed(q, ledger)
        val goals = if (named != null) listOf(named) else ledger.goals.filter { !it.archived }
        if (goals.isEmpty()) return AskAnswer(question, "No goals yet", "Add one from Plan → Goals and Leftover will tell you when you'll get there.")
        val fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val progress = goals.map { g -> g to GoalPace.progress(ledger.goalSaved(g.id), g.target, g.perCycle, g.targetEpochDay?.let(LocalDate::ofEpochDay), ledger.cycle.nextPayday) }
        val behind = progress.count { it.second.status == GoalStatus.BEHIND }
        val headline = when {
            named != null -> progress.single().second.let { p -> when (p.status) { GoalStatus.DONE -> "Done"; GoalStatus.ON_TRACK -> "On track"; GoalStatus.BEHIND -> "Behind"; GoalStatus.STEADY -> "${p.percent}%" } }
            behind == 0 -> "On track"
            else -> "$behind behind"
        }
        val summary = if (named != null) {
            val p = progress.single().second
            when (p.status) {
                GoalStatus.DONE -> "${named.name} is fully saved."
                GoalStatus.ON_TRACK -> "${named.name}: ${money(p.saved)} of ${money(p.target)}. At ${money(p.perCycle)} a payday you'll get there by ${p.finishDate?.let(fmt::format)}."
                GoalStatus.BEHIND -> "${named.name}: ${money(p.saved)} of ${money(p.target)}. Set aside ${money(p.shortfallPerCycle)} more each payday to make your date."
                GoalStatus.STEADY -> "${named.name}: ${money(p.saved)} of ${money(p.target)}." + (p.finishDate?.let { " At this pace, ${fmt.format(it)}." } ?: " Nothing is set aside each payday yet.")
            }
        } else if (behind == 0) "All ${goals.size} ${if (goals.size == 1) "goal is" else "goals are"} on pace." else "$behind of ${goals.size} need a little more each payday to make their dates."
        return AskAnswer(question, headline, summary, null, progress.map { (g, p) -> g.name to "${p.percent}% · ${when (p.status) { GoalStatus.DONE -> "done"; GoalStatus.ON_TRACK -> "on track"; GoalStatus.BEHIND -> "behind"; GoalStatus.STEADY -> p.finishDate?.let(fmt::format) ?: "no pace" }}" })
    }

    private fun changes(question: String, ledger: Ledger, hidden: Boolean): AskAnswer {
        val changes = CategoryDrivers.changes(ledger).filter { kotlin.math.abs(it.difference) >= 100_00L }.sortedByDescending { kotlin.math.abs(it.difference) }.take(5)
        val day = ledger.cycle.dayOfCycle(ledger.today)
        val now = ledger.totalSpent(ledger.cycle)
        val before = ledger.totalSpent(ledger.cycle.previous(), upToDayOfCycle = day)
        val diff = now - before
        val headline = if (before <= 0) "First cycle" else if (hidden) (if (diff <= 0) "Less" else "More") else (if (diff <= 0) "− " else "+ ") + Money.format(kotlin.math.abs(diff))
        val summary = when {
            before <= 0 -> "There's no earlier cycle to compare with yet."
            diff <= 0 -> "By day $day you've spent ${if (hidden) "less" else Money.format(-diff) + " less"} than at this point last cycle."
            else -> "By day $day you've spent ${if (hidden) "more" else Money.format(diff) + " more"} than at this point last cycle."
        } + (CategoryDrivers.headline(changes)?.let { " $it" } ?: "")
        return AskAnswer(question, headline, summary, "day $day vs day $day", changes.map { ch ->
            ch.category.name to if (hidden) (if (ch.difference > 0) "up" else "down") else "${if (ch.difference > 0) "+" else "−"}${Money.format(kotlin.math.abs(ch.difference))}" + (ch.percent?.let { " (${if (it > 0) "+" else ""}$it%)" } ?: "")
        })
    }

    private fun regrets(question: String, ledger: Ledger, period: Period, money: (Paise) -> String): AskAnswer {
        val all = spends(ledger, period.span).filter { it.type == TxType.EXPENSE }
        val regret = all.filter { it.feeling == TxFeeling.REGRET }
        val total = regret.sumOf { RangeReport.ownShare(it) }
        val marked = all.count { it.feeling != null }
        return AskAnswer(
            question, money(total),
            when {
                marked == 0 -> "You haven't marked any spends ${period.label}. Open one and pick Needed, Worth it or Regret."
                regret.isEmpty() -> "No regrets ${period.label}, out of $marked marked ${if (marked == 1) "spend" else "spends"}."
                else -> "${regret.size} ${if (regret.size == 1) "spend" else "spends"} you'd rather not have made ${period.label}."
            },
            period.span.label(), entries = regret.sortedByDescending { it.occurredAt },
        )
    }

    private fun fallback(question: String) = AskAnswer(
        question, "Try asking it another way",
        "Leftover answers from your own entries, on this phone, so it understands a few kinds of question: spending on a category or at a place, what changed, bills, goals, biggest spends, what's left and regrets.",
        understood = false,
    )
}
