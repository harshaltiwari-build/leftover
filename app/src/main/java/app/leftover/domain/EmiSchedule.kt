package app.leftover.domain

import app.leftover.data.db.EmiEntity
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/** What is left on an EMI, and what paying some of it off early would change. */
data class EmiSummary(
    val remainingMonths: Int,
    val remainingToPay: Paise,
    /** The part of what is left that is still loan principal; equals [remainingToPay] when no rate is known. */
    val outstanding: Paise,
    val nextDue: LocalDate?,
    val lastDue: LocalDate?,
)

data class Prepayment(val monthsSaved: Int, val interestSaved: Paise, val newRemainingMonths: Int)

object EmiSchedule {
    fun monthlyRate(annualRateBps: Int?): Double = (annualRateBps ?: 0) / 10_000.0 / 12.0

    /** Principal still owed: the value today of the instalments left, at the monthly interest rate. */
    fun outstanding(monthly: Paise, months: Int, annualRateBps: Int?): Paise {
        if (months <= 0) return 0
        val r = monthlyRate(annualRateBps)
        if (r <= 0.0) return monthly * months
        return (monthly * (1 - (1 + r).pow(-months)) / r).roundToLong()
    }

    fun summary(emi: EmiEntity, today: LocalDate): EmiSummary {
        val left = emi.remainingMonths
        if (left == 0) return EmiSummary(0, 0, 0, null, null)
        val next = DueDates.nextUnpaid(emi.dueDay, emi.paidThroughEpochDay?.let(LocalDate::ofEpochDay), today)
        return EmiSummary(
            remainingMonths = left,
            remainingToPay = emi.monthlyAmount * left,
            outstanding = outstanding(emi.monthlyAmount, left, emi.annualRateBps),
            nextDue = next,
            lastDue = next.plusMonths((left - 1).toLong()),
        )
    }

    /**
     * Paying [lump] off now while keeping the monthly amount: the loan ends sooner. Returns null when [lump] is not positive
     * or is more than what is owed.
     */
    fun prepay(emi: EmiEntity, lump: Paise): Prepayment? {
        val left = emi.remainingMonths
        val owed = outstanding(emi.monthlyAmount, left, emi.annualRateBps)
        if (lump <= 0 || lump > owed || left == 0) return null
        val r = monthlyRate(emi.annualRateBps)
        val newOwed = owed - lump
        // The exact (fractional) number of instalments the smaller loan needs; the last one is simply smaller.
        val exactMonths = when {
            newOwed <= 0 -> 0.0
            r <= 0.0 -> newOwed.toDouble() / emi.monthlyAmount
            else -> {
                val x = 1 - r * newOwed / emi.monthlyAmount
                if (x <= 0) left.toDouble() else -ln(x) / ln(1 + r)
            }
        }.coerceAtMost(left.toDouble())
        val newMonths = kotlin.math.ceil(exactMonths - 1e-9).toInt().coerceAtMost(left)
        val interestBefore = emi.monthlyAmount * left - owed
        val interestAfter = (emi.monthlyAmount * exactMonths).roundToLong() - newOwed
        val saved = (interestBefore - interestAfter).coerceAtLeast(0)
        return Prepayment(monthsSaved = left - newMonths, interestSaved = if (r <= 0.0) 0 else saved, newRemainingMonths = newMonths)
    }
}
