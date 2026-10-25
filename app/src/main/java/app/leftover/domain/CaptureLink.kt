package app.leftover.domain

/** An entry another app asked Leftover to record. It is never saved directly: it waits in the review queue. */
data class LinkEntry(val kind: SmsKind, val amount: Paise, val merchant: String, val note: String)

/**
 * Reads `leftover://capture?amount=180&merchant=Blue%20Tokai&note=...&kind=expense|income`.
 * Anything unreasonable is refused so another app can't flood the queue with nonsense.
 */
object CaptureLink {
    const val MAX_PAISE = 10_00_00_000L * 100

    fun parse(amount: String?, merchant: String?, note: String?, kind: String?): LinkEntry? {
        val paise = amount?.let(Money::parse)?.takeIf { it in 1..MAX_PAISE } ?: return null
        val direction = when (kind?.lowercase()) {
            null, "", "expense", "debit" -> SmsKind.DEBIT
            "income", "credit" -> SmsKind.CREDIT
            else -> return null
        }
        return LinkEntry(direction, paise, clean(merchant, 40), clean(note, 80))
    }

    private fun clean(text: String?, max: Int): String = text.orEmpty().filter { !it.isISOControl() }.trim().take(max)
}
