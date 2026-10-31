package app.leftover

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.leftover.data.ThemeMode
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.ui.Routes
import app.leftover.ui.add.QuickAddScreen
import app.leftover.widget.WidgetIntents
import app.leftover.ui.theme.LeftoverTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Quick entry from a widget, as a sheet over the launcher: type the amount, pick a category, save, and you're back on
 * the home screen. The whole app never opens. With app lock on the widget opens the app instead, so this never
 * shows anything a lock should cover.
 */
class QuickEntryActivity : ComponentActivity() {
    companion object { const val KIND = "kind" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LeftoverApp
        val snapshots = app.repository.snapshot.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        val kind = intent.getStringExtra(KIND)?.takeIf { it == "Expense" || it == "Income" } ?: "Expense"
        setContent {
            val snapshot by snapshots.collectAsState()
            val data = snapshot
            LeftoverTheme(data?.settings?.themeMode ?: ThemeMode.SYSTEM, data?.settings?.pureBlack == true) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Close") { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (data == null) return@Box
                    if (!data.settings.onboarded || data.settings.appLock) {
                        // Not set up yet, or locked since the widget was drawn: hand over to the app.
                        LaunchedEffect(Unit) {
                            startActivity(WidgetIntents.openApp(this@QuickEntryActivity, Routes.add(kind)))
                            finish()
                        }
                        return@Box
                    }
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(0.94f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .clickable(remember { MutableInteractionSource() }, indication = null) {},
                    ) {
                        QuickAddScreen(
                            initialKind = kind,
                            snapshot = data,
                            onClose = { finish() },
                            onSave = { tx, message, tags ->
                                lifecycleScope.launch {
                                    val id = app.repository.addTransaction(tx)
                                    if (tags.isNotEmpty()) app.repository.setTransactionTags(id, tags)
                                    Toast.makeText(this@QuickEntryActivity, "${message.title} · ${message.subtitle}", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            },
                            onAddTag = { name -> lifecycleScope.launch { app.repository.addTag(name, "lavender", null) } },
                            onAddCategory = { name, k -> lifecycleScope.launch { app.repository.addCategory(CategoryEntity(name = name, icon = "other", color = if (k == CategoryKind.INCOME) "lime" else "grey", kind = k)) } },
                            onSaveTemplate = { t -> lifecycleScope.launch { app.repository.addTemplate(t) } },
                            onDeleteTemplate = { id -> lifecycleScope.launch { app.repository.deleteTemplate(id) } },
                        )
                    }
                }
            }
        }
    }
}
