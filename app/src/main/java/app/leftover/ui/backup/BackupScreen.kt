package app.leftover.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.leftover.backup.AutoBackup
import app.leftover.data.Snapshot
import app.leftover.domain.CsvExport
import app.leftover.ui.RestoreOutcome
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.SettingsRow
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Asking { NONE, NEW_PASSPHRASE, OPEN_PASSPHRASE }

/**
 * Everything about keeping your data safe: a status you can trust, backing up now or on a schedule, protecting backups with a
 * passphrase, restoring, and moving data in a plain CSV. Nothing here talks to the internet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onWriteBackup: (Uri, (String?) -> Unit) -> Unit,
    onReadRestore: (Uri, (RestoreOutcome) -> Unit) -> Unit,
    onRestoreEncrypted: (String, CharArray, (RestoreOutcome) -> Unit) -> Unit,
    onSetPassphrase: (CharArray, () -> Unit) -> Unit,
    onClearPassphrase: () -> Unit,
    onAutoBackup: (Boolean, String?, Int) -> Unit,
    onRestored: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val c = Leftover.colors
    val context = LocalContext.current
    val settings = snapshot.settings
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var asking by rememberSaveable { mutableStateOf(Asking.NONE) }
    var dialogError by rememberSaveable { mutableStateOf<String?>(null) }
    var working by rememberSaveable { mutableStateOf(false) }
    var encryptedText by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var confirmUnprotect by rememberSaveable { mutableStateOf(false) }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) onWriteBackup(uri) { problem -> message = problem ?: "Backup saved." }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            working = true
            onReadRestore(uri) { outcome ->
                working = false
                when (outcome) {
                    RestoreOutcome.Done -> onRestored()
                    is RestoreOutcome.NeedsPassphrase -> { encryptedText = outcome.text; dialogError = null; asking = Asking.OPEN_PASSPHRASE }
                    is RestoreOutcome.Failed -> message = outcome.message
                }
            }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            onAutoBackup(true, uri.toString(), settings.backupEveryDays)
            message = "Automatic backups will go to that folder."
        }
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Backup and restore", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Status: the one thing to know at a glance.
            val never = settings.lastBackupAt == 0L
            Column(
                Modifier.fillMaxWidth().clip(Radius.lg).background(if (never) c.emberBg else c.lavender).padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (never) "You haven't backed up yet" else "Last backup ${describe(settings.lastBackupAt)}",
                    style = Type.bodyExtra, color = if (never) c.emberText else c.lavenderInk,
                )
                Text(
                    when {
                        never -> "If this phone is lost, so is your data."
                        settings.backupProtected -> "Protected with your passphrase."
                        else -> "Saved as a plain file. Anyone who gets it can read it."
                    },
                    style = Type.caption, color = if (never) c.emberText else c.lavenderMuted,
                )
                settings.lastBackupError?.let { Text("The last automatic backup didn't work: $it", style = Type.captionBold, color = c.emberText) }
            }
            message?.let { note ->
                Text(note, style = Type.caption, color = c.ink, modifier = Modifier.fillMaxWidth().clip(Radius.md).background(c.surface).padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }

            LButton(
                "Back up now", kind = ButtonKind.Lime,
                onClick = { saveFile.launch("leftover-backup-${LocalDate.now()}" + if (settings.backupProtected) ".leftover" else ".json") },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Protection")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    ToggleRow(
                        "Protect backups with a passphrase",
                        if (settings.backupProtected) "On. Backup files are scrambled and can't be read without it." else "Off. Backup files are plain text.",
                        settings.backupProtected,
                        { wanted -> if (wanted) { dialogError = null; asking = Asking.NEW_PASSPHRASE } else confirmUnprotect = true },
                    )
                }
                if (confirmUnprotect) {
                    Card(padding = PaddingValues(16.dp)) {
                        Text("Stop protecting new backups?", style = Type.bodyExtra, color = c.ink)
                        Text("New backups will be plain files. Backups you already made keep their passphrase.", style = Type.caption, color = c.muted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LButton("Keep protection", { confirmUnprotect = false }, modifier = Modifier.weight(1f), height = 48.dp)
                            LButton("Turn off", { confirmUnprotect = false; onClearPassphrase(); message = "New backups are no longer protected." }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                        }
                    }
                }
                Text("Leftover has no account, so it can't reset a forgotten passphrase. Write it down somewhere safe.", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Automatic backup")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    ToggleRow(
                        "Back up automatically",
                        if (settings.autoBackup) "Saves a new file to your folder every ${if (settings.backupEveryDays >= 30) "month" else "week"}." else "Saves a file to a folder you choose. Nothing leaves your phone.",
                        settings.autoBackup,
                        { wanted ->
                            if (!wanted) onAutoBackup(false, settings.backupFolder, settings.backupEveryDays)
                            else if (settings.backupFolder != null) onAutoBackup(true, settings.backupFolder, settings.backupEveryDays)
                            else pickFolder.launch(null)
                        },
                    )
                    if (settings.autoBackup || settings.backupFolder != null) {
                        Divider()
                        SettingsRow("Folder", "Tap to choose a different one", { pickFolder.launch(null) }, value = settings.backupFolder?.let(::folderName) ?: "Not chosen", warning = settings.backupFolder == null)
                        Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("How often", style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                            Chip("Weekly", settings.backupEveryDays < 30, { onAutoBackup(settings.autoBackup, settings.backupFolder, 7) })
                            Chip("Monthly", settings.backupEveryDays >= 30, { onAutoBackup(settings.autoBackup, settings.backupFolder, 30) })
                        }
                    }
                }
                if (settings.autoBackup && !settings.backupProtected) {
                    Text("These files are plain text. Turn on the passphrase above to protect them.", style = Type.captionBold, color = c.emberText, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Text("Keeps the newest ${AutoBackup.KEEP} files. Android decides the exact time, usually when the phone is idle.", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Restore")
                if (confirmRestore) {
                    Card(padding = PaddingValues(16.dp)) {
                        Text("Replace everything with a backup?", style = Type.bodyExtra, color = c.ink)
                        Text("Everything on this phone is replaced by what's in the file. If the file can't be read, nothing changes. Back up first if you may want today's data.", style = Type.caption, color = c.muted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LButton("Cancel", { confirmRestore = false }, modifier = Modifier.weight(1f), height = 48.dp)
                            LButton("Choose file", { confirmRestore = false; pickFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                        }
                    }
                } else {
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                        SettingsRow("Restore from a file", "A backup made by Leftover, protected or not", { confirmRestore = true; message = null })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Move your data")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    SettingsRow("Export as CSV", "Every transaction, to open in a spreadsheet", {
                        val file = File(context.cacheDir, "leftover-export.csv").apply { writeText(CsvExport.build(snapshot.ledger)) }
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Export transactions"))
                    })
                    Divider()
                    SettingsRow("Import from a CSV", "A bank statement or another app's export", onOpenImport)
                }
            }
            Text("Not included in backups: bank-text senders and waiting items, and the settings that belong to this phone (tracking switch, backup folder).", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    when (asking) {
        Asking.NEW_PASSPHRASE -> PassphraseDialog(
            title = "Choose a passphrase",
            message = "Pick one you'll remember. Leftover has no account, so we can't reset it for you. Without it, protected backups can't be opened.",
            confirmLabel = "Protect backups", creating = true, error = dialogError, busy = working,
            onSubmit = { pass ->
                working = true
                onSetPassphrase(pass) { working = false; asking = Asking.NONE; message = "Backups are now protected with your passphrase." }
            },
            onCancel = { asking = Asking.NONE },
        )
        Asking.OPEN_PASSPHRASE -> PassphraseDialog(
            title = "This backup is protected",
            message = "Enter the passphrase you chose when you made it.",
            confirmLabel = "Restore", creating = false, error = dialogError, busy = working,
            onSubmit = { pass ->
                working = true
                onRestoreEncrypted(encryptedText.orEmpty(), pass) { outcome ->
                    working = false
                    when (outcome) {
                        RestoreOutcome.Done -> { asking = Asking.NONE; onRestored() }
                        is RestoreOutcome.Failed -> dialogError = outcome.message
                        is RestoreOutcome.NeedsPassphrase -> Unit
                    }
                }
            },
            onCancel = { asking = Asking.NONE; encryptedText = null },
        )
        Asking.NONE -> Unit
    }
}

private val whenFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

private fun describe(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(whenFormat)

/** The last part of a folder's address, which is the name people recognise. */
private fun folderName(uri: String): String = Uri.decode(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "Chosen folder" }
