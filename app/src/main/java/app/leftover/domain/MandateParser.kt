package app.leftover.domain

import java.time.LocalDate

enum class MandateKind {
    /** The user just set up a recurring debit. */
    CREATED,

    /** A heads-up that a recurring debit is coming. */
    UPCOMING,
}

/** A recurring UPI debit (AutoPay) seen in a text. Only what the text says is kept. */
data class MandateEvent(
    val kind: MandateKind,
    val merchant: String,
    val amount: Paise,
    /** "monthly", "weekly", "yearly", "daily" or "" when the text doesn't say. */
    val frequency: String,
    val nextDebit: LocalDate?,
)

/**
 * Spots UPI AutoPay mandates and their pre-debit notices so forgotten recurring debits can be listed and cancelled.
 * Like the rest of the SMS reading, it prefers returning null to guessing.
 */
object MandateParser {
    private val created = Regex("(?i)(mandate (?:has been |is |was )?(?:successfully )?(?:created|registered|set ?up|approved|activated)|auto-?pay (?:has been |is |was )?(?:successfully )?(?:set ?up|registered|created|activated|enabled))")
    private val upcoming = Regex("(?i)(will be (?:debited|deducted|charged)|pre-?debit|upcoming (?:auto-?pay|mandate|debit)|auto-?pay (?:payment )?(?:due|scheduled))")
    private val amount = Regex("(?i)(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val frequency = Regex("(?i)\\b(daily|weekly|fortnightly|monthly|quarterly|half-?yearly|yearly|annual(?:ly)?)\\b")
    private val forMerchant = Regex("(?i)\\b(?:for|to|towards|with|at)\\s+([A-Za-z0-9][A-Za-z0-9 &'*_@-]{1,40}?)(?=\\s+(?:on|via|using|ref|upi|from|a/c|ac|frequency|every|starting|valid|if|not|call)\\b|[.,(]|$)")
    private val date = Regex("(?i)\\b(\\d{1,2})[-/ ]([A-Za-z]{3,9}|\\d{1,2})[-/ ](\\d{2,4})\\b")
    private val notAName = Regex("(?i)^(your|a/?c|acct?|account|the|upi|rs|inr|mandate|autopay|auto-pay|this|you|bank)\\b")

    fun parse(body: String): MandateEvent? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (Regex("(?i)\\b(otp|one[- ]time|verification code)\\b").containsMatchIn(text)) return null
        val kind = when {
            created.containsMatchIn(text) -> MandateKind.CREATED
            upcoming.containsMatchIn(text) -> MandateKind.UPCOMING
            else -> return null
        }
        val value = amount.find(text)?.groupValues?.get(1)?.let(Money::parse)?.takeIf { it > 0 } ?: return null
        val merchant = forMerchant.findAll(text).map { SmsParser.cleanMerchant(it.groupValues[1]) }.firstOrNull { it.isNotBlank() && !notAName.containsMatchIn(it) } ?: ""
        if (merchant.isBlank()) return null
        val every = frequency.find(text)?.groupValues?.get(1)?.lowercase()?.let {
            when {
                it.startsWith("annual") || it == "yearly" -> "yearly"
                it.startsWith("half") -> "half-yearly"
                else -> it
            }
        } ?: ""
        return MandateEvent(kind, merchant, value, every, findDate(text))
    }

    private fun findDate(text: String): LocalDate? {
        val m = date.find(text) ?: return null
        val day = m.groupValues[1].toIntOrNull() ?: return null
        val monthText = m.groupValues[2]
        val month = monthText.toIntOrNull() ?: months.indexOf(monthText.take(3).lowercase()).takeIf { it >= 0 }?.plus(1) ?: return null
        var year = m.groupValues[3].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

}
