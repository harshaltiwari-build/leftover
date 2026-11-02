package app.leftover.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.db.CategoryEntity
import app.leftover.domain.Money
import app.leftover.domain.ScreenshotProposal
import app.leftover.ui.UiFormat
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * After a payment screenshot is read: everything found, a guessed category you can change, and any warning. Nothing is
 * added until Add is tapped; Edit opens the full form with the same details filled in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmScreenshotScreen(
    proposal: ScreenshotProposal, app: String, categories: List<CategoryEntity>, hidden: Boolean,
    onAdd: (categoryId: Long?) -> Unit, onEdit: () -> Unit, onCancel: () -> Unit,
) {
    val c = Leftover.colors
    var categoryId by rememberSaveable { mutableStateOf(proposal.tx.categoryId) }
    val tx = proposal.tx
    val whenText = remember(tx.occurredAt) {
        Instant.ofEpochMilli(tx.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)).replace("AM", "am").replace("PM", "pm")
    }
    val ref = tx.note.removePrefix("UPI ref").trim().takeIf { it.isNotEmpty() }
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onCancel, "Close", close = true)
            Text("Add this payment?", style = Type.h2, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (app.isNotBlank()) "Read from your $app screenshot" else "Read from your screenshot",
                style = Type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Column(Modifier.fillMaxWidth().clip(Radius.xl).background(c.hero).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tx.merchant.ifBlank { "Payment" }, style = Type.bodyStrong, color = c.onHero, textAlign = TextAlign.Center)
                Text(if (hidden) Money.HIDDEN else Money.format(tx.amount), style = Type.display48, color = c.heroNumber)
                Text(whenText, style = Type.caption, color = c.onHeroMuted)
            }
            if (proposal.warnings.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(Radius.md).background(c.emberBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    proposal.warnings.forEach { Text(it.text(hidden), style = Type.captionBold, color = c.emberText) }
                }
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                Detail("Paid to", tx.merchant.ifBlank { "Not found" })
                Divider()
                Detail("Amount", if (hidden) Money.HIDDEN else Money.format(tx.amount))
                Divider()
                Detail("When", whenText)
                Divider()
                Detail("From account", proposal.accountName)
                if (ref != null) { Divider(); Detail("UPI ref", ref) }
            }
            Card(padding = PaddingValues(16.dp)) {
                Text("Category", style = Type.bodyExtra, color = c.ink)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat -> Chip(cat.name, categoryId == cat.id, { categoryId = cat.id }, icon = LeftoverIcons.forKey(cat.icon)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LButton("Edit details", onEdit, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 52.dp)
            LButton("Add", { onAdd(categoryId) }, modifier = Modifier.weight(1f), height = 52.dp)
        }
        Text(
            "Cancel", style = Type.bodyStrong, color = c.muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(Radius.sm).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCancel).padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun Detail(label: String, value: String) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Type.caption, color = c.muted)
        Text(value, style = Type.captionBold, color = c.ink, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.padding(start = 16.dp))
    }
}
