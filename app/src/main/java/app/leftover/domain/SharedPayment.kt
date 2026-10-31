package app.leftover.domain

/** What could be read from text shared alongside a payment screenshot. Either part may be missing. */
data class SharedPayment(val amount: Paise?, val payee: String)

/**
 * Payment apps often share a line of text with the screenshot, such as "Paid ₹500 to Rohan Sharma" or
 * "₹1,250.00 sent to Zepto via UPI". The image itself isn't read: Leftover has no text-recognition library (Google's is
 * closed source), so when there's no text the amount is typed in by hand.
 */
object SharedPaymentParser {
    private val amount = Regex("""(?:₹|\bRs\.?|\bINR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val payee = Regex("""\b(?:paid to|sent to|payment to|paying|to)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,39}?)(?=\s+(?:on|via|using|from|for|upi|at|ref|successfully)\b|[,.!\n]|$)""", RegexOption.IGNORE_CASE)

    fun parse(text: String?): SharedPayment {
        val t = text.orEmpty().replace(' ', ' ').take(500)
        val paise = amount.find(t)?.groupValues?.get(1)?.let(Money::parse)?.takeIf { it in 1..CaptureLink.MAX_PAISE }
        val name = payee.find(t)?.groupValues?.get(1)?.trim()?.trimEnd('.', '-')
            ?.takeIf { it.any(Char::isLetter) && !it.equals("you", true) && !it.equals("your", true) }.orEmpty()
        return SharedPayment(paise, name.take(40))
    }
}
