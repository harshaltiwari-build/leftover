package app.leftover.domain

/** Compares the cash you counted with what the app thinks you have. */
sealed interface CashCount {
    data object Matches : CashCount
    /** Less cash than expected: the difference was spent without being logged. */
    data class Missing(val amount: Paise) : CashCount
    /** More cash than expected: money came in that was never logged. */
    data class Extra(val amount: Paise) : CashCount

    companion object {
        fun compare(expected: Paise, counted: Paise): CashCount = when {
            counted == expected -> Matches
            counted < expected -> Missing(expected - counted)
            else -> Extra(counted - expected)
        }
    }
}
