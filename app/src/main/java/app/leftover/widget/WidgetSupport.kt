package app.leftover.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import app.leftover.LeftoverApp
import app.leftover.MainActivity
import app.leftover.ui.DeepLinks

/** Colours for the widgets: one value for light and one for dark, chosen by the system. */
internal object WidgetColors {
    val ink = ColorProvider(day = Color(0xFF20251B), night = Color(0xFFF2F2EA))
    val muted = ColorProvider(day = Color(0xFF5E6357), night = Color(0xFFA9AE9F))
    val onPrimary = ColorProvider(day = Color(0xFFF8F8F2), night = Color(0xFF20251B))
    val onLime = ColorProvider(day = Color(0xFF20251B), night = Color(0xFF20251B))
    val track = ColorProvider(day = Color(0xFFECECE3), night = Color(0xFF2C3326))
    val ember = ColorProvider(day = Color(0xFFA8360A), night = Color(0xFFF6B08A))
    val positive = ColorProvider(day = Color(0xFF3F5A0B), night = Color(0xFFC5E86B))
}

/** Lets the quick-entry sheet hand over to the full app. */
object WidgetIntents {
    fun openApp(context: Context, route: String): Intent = Widgets.open(context, route)
}

internal object Widgets {
    /** An intent that opens Leftover on a specific screen, for example `add?kind=Income` or `voice`. */
    fun open(context: Context, route: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.uri(route)), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The quick-entry sheet over the launcher, or the app itself when app lock is on. */
    fun add(context: Context, kind: String, locked: Boolean): Intent =
        if (locked) open(context, app.leftover.ui.Routes.add(kind))
        else Intent(context, app.leftover.QuickEntryActivity::class.java).putExtra(app.leftover.QuickEntryActivity.KIND, kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    suspend fun refreshAll(context: Context) {
        LeftoverWidget().updateAll(context)
        QuickLogWidget().updateAll(context)
    }

    suspend fun model(context: Context): WidgetModel {
        val app = context.applicationContext as LeftoverApp
        return WidgetModel.from(app.repository.currentSnapshot())
    }
}

/** The last entry logged from a widget, so a short-lived "Undo" can be offered. */
object LastLogged {
    private const val FILE = "widget_state"
    private const val FRESH_MILLIS = 10 * 60 * 1000L

    data class Entry(val id: Long, val label: String, val amount: Long, val at: Long)

    fun save(context: Context, entry: Entry) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("id", entry.id).putString("label", entry.label).putLong("amount", entry.amount).putLong("at", entry.at).apply()
    }

    fun clear(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()

    /** The entry if it was logged in the last ten minutes, otherwise null. */
    fun fresh(context: Context, now: Long = System.currentTimeMillis()): Entry? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val id = prefs.getLong("id", -1L)
        val at = prefs.getLong("at", 0L)
        if (id < 0 || now - at > FRESH_MILLIS) return null
        return Entry(id, prefs.getString("label", "") ?: "", prefs.getLong("amount", 0L), at)
    }
}
