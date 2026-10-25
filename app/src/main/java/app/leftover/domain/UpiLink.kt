package app.leftover.domain

import java.net.URLEncoder

/** Builds a `upi://pay` link that opens the user's UPI app with the payee, amount and note already filled in. */
object UpiLink {
    private val validId = Regex("^[a-zA-Z0-9._-]{2,256}@[a-zA-Z0-9]{2,64}$")

    fun isValidId(id: String): Boolean = validId.matches(id.trim())

    /** Returns null when [upiId] isn't a plausible UPI address or the amount isn't positive. */
    fun build(upiId: String, name: String, amount: Paise, note: String): String? {
        val id = upiId.trim()
        if (!isValidId(id) || amount <= 0) return null
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val rupees = "%d.%02d".format(amount / 100, amount % 100)
        return "upi://pay?pa=${enc(id)}&pn=${enc(name.ifBlank { id })}&am=$rupees&cu=INR&tn=${enc(note.take(50))}"
    }
}
