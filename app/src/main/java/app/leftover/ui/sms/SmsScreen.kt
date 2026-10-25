package app.leftover.ui.sms

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.leftover.data.Snapshot
import app.leftover.data.db.TxSource
import app.leftover.domain.UpiApps
import app.leftover.domain.toLocalDate
import app.leftover.sms.ScanResult
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Banner
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.SettingsRow
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Type

private val SCAN_CHOICES = listOf(30, 90)

/** Where automatic tracking is switched on or off, and where each sender and the scan are controlled. */
@Composable
fun SmsScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onSetTracking: (Boolean) -> Unit,
    onSetTrusted: (String, Boolean) -> Unit,
    onScan: (days: Int, onDone: (ScanResult?) -> Unit) -> Unit,
    onOpenReview: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMandates: () -> Unit,
    onSetIgnored: (String, Boolean) -> Unit,
    onAcceptLinks: (Boolean) -> Unit,
    onSetAutoSave: (Boolean) -> Unit,
    onSetNotify: (Boolean) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onSetNotificationApp: (String, Boolean) -> Unit,
    onDeleteData: () -> Unit,
) {
    val c = Leftover.colors
    val context = LocalContext.current
    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    var smsGranted by remember { mutableStateOf(has(Manifest.permission.RECEIVE_SMS)) }
    var notificationsGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)) }
    LifecycleResumeEffect(Unit) {
        smsGranted = has(Manifest.permission.RECEIVE_SMS)
        notificationsGranted = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
        onPauseOrDispose { }
    }
    var listenerAllowed by remember { mutableStateOf(listenerAccess(context)) }
    LifecycleResumeEffect(Unit) {
        listenerAllowed = listenerAccess(context)
        onPauseOrDispose { }
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var scanDays by rememberSaveable { mutableStateOf(30) }
    var scanMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val on = snapshot.settings.smsTracking && smsGranted
    val smsCount = snapshot.transactions.count { it.source == TxSource.SMS }
    val waiting = snapshot.pendingSms.size

    fun startScan() {
        scanning = true
        scanMessage = null
        onScan(scanDays) { result ->
            scanning = false
            scanMessage = when {
                result == null -> "The SMS permission wasn't granted, so nothing was read."
                result.saved + result.queued == 0 -> "Nothing new found in the last $scanDays days."
                else -> "Found ${result.saved + result.queued}: ${result.saved} added, ${result.queued} waiting for review."
            }
        }
    }

    val askToTrack = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        smsGranted = result[Manifest.permission.RECEIVE_SMS] == true || has(Manifest.permission.RECEIVE_SMS)
        notificationsGranted = Build.VERSION.SDK_INT < 33 || result[Manifest.permission.POST_NOTIFICATIONS] == true || has(Manifest.permission.POST_NOTIFICATIONS)
        denied = !smsGranted
        if (smsGranted) onSetTracking(true)
    }
    val askToRead = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startScan() else scanMessage = "The SMS permission wasn't granted, so nothing was read."
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Automatic tracking", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!on) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadList("We read", listOf("Debit and credit alerts", "Card and UPI payments", "Bill and EMI notices"), Modifier.weight(1f), good = true)
                    ReadList("We never read", listOf("OTPs and passwords", "Personal chats", "Promotions"), Modifier.weight(1f), good = false)
                }
            }
            Card(color = c.lavender, bordered = false, padding = PaddingValues(16.dp)) {
                Text("Add bank spends without typing", style = Type.bodyExtra, color = c.lavenderInk)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Leftover reads texts from bank and wallet senders such as HDFCBK, never from phone numbers, and ignores anything that mentions an OTP. " +
                        "The text isn't saved: only the amount, merchant, date and last four digits of the account are kept, on this phone.",
                    style = Type.caption, color = c.lavenderMuted,
                )
            }

            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow("Track from bank texts", if (on) "On" else "Off", on, { wanted ->
                    if (!wanted) onSetTracking(false)
                    else if (smsGranted && notificationsGranted) onSetTracking(true)
                    else askToTrack.launch(
                        buildList {
                            add(Manifest.permission.RECEIVE_SMS)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray(),
                    )
                })
            }
            if (snapshot.settings.smsTracking && !smsGranted) {
                Banner("The SMS permission was turned off in Android settings, so tracking is paused.", c.emberBg, c.emberText)
            }
            if (denied) {
                Banner("Leftover needs the SMS permission to see new bank texts.", c.emberBg, c.emberText, trailing = "Settings", onClick = { openAppSettings(context) })
            }
            if (on && !notificationsGranted) {
                Banner("Notifications are off, so new items only show up inside the app.", c.emberBg, c.emberText, trailing = "Settings", onClick = { openAppSettings(context) })
            }

            if (on) {
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(role = Role.Button, onClick = onOpenReview), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Review queue", style = Type.bodyStrong, color = c.ink)
                            Text(if (waiting == 0) "Nothing waiting" else "$waiting waiting for you", style = Type.small, color = c.muted)
                        }
                        Icon(LeftoverIcons.Chevron, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("How it behaves")
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        ToggleRow("Save when confident", "A clear spend from a known account and merchant is saved without asking. Off means every payment waits for you.", snapshot.settings.smsAutoSave, onSetAutoSave)
                        Divider()
                        ToggleRow("Notify for each payment", "A notification when a payment is saved or needs your help.", snapshot.settings.smsNotify, onSetNotify)
                    }
                    Card(padding = PaddingValues(16.dp)) {
                        Text("What was set aside", style = Type.bodyStrong, color = c.ink)
                        Text("${snapshot.settings.smsSkippedOtp} one-time-password texts ignored · ${snapshot.settings.smsSkippedPromo} promotional texts ignored · 0 texts uploaded", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    val newMandates = snapshot.mandates.count { it.status == app.leftover.data.db.MandateStatus.NEW }
                    SettingsRow("Capture rules", "Always file a merchant under a category", onOpenRules, value = snapshot.rules.size.takeIf { it > 0 }?.toString())
                    Divider()
                    SettingsRow("UPI AutoPay", "Recurring debits found in your texts", onOpenMandates, value = if (newMandates > 0) "$newMandates new" else null, warning = newMandates > 0)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Look back")
                    Card(padding = PaddingValues(16.dp)) {
                        Text("Scan the last $scanDays days", style = Type.bodyStrong, color = c.ink)
                        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SCAN_CHOICES.forEach { days -> app.leftover.ui.components.Chip("$days days", scanDays == days, { scanDays = days }) }
                        }
                        Text(
                            "Reads your inbox once, now. It opens only texts from bank-style senders and skips every text from a phone number. Needs the SMS read permission, asked when you tap.",
                            style = Type.small, color = c.muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        LButton(if (scanning) "Scanning…" else "Scan now", enabled = !scanning, kind = ButtonKind.Outline, height = 48.dp, onClick = {
                            if (has(Manifest.permission.READ_SMS)) startScan() else askToRead.launch(Manifest.permission.READ_SMS)
                        })
                        scanMessage?.let { Text(it, style = Type.caption, color = c.ink, modifier = Modifier.padding(top = 10.dp)) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Senders")
                if (snapshot.smsSenders.isEmpty()) {
                    Card { CenteredHint("Senders appear here after their first bank text arrives.") }
                } else {
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        snapshot.smsSenders.forEachIndexed { index, sender ->
                            if (index > 0) Divider()
                            ToggleRow(
                                title = sender.sender,
                                subtitle = "${sender.seen} ${if (sender.seen == 1) "text" else "texts"} · last ${UiFormat.pastDay(sender.lastSeen.toLocalDate(), snapshot.today)} · " +
                                    if (sender.trusted) "saves clean spends on its own" else "everything waits for review",
                                checked = sender.trusted,
                                onChange = { onSetTrusted(sender.sender, it) },
                            )
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { onSetIgnored(sender.sender, !sender.ignored) }, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (sender.ignored) "Ignored. Texts from this sender are skipped. Listen again" else "Ignore this sender",
                                    style = Type.captionBold, color = if (sender.ignored) c.emberText else c.muted,
                                )
                            }
                        }
                    }
                    Text(
                        "Even when a sender is allowed, a spend is only saved without asking if its account and merchant are already known. Money coming in always waits for you.",
                        style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Payment app notifications")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    ToggleRow(
                        "Read UPI app notifications",
                        "Reads \"Paid ₹250 to…\" notices, only from the apps you tick below. Nothing is saved without your say-so until you trust an app.",
                        snapshot.settings.captureNotifications && listenerAllowed,
                        { wanted ->
                            if (!wanted) onSetNotifications(false)
                            else if (listenerAllowed) onSetNotifications(true)
                            else { onSetNotifications(true); openListenerSettings(context) }
                        },
                    )
                }
                if (snapshot.settings.captureNotifications && !listenerAllowed) {
                    Banner("Android hasn't given Leftover Notification access yet. Turn Leftover on in the list that opens.", c.emberBg, c.emberText, trailing = "Open", onClick = { openListenerSettings(context) })
                }
                if (snapshot.settings.captureNotifications) {
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        UpiApps.known.forEachIndexed { index, upi ->
                            if (index > 0) Divider()
                            ToggleRow(upi.label, if (isInstalled(context, upi.packageName)) "Installed" else "Not installed", upi.packageName in snapshot.settings.notificationApps, { onSetNotificationApp(upi.packageName, it) })
                        }
                    }
                    Text(
                        "Leftover can't see notifications from any app you leave off. For apps you tick, it reads the notification once, keeps only the amount, who it was and the time, and drops the text. If your bank also texts the same payment, you get one entry, not two.",
                        style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Other apps")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    ToggleRow(
                        "Accept entries from other apps",
                        "Tasker, Automate and similar apps can send a spend to Leftover. It waits in your review queue; nothing is added without you.",
                        snapshot.settings.acceptLinks, onAcceptLinks,
                    )
                }
                if (snapshot.settings.acceptLinks) {
                    Text("Link: leftover://capture?amount=180&merchant=Blue%20Tokai&kind=expense", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            if (confirmDelete) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Turn off and delete SMS data?", style = Type.bodyExtra, color = c.ink)
                    Text(
                        "This turns tracking off and removes $smsCount ${if (smsCount == 1) "transaction" else "transactions"} that came from texts, $waiting waiting ${if (waiting == 1) "item" else "items"} and every sender setting. Entries you added yourself stay. This can't be undone.",
                        style = Type.caption, color = c.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LButton("Keep", { confirmDelete = false }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                        LButton("Delete", { confirmDelete = false; onDeleteData() }, modifier = Modifier.weight(1f), height = 48.dp)
                    }
                }
            } else if (snapshot.smsSenders.isNotEmpty() || smsCount > 0 || waiting > 0 || snapshot.settings.smsTracking) {
                DeleteLink(onClick = { confirmDelete = true })
            }

            Text(
                "Android lets you take the SMS permission back at any time in the phone's settings. Google Play restricts the SMS permission, so this feature is meant for builds you install yourself, such as from GitHub or F-Droid.",
                style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ReadList(title: String, items: List<String>, modifier: Modifier, good: Boolean) {
    val c = Leftover.colors
    Card(modifier = modifier, padding = PaddingValues(14.dp)) {
        Text(title, style = Type.bodyStrong, color = if (good) c.positive else c.emberText)
        items.forEach { Text("· $it", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun DeleteLink(onClick: () -> Unit) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("Turn off and delete SMS data", style = Type.bodyStrong, color = c.emberText)
    }
}

private fun listenerAccess(context: android.content.Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun openListenerSettings(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun isInstalled(context: android.content.Context, packageName: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

private fun openAppSettings(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
