package app.leftover.domain

import kotlin.math.abs

/** Amounts are stored as whole paise (₹1 = 100 paise) so money maths never touches floating point. */
typealias Paise = Long

fun rupees(amount: Long): Paise = amount * 100

object Money {
    const val HIDDEN = "₹ ••••"

    /**
     * Formats paise as Indian rupees with lakh/crore grouping, e.g. 123456700 → "₹12,34,567".
     * Paise are rounded to the nearest rupee unless [showPaise] is set.
     */
    fun format(amount: Paise, showPaise: Boolean = false, signed: Boolean = false): String {
        val negative = amount < 0
        val absolute = abs(amount)
        val body = if (showPaise && absolute % 100 != 0L) {
            groupIndian(absolute / 100) + "." + (absolute % 100).toString().padStart(2, '0')
        } else {
            groupIndian((absolute + 50) / 100)
        }
        val prefix = when {
            negative -> "−₹"
            signed && amount > 0 -> "+₹"
            else -> "₹"
        }
        return prefix + body
    }

    /** The formatted amount, or the hidden placeholder when "Hide balances" is on. */
    fun display(amount: Paise, hidden: Boolean, signed: Boolean = false): String = if (hidden) HIDDEN else format(amount, signed = signed)

    fun groupIndian(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val groups = ArrayDeque<String>()
        while (rest.length > 2) {
            groups.addFirst(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)
        return groups.joinToString(",") + "," + lastThree
    }

    /** Paise as text for an input field: "33.33", or "33" when there are no paise. */
    fun toInput(amount: Paise): String =
        if (amount % 100 == 0L) (amount / 100).toString() else "%d.%02d".format(amount / 100, amount % 100)

    /** Parses keypad input such as "1,250.50" into paise; returns null for empty or invalid input. */
    fun parse(input: String): Paise? {
        val clean = input.replace(",", "").trim()
        if (clean.isEmpty()) return null
        val parts = clean.split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1)?.take(2)?.padEnd(2, '0')?.toLongOrNull() ?: 0
        return whole * 100 + fraction
    }
}
