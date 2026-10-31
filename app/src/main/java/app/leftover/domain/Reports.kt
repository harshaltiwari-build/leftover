package app.leftover.domain

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

/** An inclusive run of days, for reports over any period rather than only the pay cycle. */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** The same days as a [PayCycle], so every [Ledger] total works over it unchanged. */
    fun asCycle(): PayCycle = PayCycle(start, end, end.plusDays(1))

    /** The span of the same length that ends the day before this one starts, for "vs the period before". */
    fun previous(): DateSpan = DateSpan(start.minusDays(days.toLong()), start.minusDays(1))

    fun label(): String {
        val short = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        val long = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        return when {
            start == end -> long.format(start)
            start.year == end.year -> "${short.format(start)} – ${long.format(end)}"
            else -> "${long.format(start)} – ${long.format(end)}"
        }
    }
}

enum class RangePreset(val label: String) {
    THIS_CYCLE("This cycle"),
    LAST_CYCLE("Last cycle"),
    TODAY("Today"),
    LAST_7("Last 7 days"),
    LAST_30("Last 30 days"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year"),
    CUSTOM("Custom");

    /** The days this preset covers on [today]. [CUSTOM] falls back to the current cycle until dates are picked. */
    fun span(today: LocalDate, cycle: PayCycle): DateSpan = when (this) {
        THIS_CYCLE, CUSTOM -> DateSpan(cycle.start, minOf(cycle.end, today))
        LAST_CYCLE -> cycle.previous().let { DateSpan(it.start, it.end) }
        TODAY -> DateSpan(today, today)
        LAST_7 -> DateSpan(today.minusDays(6), today)
        LAST_30 -> DateSpan(today.minusDays(29), today)
        THIS_MONTH -> DateSpan(today.withDayOfMonth(1), today)
        LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { DateSpan(it.atDay(1), it.atEndOfMonth()) }
        LAST_3_MONTHS -> DateSpan(YearMonth.from(today).minusMonths(2).atDay(1), today)
        THIS_YEAR -> DateSpan(today.withDayOfYear(1), today)
    }
}

/** One bar in the money in / money out chart: a day, a week or a month depending on the span's length. */
data class ReportBucket(val label: String, val span: DateSpan, val income: Paise, val spent: Paise)

data class MerchantTotal(val name: String, val spent: Paise, val count: Int)

data class RangeReport(
    val span: DateSpan,
    val spent: Paise,
    val income: Paise,
    /** Spending in the span of the same length just before, for a fair comparison. */
    val previousSpent: Paise,
    val categories: List<CategorySpend>,
    val buckets: List<ReportBucket>,
    /** Your own share of each expense, by how it felt. The null key holds spends you haven't marked. */
    val feelings: Map<TxFeeling?, Paise>,
    val merchants: List<MerchantTotal>,
    /** Expenses and refunds in the span, newest first. */
    val entries: List<TransactionEntity>,
) {
    val net: Paise get() = income - spent
    val perDay: Paise get() = spent / span.days.coerceAtLeast(1)

    /** Share of income kept, or null when no income came in. Can be negative when spending ran past income. */
    val savingsRate: Int? get() = if (income <= 0) null else ((income - spent) * 100 / income).toInt()

    /** Share of income spent, or null when no income came in. */
    val spendRate: Int? get() = if (income <= 0) null else (spent * 100 / income).toInt()

    val regret: Paise get() = feelings[TxFeeling.REGRET] ?: 0L
    val marked: Paise get() = feelings.filterKeys { it != null }.values.sum()

    companion object {
        /** Own share of a spend: what you paid minus what friends owe back. Refunds count against it. */
        fun ownShare(tx: TransactionEntity): Paise = when (tx.type) {
            TxType.EXPENSE -> tx.amount - tx.splitOthers
            TxType.REFUND -> -tx.amount
            else -> 0L
        }

        fun build(ledger: Ledger, span: DateSpan): RangeReport {
            val cycle = span.asCycle()
            val inSpan = ledger.transactions.filter { span.contains(it.occurredAt.toLocalDate()) }
            val spends = inSpan.filter { (it.type == TxType.EXPENSE || it.type == TxType.REFUND) && !it.excludeFromBudgets }
            val categories = ledger.categories
                .filter { it.kind == CategoryKind.EXPENSE }
                .map { CategorySpend(it, ledger.spent(it.id, cycle).coerceAtLeast(0)) }
                .filter { it.spent > 0 }
                .sortedByDescending { it.spent }
            val feelings = spends.filter { it.type == TxType.EXPENSE }.groupBy { it.feeling }.mapValues { (_, list) -> list.sumOf { ownShare(it) } }
            val merchants = spends.filter { it.type == TxType.EXPENSE && it.merchant.isNotBlank() }
                .groupBy { it.merchant.trim().lowercase() }
                .map { (_, list) -> MerchantTotal(list.first().merchant.trim(), list.sumOf { ownShare(it) }, list.size) }
                .sortedByDescending { it.spent }
                .take(5)
            return RangeReport(
                span = span,
                spent = ledger.totalSpent(cycle),
                income = ledger.income(cycle),
                previousSpent = ledger.totalSpent(span.previous().asCycle()),
                categories = categories,
                buckets = buckets(span).map { ReportBucket(it.first, it.second, ledger.income(it.second.asCycle()), ledger.totalSpent(it.second.asCycle())) },
                feelings = feelings,
                merchants = merchants,
                entries = spends.sortedByDescending { it.occurredAt },
            )
        }

        /** Up to two weeks: one bar a day. Up to three months: one a week, starting Monday. Longer: one a month. */
        fun buckets(span: DateSpan): List<Pair<String, DateSpan>> {
            val out = mutableListOf<Pair<String, DateSpan>>()
            when {
                span.days <= 14 -> {
                    var d = span.start
                    val f = DateTimeFormatter.ofPattern(if (span.days <= 7) "EEE" else "d", Locale.ENGLISH)
                    while (!d.isAfter(span.end)) { out += f.format(d) to DateSpan(d, d); d = d.plusDays(1) }
                }
                span.days <= 92 -> {
                    var d = span.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
                    while (!d.isAfter(span.end)) {
                        val s = maxOf(d, span.start); val e = minOf(d.plusDays(6), span.end)
                        out += f.format(s) to DateSpan(s, e); d = d.plusWeeks(1)
                    }
                }
                else -> {
                    var m = YearMonth.from(span.start)
                    val f = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
                    while (!m.atDay(1).isAfter(span.end)) {
                        val s = maxOf(m.atDay(1), span.start); val e = minOf(m.atEndOfMonth(), span.end)
                        out += f.format(s) to DateSpan(s, e); m = m.plusMonths(1)
                    }
                }
            }
            return out
        }
    }
}

object ReportCsv {
    /** The same columns as the full export, limited to [span]. */
    fun build(ledger: Ledger, span: DateSpan): String {
        val inSpan = Ledger(ledger.accounts, ledger.categories, ledger.transactions.filter { span.contains(it.occurredAt.toLocalDate()) }, ledger.goals, ledger.bills, ledger.cycle, ledger.today)
        return CsvExport.build(inSpan)
    }
}
