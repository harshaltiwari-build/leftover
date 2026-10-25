package app.leftover.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.leftover.BuildConfig
import app.leftover.data.Snapshot
import app.leftover.data.ThemeMode
import app.leftover.domain.rupees
import app.leftover.ui.UiFormat
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.Segmented
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Type

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val c = Leftover.colors
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text(title, style = Type.h2, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

/** Payday, income type, usual income, and the irregular-income switch. These feed the budget and Left to spend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyRhythmScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onSetPayday: (Int) -> Unit,
    onSetIncome: (String, Long) -> Unit,
    onSetIrregular: (Boolean) -> Unit,
) {
    val c = Leftover.colors
    val s = snapshot.settings
    var income by rememberSaveable(s.usualIncome) { mutableStateOf(if (s.usualIncome > 0) (s.usualIncome / 100).toString() else "") }
    val typed = income.toLongOrNull()?.let(::rupees) ?: 0L
    val types = listOf("Salary", "Pocket money", "Freelance", "A mix")
    SubPage("Money rhythm", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Payday")
            Card(padding = PaddingValues(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..28).forEach { day -> Chip(UiFormat.ordinal(day), s.paydayDay == day, { onSetPayday(day) }) }
                    Chip("Last day", s.paydayDay >= 29, { onSetPayday(31) })
                }
                Text("Your cycle runs from one payday to the day before the next. Short months use their last day.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Income")
            Card(padding = PaddingValues(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { type -> Chip(type, s.incomeType == type, { onSetIncome(type, s.usualIncome) }) }
                }
                Spacer(Modifier.height(14.dp))
                LabeledField("Usual amount each cycle", income, "0", { income = it.filter(Char::isDigit).take(8) }, KeyboardType.Number, prefix = "₹")
                if (typed != s.usualIncome) {
                    Spacer(Modifier.height(12.dp))
                    LButton("Save amount", { onSetIncome(s.incomeType, typed) }, kind = ButtonKind.Lime, height = 48.dp)
                }
                Text("Used to suggest budget limits. It isn't counted as money you have.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            ToggleRow("Irregular income", "Plan on your leanest recent cycle and treat the rest as a buffer. For freelancers and gig work.", s.irregularIncome, onSetIrregular)
        }
    }
}

@Composable
fun AppearanceScreen(snapshot: Snapshot, onBack: () -> Unit, onSetTheme: (ThemeMode) -> Unit, onSetVoiceLanguage: (String) -> Unit) {
    val c = Leftover.colors
    val s = snapshot.settings
    SubPage("Appearance and language", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Theme")
            Card(padding = PaddingValues(16.dp)) {
                Segmented(listOf("Light", "Dark", "System"), s.themeMode.label(), { picked -> onSetTheme(ThemeMode.entries.first { it.label() == picked }) })
                Text("System follows your phone's setting.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Voice entry language")
            Card(padding = PaddingValues(16.dp)) {
                Segmented(listOf("English", "हिन्दी"), if (s.voiceLanguage == "hi-IN") "हिन्दी" else "English", { picked -> onSetVoiceLanguage(if (picked == "English") "en-IN" else "hi-IN") })
                Text(
                    "What Leftover listens for when you say a spend aloud. Hinglish works in both: \"chai pe bees rupaye\". Your phone's speech service does the listening.",
                    style = Type.small, color = c.muted, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** What Leftover is and isn't allowed to do on this phone, read live from Android. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val c = Leftover.colors
    val context = LocalContext.current
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var sms by remember { mutableStateOf(granted(Manifest.permission.RECEIVE_SMS)) }
    var readSms by remember { mutableStateOf(granted(Manifest.permission.READ_SMS)) }
    var notifications by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    var listener by remember { mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) }
    LifecycleResumeEffect(Unit) {
        mic = granted(Manifest.permission.RECORD_AUDIO)
        sms = granted(Manifest.permission.RECEIVE_SMS)
        readSms = granted(Manifest.permission.READ_SMS)
        notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        listener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        onPauseOrDispose { }
    }
    SubPage("Privacy and permissions", onBack) {
        Card(color = c.lavender, bordered = false, padding = PaddingValues(16.dp)) {
            Text("Everything stays on this phone", style = Type.bodyExtra, color = c.lavenderInk)
            Text(
                "Leftover has no accounts, no servers and no internet access at all. You can take any permission back in Android settings.",
                style = Type.caption, color = c.lavenderMuted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            Perm("Internet", "None. Leftover can't send anything anywhere.", "None", true)
            Divider()
            Perm("Microphone", "Only while you tap Voice, to hear a spend you say aloud.", if (mic) "Allowed" else "Not allowed", mic)
            Divider()
            Perm("Receive texts", "Sees new texts from bank-style senders only, never phone numbers. Used for automatic tracking.", if (sms) "Allowed" else "Not allowed", sms)
            Divider()
            Perm("Read texts", "Only when you tap Scan: one pass over the last 30 days of bank texts.", if (readSms) "Allowed" else "Not allowed", readSms)
            Divider()
            Perm("Notifications", "Shows reminders and bank-text results.", if (notifications) "Allowed" else "Not allowed", notifications)
            Divider()
            Perm("Notification access", "Lets Leftover read notices from payment apps you choose. Off unless you turn it on.", if (listener) "On" else "Off", listener)
        }
        LButton("Open Android settings", {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, kind = ButtonKind.Outline, height = 48.dp)
    }
}

@Composable
private fun Perm(title: String, detail: String, status: String, good: Boolean) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = c.ink)
            Text(detail, style = Type.small, color = c.muted)
        }
        Text(status, style = Type.captionBold, color = if (good) c.positive else c.muted)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = Leftover.colors
    SubPage("About Leftover", onBack) {
        Card(padding = PaddingValues(16.dp)) {
            Text("Leftover ${BuildConfig.VERSION_NAME}", style = Type.title, color = c.ink)
            Text("A money app for knowing what's safe to spend. Built for people who earn once a month and want it to last.", style = Type.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            About("Licence", "GNU GPL v3. You can read, change and share it. The full text is the LICENSE file in the project.")
            Divider()
            About("Source code", "Free and open source, with no closed-source libraries. Build it yourself from the project folder.")
            Divider()
            About("Font", "Manrope, under the SIL Open Font Licence.")
            Divider()
            About("Your data", "Never sold, never shared, never uploaded. There is no account to delete because there is no account.")
        }
    }
}

@Composable
private fun About(title: String, text: String) {
    val c = Leftover.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = Type.bodyStrong, color = c.ink)
        Text(text, style = Type.small, color = c.muted)
    }
}

/** A page of its own, with the safe choice first. A backup is one tap away before anything is removed. */
@Composable
fun DeleteDataScreen(snapshot: Snapshot, onBack: () -> Unit, onBackup: () -> Unit, onDelete: () -> Unit) {
    val c = Leftover.colors
    var understood by rememberSaveable { mutableStateOf(false) }
    val backedUp = snapshot.settings.lastBackupAt > 0
    SubPage("Delete all data", onBack) {
        Card(padding = PaddingValues(16.dp)) {
            Text("This removes every account, entry, goal and bill from this phone. It can't be undone.", style = Type.bodyStrong, color = c.ink)
            Text(
                if (backedUp) "You have a backup from ${UiFormat.dayMonth(java.time.Instant.ofEpochMilli(snapshot.settings.lastBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}. Anything since then would be lost."
                else "You haven't made a backup. Save one first if you might want this data again.",
                style = Type.caption, color = c.muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
        LButton("Keep my data", onBack, kind = ButtonKind.Lime)
        LButton("Back up first", onBackup, kind = ButtonKind.Outline)
        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            ToggleRow("I understand this can't be undone", null, understood, { understood = it })
        }
        LButton("Delete everything", onDelete, enabled = understood, kind = ButtonKind.Outline, height = 48.dp)
    }
}
