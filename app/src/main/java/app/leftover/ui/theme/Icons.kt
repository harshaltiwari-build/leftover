package app.leftover.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Leftover's outline icon set: 24-unit grid, 1.8 stroke, round caps. Paths match the design canvas,
 * so the app and the design stay in sync. Tint with Icon(tint = …).
 */
object LeftoverIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

    private fun icon(name: String, vararg paths: String, stroke: Float = 1.8f): ImageVector {
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Home by lazy { icon("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val List by lazy { icon("list", "M9 6h12M9 12h12M9 18h12M4 6h.01M4 12h.01M4 18h.01") }
    val Target by lazy { icon("target", circle(12f, 12f, 9f), circle(12f, 12f, 5f), circle(12f, 12f, 1f)) }
    val People by lazy { icon("people", circle(9f, 8f, 3.5f), "M2.5 20a6.5 6.5 0 0 1 13 0", "M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5a6.5 6.5 0 0 1 3.5 5.5") }
    val Repeat by lazy { icon("repeat", "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3", stroke = 2f) }
    val Import by lazy { icon("import", "M12 3v12M7 10l5 5 5-5M4 19h16", stroke = 2f) }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14", stroke = 2.2f) }
    val Minus by lazy { icon("minus", "M5 12h14", stroke = 2.2f) }
    val Back by lazy { icon("back", "m15 6-6 6 6 6", stroke = 2f) }
    val Close by lazy { icon("close", "M6 6l12 12M18 6 6 18", stroke = 2f) }
    val Chevron by lazy { icon("chevron", "m9 6 6 6-6 6", stroke = 2f) }
    val ChevronDown by lazy { icon("chevron_down", "m6 9 6 6 6-6", stroke = 2f) }
    val Arrow by lazy { icon("arrow", "M5 12h14M13 6l6 6-6 6", stroke = 2f) }
    val Eye by lazy { icon("eye", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", circle(12f, 12f, 3f)) }
    val EyeOff by lazy { icon("eye_off", "M3 3l18 18M10.6 5.1A10 10 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4.2M6.6 6.6A17 17 0 0 0 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2") }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0") }
    val Search by lazy { icon("search", circle(11f, 11f, 7f), "m20 20-3.5-3.5", stroke = 2f) }
    val Check by lazy { icon("check", "m5 12 5 5 9-10", stroke = 2.2f) }
    val Calendar by lazy { icon("calendar", "M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M3 10h18M8 3v4M16 3v4") }
    val Info by lazy { icon("info", circle(12f, 12f, 9f), "M12 11v5M12 8h.01") }
    val Trend by lazy { icon("trend", "M3 17l6-6 4 4 8-8M14 7h7v7", stroke = 2f) }
    val Camera by lazy { icon("camera", "M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", circle(12f, 13.5f, 3.5f)) }
    val Mic by lazy { icon("mic", "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0z", "M5 11a7 7 0 0 0 14 0M12 18v3") }
    val Lock by lazy { icon("lock", "M4 13a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", "M8 11V7a4 4 0 0 1 8 0v4") }
    val Settings by lazy { icon("settings", "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12", circle(16f, 6f, 2f), circle(10f, 12f, 2f), circle(18f, 18f, 2f)) }
    val Clock by lazy { icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }
    val Delete by lazy { icon("delete", "M21 5H9l-6 7 6 7h12z", "m16 9-5 6M11 9l5 6") }
    val Trash by lazy { icon("trash", "M4 7h16", "M10 11v6M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3") }
    val Download by lazy { icon("download", "M12 15V3M7 10l5 5 5-5M5 21h14") }
    val Wallet by lazy { icon("wallet", "M3 7a2 2 0 0 1 2-2h13v4", "M3 9a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M16 13.5h.01") }

    // Categories and money moves
    val Food by lazy { icon("food", "M3 11h18a9 9 0 0 1-18 0z", "M8 7c0-1.5 1.5-1.5 1.5-3M12.5 7c0-1.5 1.5-1.5 1.5-3") }
    val Coffee by lazy { icon("coffee", "M4 9h13v4a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z", "M17 10h1.5a2.5 2.5 0 0 1 0 5H17M8 3v3M12 3v3") }
    val Groceries by lazy { icon("groceries", "M3 4h2l2.4 10.5h10.2L20 7H6.3", circle(9f, 19f, 1.5f), circle(17f, 19f, 1.5f)) }
    val Transport by lazy { icon("transport", "M5 16v-4l2-5h10l2 5v4", "M3 16h18v3H3z", "M7.5 12h9") }
    val Shopping by lazy { icon("shopping", "M5 8h14l-1 13H6z", "M9 8V6a3 3 0 0 1 6 0v2") }
    val Fun by lazy { icon("fun", "M3 7h18v3a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4z", "M14 7v10") }
    val Personal by lazy { icon("personal", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z") }
    val Snacks by lazy { icon("snacks", circle(12f, 12f, 9f), "M9 9h.01M14.5 8.5h.01M15 13.5h.01M9.5 14.5h.01M12 11.5h.01") }
    val Other by lazy { icon("other", "M5 12h.01M12 12h.01M19 12h.01", stroke = 2.6f) }
    val Transfer by lazy { icon("transfer", "M4 8h14l-3-3M20 16H6l3 3") }
    val MoneyBack by lazy { icon("moneyback", "M17 7 7 17M7 9v8h8") }
    val Income by lazy { icon("income", "M12 4v12M6 10l6 6 6-6M5 20h14") }
    val Refund by lazy { icon("refund", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5") }
    val Bank by lazy { icon("bank", "M3 10 12 4l9 6M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18") }
    val Cash by lazy { icon("cash", "M2 8a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z", circle(12f, 12f, 2.5f)) }
    val Card by lazy { icon("card", "M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M3 10h18M7 15h3") }
    val Gym by lazy { icon("gym", "M6 7v10M18 7v10M3 10v4M21 10v4M6 12h12") }
    val Tv by lazy { icon("tv", "M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M8 21h8M12 17v4") }
    val Music by lazy { icon("music", "M9 18V5l11-2v13", circle(6f, 18f, 3f), circle(17f, 16f, 3f)) }
    val Phone by lazy { icon("phone", "M7 4a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2z", "M11 18h2") }
    val Bolt by lazy { icon("bolt", "M13 2 4 14h7l-1 8 9-12h-7z") }
    val Wifi by lazy { icon("wifi", "M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M2 9a15 15 0 0 1 20 0M12 19.5h.01") }
    val Savings by lazy { icon("savings", circle(12f, 12f, 9f), circle(12f, 12f, 5f), circle(12f, 12f, 1f)) }

    /** Looks up an icon by the key stored on categories and bills. */
    fun forKey(key: String): ImageVector = when (key) {
        "food" -> Food
        "coffee" -> Coffee
        "groceries" -> Groceries
        "transport" -> Transport
        "shopping" -> Shopping
        "fun" -> Fun
        "personal" -> Personal
        "snacks" -> Snacks
        "transfer" -> Transfer
        "moneyback" -> MoneyBack
        "income" -> Income
        "refund" -> Refund
        "bank" -> Bank
        "cash" -> Cash
        "card" -> Card
        "gym" -> Gym
        "tv" -> Tv
        "music" -> Music
        "phone" -> Phone
        "bolt" -> Bolt
        "wifi" -> Wifi
        "home" -> Home
        "calendar" -> Calendar
        "savings" -> Savings
        else -> Other
    }
}
