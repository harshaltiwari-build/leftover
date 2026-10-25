package app.leftover.ui.plan

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.TagEntity
import app.leftover.domain.TagTotals
import app.leftover.domain.rupees
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.home.TransactionRow
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

private val tagColors = listOf("lavender", "sky", "mint", "peach", "rose", "butter")

/** Trips, weddings and occasions: spending that cuts across categories, with a total and an optional budget for each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(snapshot: Snapshot, onBack: () -> Unit, onOpen: (Long) -> Unit, onAdd: (String, String, Long?) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    var name by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("lavender") }
    val taken = snapshot.tags.any { it.name.equals(name.trim(), true) }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Tags and events", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Put a tag on any spend, then see what a trip or a wedding really cost, across every category.", style = Type.caption, color = c.muted)
            if (snapshot.tags.isEmpty()) {
                Card(padding = PaddingValues(20.dp)) { Text("No tags yet. Add one below, or from the Quick add screen.", style = Type.caption, color = c.muted) }
            }
            snapshot.tags.forEach { tag ->
                val spent = TagTotals.spent(tag.id, snapshot.transactions, snapshot.txTags)
                val over = tag.budget != null && spent > tag.budget
                Card(modifier = Modifier.clickable(role = Role.Button) { onOpen(tag.id) }, padding = PaddingValues(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(8.dp, 40.dp).clip(Radius.pill).background(CategoryPalette.of(tag.color, c.isDark).bar))
                        Column(Modifier.weight(1f)) {
                            Text("#${tag.name}", style = Type.bodyStrong, color = c.ink, maxLines = 1)
                            Text("${UiFormat.count(TagTotals.transactionsOf(tag.id, snapshot.transactions, snapshot.txTags).size, "entry", "entries")}", style = Type.small, color = c.muted)
                        }
                        Amount(spent, hidden, Type.bodyExtra, color = if (over) c.emberText else c.ink)
                    }
                    if (tag.budget != null && tag.budget > 0) {
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(spent.toFloat() / tag.budget, color = if (over) c.ember else c.ink, track = if (over) c.emberBg else c.track)
                    }
                }
            }
            SectionLabel("New tag")
            Card(padding = PaddingValues(16.dp)) {
                LabeledField("Name", name, "Goa trip", { name = it.filter { ch -> ch.isLetterOrDigit() || ch == ' ' }.take(20) }, KeyboardType.Text, capitalize = true, error = if (taken) "You already have a tag with that name" else null)
                Spacer(Modifier.height(12.dp))
                LabeledField("Budget (optional)", budget, "0", { budget = it.filter(Char::isDigit).take(8) }, KeyboardType.Number, prefix = "₹")
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tagColors.forEach { key ->
                        Column(
                            Modifier.size(44.dp).clip(Radius.pill).background(CategoryPalette.of(key, c.isDark).bar)
                                .let { if (color == key) it.then(Modifier.padding(0.dp)) else it }
                                .clickable(role = Role.RadioButton) { color = key },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) { if (color == key) Text("✓", style = Type.bodyExtra, color = c.ink) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                LButton("Add tag", enabled = name.isNotBlank() && !taken, kind = ButtonKind.Lime, height = 48.dp, onClick = {
                    onAdd(name.trim(), color, budget.toLongOrNull()?.takeIf { it > 0 }?.let(::rupees))
                    name = ""; budget = ""
                })
            }
        }
    }
}

@Composable
fun TagDetailScreen(snapshot: Snapshot, id: Long, onBack: () -> Unit, onUpdate: (TagEntity) -> Unit, onDelete: (Long) -> Unit, onOpenTransaction: (Long) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val tag = snapshot.tags.firstOrNull { it.id == id }
    var budget by rememberSaveable(id) { mutableStateOf(tag?.budget?.let { (it / 100).toString() }.orEmpty()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (tag == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onBack)
            CenteredHint("This tag was deleted.")
        }
        return
    }
    val spent = TagTotals.spent(tag.id, snapshot.transactions, snapshot.txTags)
    val entries = TagTotals.transactionsOf(tag.id, snapshot.transactions, snapshot.txTags)
    val limit = tag.budget
    val over = limit != null && spent > limit
    val newBudget = budget.toLongOrNull()?.takeIf { it > 0 }?.let(::rupees)

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("#${tag.name}", style = Type.h2, color = c.ink, maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Spent so far", style = Type.caption, color = c.onHeroMuted)
                Amount(spent, hidden, Type.display36, color = c.heroNumber)
                if (limit != null && limit > 0) {
                    ProgressBar(spent.toFloat() / limit, color = if (over) c.ember else c.heroNumber, track = c.onHeroMuted.copy(alpha = 0.35f))
                    Row {
                        Amount(kotlin.math.abs(limit - spent), hidden, Type.captionBold, color = c.onHero)
                        Text(if (over) " over the budget" else " left of the budget", style = Type.caption, color = c.onHeroMuted)
                    }
                }
                Text("Your share only, after refunds. Your friends' parts of split bills aren't counted.", style = Type.small, color = c.onHeroMuted)
            }
            Card(padding = PaddingValues(16.dp)) {
                LabeledField("Budget (optional)", budget, "0", { budget = it.filter(Char::isDigit).take(8) }, KeyboardType.Number, prefix = "₹")
                if (newBudget != tag.budget) {
                    Spacer(Modifier.height(12.dp))
                    LButton("Save budget", { onUpdate(tag.copy(budget = newBudget)) }, kind = ButtonKind.Lime, height = 48.dp)
                }
            }
            SectionLabel("Entries")
            if (entries.isEmpty()) Card { CenteredHint("Nothing is tagged #${tag.name} yet. Add the tag when you save a spend.") }
            else Card {
                entries.forEachIndexed { index, tx ->
                    if (index > 0) Divider()
                    TransactionRow(tx, snapshot.ledger, hidden) { onOpenTransaction(tx.id) }
                }
            }
            if (confirmDelete) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Delete this tag?", style = Type.bodyExtra, color = c.ink)
                    Text("The entries stay. They just lose this tag.", style = Type.caption, color = c.muted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LButton("Keep it", { confirmDelete = false }, modifier = Modifier.weight(1f), height = 48.dp)
                        LButton("Delete", { onDelete(tag.id); onBack() }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                    }
                }
            } else {
                Text(
                    "Delete this tag", style = Type.bodyStrong, color = c.emberText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { confirmDelete = true }.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
        }
    }
}
