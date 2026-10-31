package app.leftover

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.leftover.ui.DeepLinks

/** Long-press the launcher icon: add an expense or income, speak one, or open your reports. Built in code so forks don't hard-code the package name. */
object Shortcuts {
    private data class Entry(val id: String, val short: String, val long: String, val icon: Int, val route: String)

    private val entries = listOf(
        Entry("add-expense", "Expense", "Add an expense", R.drawable.ic_shortcut_expense, "add?kind=Expense"),
        Entry("add-income", "Income", "Add income", R.drawable.ic_shortcut_income, "add?kind=Income"),
        Entry("voice", "Say it", "Add by voice", R.drawable.ic_shortcut_voice, "voice"),
        Entry("reports", "Reports", "Spending reports", R.drawable.ic_shortcut_reports, "reports"),
    )

    fun publish(context: Context) {
        val shortcuts = entries.mapIndexed { rank, e ->
            ShortcutInfoCompat.Builder(context, e.id)
                .setShortLabel(e.short)
                .setLongLabel(e.long)
                .setIcon(IconCompat.createWithResource(context, e.icon))
                .setRank(rank)
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.uri(e.route)), context, MainActivity::class.java))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
