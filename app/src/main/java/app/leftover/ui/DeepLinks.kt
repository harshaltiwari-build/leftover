package app.leftover.ui

/**
 * Links that open a specific screen: `leftover://add?kind=Income`, `leftover://voice`, `leftover://home`, `leftover://review`.
 * Home-screen widgets use them, and any launcher shortcut can too.
 */
object DeepLinks {
    const val SCHEME = "leftover"
    private val kinds = setOf("Expense", "Income", "Transfer")

    fun uri(route: String): String = "$SCHEME://$route"

    /** Maps a link's host and `kind` query value to an in-app route, or null when it isn't one of ours. */
    fun toRoute(scheme: String?, host: String?, kind: String?): String? {
        if (scheme != SCHEME) return null
        return when (host) {
            "add" -> Routes.add(kind?.takeIf { it in kinds } ?: "Expense")
            "voice" -> Routes.VOICE
            "home" -> Routes.HOME
            "review" -> Routes.REVIEW
            "mandates" -> Routes.MANDATES
            "bills" -> Routes.BILLS
            "plan" -> Routes.PLAN
            "checkin" -> Routes.CHECKIN
            "afford" -> Routes.AFFORD
            "reports" -> Routes.REPORTS
            "ask" -> Routes.ASK
            "insights" -> Routes.INSIGHTS
            "activity" -> Routes.ACTIVITY
            "backup" -> Routes.BACKUP
            else -> null
        }
    }
}
