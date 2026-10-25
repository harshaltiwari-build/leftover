package app.leftover.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.MandateEntity
import app.leftover.data.db.MandateStatus
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.LButton
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate

/** Recurring UPI debits (AutoPay) found in bank texts, so a forgotten one can be tracked or cancelled. */
@Composable
fun MandatesScreen(snapshot: Snapshot, onBack: () -> Unit, onTrack: (MandateEntity) -> Unit, onStatus: (MandateEntity, MandateStatus) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val fresh = snapshot.mandates.filter { it.status == MandateStatus.NEW }
    val tracked = snapshot.mandates.filter { it.status == MandateStatus.TRACKED }
    val ignored = snapshot.mandates.filter { it.status == MandateStatus.IGNORED }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("UPI AutoPay", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.md).background(c.lavender).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recurring debits find their way in quietly", style = Type.bodyExtra, color = c.lavenderInk)
                Text("When a bank text says an AutoPay was set up or is about to be taken, it is listed here. Leftover never counts these as spending until the money actually leaves.", style = Type.caption, color = c.lavenderMuted)
            }
            if (snapshot.mandates.isEmpty()) {
                Card(padding = PaddingValues(20.dp)) {
                    Text("Nothing found yet", style = Type.bodyExtra, color = c.ink)
                    Text("Turn on bank-text tracking and AutoPay notices will show up here as they arrive.", style = Type.caption, color = c.muted)
                }
            }
            if (fresh.isNotEmpty()) {
                SectionLabel("New")
                fresh.forEach { m ->
                    Card(padding = PaddingValues(16.dp)) {
                        MandateHeader(m, hidden, snapshot.today)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LButton("Not mine", { onStatus(m, MandateStatus.IGNORED) }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                            LButton("Track it", { onTrack(m) }, modifier = Modifier.weight(1f), kind = ButtonKind.Lime, height = 48.dp)
                        }
                    }
                }
            }
            if (tracked.isNotEmpty()) {
                SectionLabel("Tracked in Bills")
                tracked.forEach { m -> Card(padding = PaddingValues(16.dp)) { MandateHeader(m, hidden, snapshot.today) } }
            }
            if (ignored.isNotEmpty()) {
                SectionLabel("Dismissed")
                ignored.forEach { m ->
                    Card(padding = PaddingValues(16.dp)) {
                        MandateHeader(m, hidden, snapshot.today)
                        Spacer(Modifier.height(8.dp))
                        LButton("Bring it back", { onStatus(m, MandateStatus.NEW) }, kind = ButtonKind.Quiet, height = 44.dp)
                    }
                }
            }
            Card(padding = PaddingValues(16.dp)) {
                Text("How to cancel one", style = Type.bodyExtra, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text("Open your UPI app, then your profile or settings, then AutoPay (sometimes called Mandates). Pick the debit and choose Revoke or Cancel. Uninstalling the shop's app does not cancel it.", style = Type.caption, color = c.muted)
            }
        }
    }
}

@Composable
private fun MandateHeader(m: MandateEntity, hidden: Boolean, today: LocalDate) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.merchant, style = Type.bodyStrong, color = c.ink, maxLines = 1)
            Text(
                buildString {
                    append(m.frequency.replaceFirstChar { it.uppercase() }.ifBlank { "Recurring" })
                    m.nextDueEpochDay?.let { append(" · next ${UiFormat.dayMonth(LocalDate.ofEpochDay(it))}") }
                },
                style = Type.small, color = c.muted,
            )
        }
        Amount(m.amount, hidden, Type.bodyExtra, color = c.ink)
    }
}
