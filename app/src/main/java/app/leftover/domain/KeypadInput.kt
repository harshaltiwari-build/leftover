package app.leftover.domain

/** Keypad behaviour shared by every amount entry screen. The amount is kept as the digits the user typed. */
object KeypadInput {
    const val DELETE = "del"
    const val DOUBLE_ZERO = "00"
    const val MAX_DIGITS = 7

    fun press(current: String, key: String): String = when (key) {
        DELETE -> current.dropLast(1)
        DOUBLE_ZERO -> if (current.isEmpty()) current else (current + "00").take(MAX_DIGITS)
        else -> when {
            current.length >= MAX_DIGITS -> current
            current == "0" -> key
            else -> current + key
        }
    }

    /** The typed digits as paise (whole rupees only), or 0 when nothing is typed. */
    fun toPaise(digits: String): Paise = (digits.toLongOrNull() ?: 0L) * 100

    /** Digits grouped the Indian way for display, "0" when empty. */
    fun display(digits: String): String = if (digits.isEmpty()) "0" else Money.groupIndian(digits.toLong())
}
