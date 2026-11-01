package app.leftover.domain

/**
 * An icon key for something someone just named, from words in the name: "Chai and coffee" gets the cup, "Uber / Ola"
 * the car. Falls back to "other".
 */
object IconKeys {
    fun guess(name: String): String {
        val n = name.lowercase()
        return guesses.firstOrNull { (_, words) -> words.any { it in n } }?.first ?: "other"
    }

    private val guesses = listOf(
        "coffee" to listOf("coffee", "chai", "tea", "cafe", "café", "starbucks"),
        "groceries" to listOf("grocer", "kirana", "vegetable", "sabzi", "blinkit", "zepto", "bigbasket", "instamart"),
        "food" to listOf("food", "eat", "dinner", "lunch", "breakfast", "restaurant", "swiggy", "zomato", "dining"),
        "transport" to listOf("uber", "ola", "cab", "auto", "metro", "bus", "fuel", "petrol", "diesel", "transport", "travel", "rapido", "train", "flight"),
        "wifi" to listOf("wifi", "wi-fi", "internet", "broadband"),
        "phone" to listOf("phone", "mobile", "recharge"),
        "bolt" to listOf("electric", "power", "light bill", "gas"),
        "home" to listOf("rent", "home", "house", "maintenance"),
        "gym" to listOf("gym", "fitness", "yoga", "sport"),
        "music" to listOf("music", "spotify"),
        "tv" to listOf("netflix", "prime", "hotstar", "ott", "tv"),
        "fun" to listOf("movie", "fun", "subscription", "game", "party", "outing"),
        "shopping" to listOf("shop", "amazon", "myntra", "flipkart", "clothes", "gift"),
        "personal" to listOf("salon", "care", "beauty", "haircut", "medicine", "health", "pharmacy"),
        "savings" to listOf("saving", "invest", "sip", "mutual"),
        "snacks" to listOf("snack", "chips", "sweet"),
    )
}
