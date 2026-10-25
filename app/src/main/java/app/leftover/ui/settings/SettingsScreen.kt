package app.leftover.ui.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.leftover.BuildConfig
import app.leftover.data.Snapshot
import app.leftover.data.ThemeMode
import app.leftover.ui.UiFormat
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Banner
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.SettingsRow
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.lock.AppLock
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.Duration
import java.time.Instant

/** Where each Settings row goes. Kept together so the page itself stays readable. */
data class SettingsActions(
    val onBack: () -> Unit,
    val onSetName: (String) -> Unit,
    val onSetHide: (Boolean) -> Unit,
    val onSetAppLock: (Boolean) -> Unit,
    val onSetHideInSwitcher: (Boolean) -> Unit,
    val onOpenAccounts: () -> Unit,
    val onOpenCategories: () -> Unit,
    val onOpenTags: () -> Unit,
    val onOpenRhythm: () -> Unit,
    val onOpenBills: () -> Unit,
    val onOpenSms: () -> Unit,
    val onOpenReminders: () -> Unit,
    val onOpenBackup: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onOpenAppearance: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenDelete: () -> Unit,
)

/**
 * The Settings page: who you are and what is hidden, then what you set up, what runs by itself, what keeps your
 * data safe, and how the app looks. Everything deeper lives on its own screen, one tap away.
 */
@Composable
fun SettingsScreen(snapshot: Snapshot, actions: SettingsActions) {
    val c = Leftover.colors
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val s = snapshot.settings
    var name by remember(s.name) { mutableStateOf(s.name) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var smsGranted by remember { mutableStateOf(has(context, Manifest.permission.RECEIVE_SMS)) }
    LifecycleResumeEffect(Unit) {
        smsGranted = has(context, Manifest.permission.RECEIVE_SMS)
        onPauseOrDispose { }
    }

    val liveAccounts = snapshot.accounts.count { !it.archived }
    val reminders = listOf(s.billReminders, s.paydayReminder, s.budgetAlerts, s.weeklyCheckIn).count { it }
    val backupAgeDays = if (s.lastBackupAt == 0L) null else Duration.between(Instant.ofEpochMilli(s.lastBackupAt), Instant.now()).toDays()
    val backupNeedsAttention = backupAgeDays == null || backupAgeDays > 30

    fun toggleLock(wanted: Boolean) {
        val activity = activity ?: return
        lockMessage = null
        if (wanted && !AppLock.available(context)) {
            lockMessage = "Set a screen lock or fingerprint in Android settings first. Leftover uses it to unlock."
            return
        }
        AppLock.prompt(
            activity, if (wanted) "Turn on app lock" else "Turn off app lock", "Confirm it's you",
            onSuccess = { actions.onSetAppLock(wanted) }, onFailure = { lockMessage = it },
        )
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(actions.onBack)
            Text("Settings", style = Type.h2, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Card(padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.padding(top = 26.dp).size(52.dp).clip(Radius.pill).background(c.lavender), contentAlignment = Alignment.Center) {
                        Text(name.ifBlank { "?" }.take(1).uppercase(), style = Type.h2, color = c.lavenderInk)
                    }
                    Column(Modifier.weight(1f)) {
                        LabeledField("Your name", name, "Shown on Home", { name = it.take(24) }, KeyboardType.Text, capitalize = true)
                        if (name.trim() != s.name) {
                            Spacer(Modifier.height(10.dp))
                            LButton("Save name", { actions.onSetName(name.trim()) }, kind = ButtonKind.Lime, height = 44.dp)
                        } else {
                            Text("Shown on Home. Never leaves this phone.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                        }
                    }
                }
            }

            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow("Hide balances", "Show ₹ •••• instead of amounts, everywhere in the app, widgets, notifications and shared recaps.", s.hideBalances, actions.onSetHide)
                Divider()
                ToggleRow("App lock", "Ask for your fingerprint, face or screen lock when you open Leftover.", s.appLock, ::toggleLock)
                Divider()
                ToggleRow("Hide in the app switcher", "Blank preview in recent apps. On Android 12 and older this also blocks screenshots.", s.hideInSwitcher, actions.onSetHideInSwitcher)
            }
            lockMessage?.let {
                Banner(it, c.emberBg, c.emberText, trailing = "Settings", onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            }

            Group("Your money") {
                SettingsRow("Accounts and cards", "Banks, cash wallet, credit cards", actions.onOpenAccounts, value = UiFormat.count(liveAccounts, "account"))
                Divider()
                SettingsRow("Categories", "Names, colours, visibility", actions.onOpenCategories)
                Divider()
                SettingsRow("Tags and events", "Trips, weddings, occasions", actions.onOpenTags, value = snapshot.tags.size.takeIf { it > 0 }?.toString())
                Divider()
                SettingsRow("Money rhythm", "Payday and income", actions.onOpenRhythm, value = "Payday ${UiFormat.ordinal(s.paydayDay)}")
                Divider()
                SettingsRow("Bills and plans", "Bills, subscriptions and EMIs", actions.onOpenBills)
            }

            Group("Add entries automatically") {
                val on = s.smsTracking || s.captureNotifications
                SettingsRow(
                    "Automatic tracking", "Add spends from bank texts and payment apps",
                    actions.onOpenSms,
                    value = when {
                        s.smsTracking && !smsGranted -> "Paused"
                        on -> "On"
                        else -> "Off"
                    },
                    warning = s.smsTracking && !smsGranted,
                )
            }

            Group("Reminders") {
                SettingsRow("Reminders", "Bills due, payday, budgets and the weekly check-in", actions.onOpenReminders, value = if (reminders == 0) "Off" else "$reminders on")
            }

            Group("Keep your data safe") {
                SettingsRow(
                    "Backup and restore",
                    when {
                        backupAgeDays == null -> "You haven't backed up yet. If this phone is lost, so is your data."
                        backupAgeDays > 30 -> "Your last backup is $backupAgeDays days old."
                        else -> "Back up, protect and restore your data"
                    },
                    actions.onOpenBackup,
                    value = when {
                        backupAgeDays == null -> "Never"
                        backupAgeDays == 0L -> "Today"
                        else -> "${backupAgeDays}d ago"
                    },
                    warning = backupNeedsAttention,
                )
                Divider()
                SettingsRow("Privacy and permissions", "What Leftover can and can't access", actions.onOpenPrivacy)
            }

            Group("Look and language") {
                SettingsRow("Appearance and language", "Theme and voice language", actions.onOpenAppearance, value = s.themeMode.label())
                Divider()
                SettingsRow("About Leftover", "Version, licence, credits", actions.onOpenAbout, value = BuildConfig.VERSION_NAME)
            }

            Text(
                "Delete all data", style = Type.bodyStrong, color = c.emberText,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = actions.onOpenDelete).padding(horizontal = 4.dp, vertical = 12.dp),
            )
            Text("Removes everything from this phone.", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) { content() }
    }
}

internal fun ThemeMode.label() = when (this) { ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.SYSTEM -> "System" }

private fun has(context: android.content.Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
