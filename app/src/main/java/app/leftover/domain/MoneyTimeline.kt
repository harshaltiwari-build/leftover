package app.leftover.domain

import app.leftover.data.db.AccountType
import java.time.LocalDate

enum class TimelineKind { GOAL, BILL, EMI, CARD, PAYDAY }

/** One thing that happens to your money before payday. [amount] is what goes out (or, for payday, what comes in). */
data class TimelineEvent(val date: LocalDate, val title: String, val amount: Paise, val kind: TimelineKind, val after: Paise)

/**
 * Your money from today to payday, day by day: goal money set aside now, then each bill, EMI and card due on its
 * date, with what's left in your accounts after each. It ends at payday on exactly what Left to spend shows, so the
 * headline number can be followed step by step. Everyday spending isn't in it; that's what the daily safe amount is for.
 */
data class MoneyTimeline(val start: Paise, val outgoings: List<TimelineEvent>, val payday: TimelineEvent) {
    val leftAtPayday: Paise get() = outgoings.lastOrNull()?.after ?: start

    /** Outgoings grouped by day, earliest first. */
    val byDay: List<Pair<LocalDate, List<TimelineEvent>>> get() = outgoings.groupBy { it.date }.toList()

    companion object {
        fun build(ledger: Ledger, usualIncome: Paise): MoneyTimeline {
            val today = ledger.today
            val payday = ledger.cycle.nextPayday
            val left = ledger.leftToSpend
            data class Out(val date: LocalDate, val title: String, val amount: Paise, val kind: TimelineKind)
            val outs = mutableListOf<Out>()
            left.goals.forEach { outs += Out(today, it.label, it.amount, TimelineKind.GOAL) }
            ledger.bills.filter { !it.archived }.forEach { bill ->
                val due = ledger.billNextDue(bill)
                if (!due.isAfter(payday)) outs += Out(maxOf(due, today), bill.name, bill.amount, TimelineKind.BILL)
            }
            ledger.emis.filter { !it.archived && it.remainingMonths > 0 }.forEach { emi ->
                val due = EmiSchedule.summary(emi, today).nextDue ?: return@forEach
                if (!due.isAfter(payday)) outs += Out(maxOf(due, today), emi.name, emi.monthlyAmount, TimelineKind.EMI)
            }
            ledger.accounts.filter { it.type == AccountType.CARD && it.includeInLeft }.forEach { card ->
                val cycle = ledger.cardCycle(card)
                if (cycle != null) {
                    if (cycle.billed > 0 && !cycle.billDue.isAfter(payday)) outs += Out(maxOf(cycle.billDue, today), card.name, cycle.billed, TimelineKind.CARD)
                } else {
                    val owed = ledger.cardOwed(card)
                    val due = card.dueDay?.let { DueDates.nextUnpaid(it, null, today) }
                    if (owed > 0 && (due == null || !due.isAfter(payday))) outs += Out(maxOf(due ?: today, today), card.name, owed, TimelineKind.CARD)
                }
            }
            // Goal money first on a shared day: it's set aside the moment the cycle starts.
            val ordered = outs.sortedWith(compareBy<Out> { it.date }.thenBy { it.kind != TimelineKind.GOAL }.thenByDescending { it.amount })
            var running = left.inAccounts
            val events = ordered.map { running -= it.amount; TimelineEvent(it.date, it.title, it.amount, it.kind, running) }
            return MoneyTimeline(left.inAccounts, events, TimelineEvent(payday, "Payday", usualIncome, TimelineKind.PAYDAY, running))
        }
    }
}
