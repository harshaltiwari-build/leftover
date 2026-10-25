package app.leftover.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.leftover.data.Snapshot
import app.leftover.notify.ReminderNotifier
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Banner
import app.leftover.ui.components.Card
import app.leftover.ui.components.Divider
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Type

/**
 * Four reminders, each off until switched on. Turning one on is the moment Android's notification permission is asked for,
 * with the reason on screen first.
 */
@Composable
fun RemindersScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onBills: (Boolean) -> Unit,
    onPayday: (Boolean) -> Unit,
    onBudgets: (Boolean) -> Unit,
    onWeekly: (Boolean) -> Unit,
) {
    val c = Leftover.colors
    val context = LocalContext.current
    val s = snapshot.settings
    var allowed by remember { mutableStateOf(ReminderNotifier.allowed(context)) }
    var denied by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf<(() -> Unit)?>(null) }
    LifecycleResumeEffect(Unit) {
        allowed = ReminderNotifier.allowed(context)
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = ReminderNotifier.allowed(context)
        denied = !granted
        if (granted) waiting?.invoke()
        waiting = null
    }

    fun switch(current: Boolean, set: (Boolean) -> Unit): (Boolean) -> Unit = { wanted ->
        when {
            !wanted -> set(false)
            allowed -> { denied = false; set(true) }
            Build.VERSION.SDK_INT >= 33 && !denied -> { waiting = { set(true) }; ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
            else -> denied = true
        }
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Reminders", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(color = c.lavender, bordered = false, padding = PaddingValues(16.dp)) {
                Text("Only the nudges you pick", style = Type.bodyExtra, color = c.lavenderInk)
                Text(
                    "Everything here is worked out on this phone, so reminders work without internet. No marketing, ever. Android asks you to confirm notifications the first time you switch one on.",
                    style = Type.caption, color = c.lavenderMuted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (denied || ((s.billReminders || s.paydayReminder || s.budgetAlerts || s.weeklyCheckIn) && !allowed)) {
                Banner(
                    "Notifications are off for Leftover, so reminders can't show. Turn them on in Android settings.", c.emberBg, c.emberText,
                    trailing = "Settings",
                    onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                )
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow("Bills due", "A day before, or whatever you set on each bill", s.billReminders, switch(s.billReminders, onBills))
                Divider()
                ToggleRow("Payday", "A note on the day your cycle starts", s.paydayReminder, switch(s.paydayReminder, onPayday))
                Divider()
                ToggleRow("Close to a budget limit", "Once per category per cycle, at 90% used", s.budgetAlerts, switch(s.budgetAlerts, onBudgets))
                Divider()
                ToggleRow("Weekly check-in", "Sunday morning: a look back at the week", s.weeklyCheckIn, switch(s.weeklyCheckIn, onWeekly))
            }
            Text(
                "On the lock screen a reminder only says that something is waiting. Amounts also stay out of reminders when Hide balances is on.",
                style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
