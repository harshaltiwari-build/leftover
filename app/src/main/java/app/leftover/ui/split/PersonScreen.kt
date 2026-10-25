package app.leftover.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettleMethod
import app.leftover.domain.UpiLink
import app.leftover.domain.toLocalDate
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconTile
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import kotlin.math.abs

/** One entry in a friend's history: a split bill or a payment between you. */
private data class HistoryEntry(
    val key: String,
    val title: String,
    val whenMillis: Long,
    val caption: String,
    /** Positive: they owe you more because of this. Negative: you owe them more. */
    val effect: Long,
)

@Composable
fun PersonScreen(
    snapshot: Snapshot,
    personId: Long,
    onBack: () -> Unit,
    onSettle: () -> Unit,
    onSplitBill: () -> Unit,
    onUpdatePerson: (PersonEntity) -> Unit,
    onDeleteExpense: (Long) -> Unit,
    onDeleteSettlement: (Long) -> Unit,
) {
    val c = Leftover.colors
    val book = snapshot.shared
    val hidden = snapshot.settings.hideBalances
    val person = book.person(personId)
    if (person == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onBack)
            CenteredHint("This friend isn't here any more.")
        }
        return
    }
    val balance = book.balanceWith(personId)
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var editingUpi by rememberSaveable { mutableStateOf(false) }
    var upi by rememberSaveable(person.id) { mutableStateOf(person.upiId.orEmpty()) }
    val upiError = if (upi.isNotBlank() && !UpiLink.isValidId(upi.trim())) "That doesn't look like a UPI id, for example name@bank" else null

    val history = buildList {
        book.expensesWith(personId).forEach { expense ->
            val theyOweYou = expense.paidBy != personId
            val amount = if (theyOweYou) book.shareOf(expense.id, personId) else book.shareOf(expense.id, 0L)
            add(
                HistoryEntry(
                    key = "e${expense.id}", title = expense.title, whenMillis = expense.occurredAt,
                    caption = if (theyOweYou) "You paid · ${person.name}'s share" else "${person.name} paid · your share",
                    effect = if (theyOweYou) amount else -amount,
                ),
            )
        }
        book.settlements.filter { it.personId == personId }.forEach { s ->
            val theyPaid = s.direction == SettleDirection.THEY_PAID
            add(
                HistoryEntry(
                    key = "s${s.id}", title = if (theyPaid) "${person.name} paid you" else "You paid ${person.name}", whenMillis = s.occurredAt,
                    caption = if (s.method == SettleMethod.UPI) "Settled by UPI" else "Settled in cash",
                    effect = if (theyPaid) -s.amount else s.amount,
                ),
            )
        }
    }.sortedByDescending { it.whenMillis }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            PersonAvatar(person, size = 40.dp)
            Text(person.name, style = Type.h2, color = c.ink, maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    when {
                        balance > 0 -> "${person.name} owes you"
                        balance < 0 -> "You owe ${person.name}"
                        else -> "All settled up"
                    },
                    style = Type.caption, color = c.onHeroMuted,
                )
                Amount(abs(balance), hidden, Type.display36, color = c.heroNumber, showPaise = true)
            }
            if (balance != 0L) LButton("Settle up", onSettle, kind = ButtonKind.Lime)
            LButton("Split a bill with ${person.name}", onSplitBill, kind = ButtonKind.Outline, height = 48.dp)

            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("UPI id", style = Type.bodyStrong, color = c.ink)
                        Text(person.upiId ?: "Not saved. Needed only to open your UPI app when you pay.", style = Type.small, color = c.muted)
                    }
                    Chip(if (person.upiId == null) "Add" else "Edit", editingUpi, { editingUpi = !editingUpi })
                }
                if (editingUpi) {
                    Divider()
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledField("UPI id", upi, "name@bank", { upi = it.trim().take(60) }, KeyboardType.Email, error = upiError)
                        LButton("Save", enabled = upiError == null, height = 48.dp, onClick = {
                            onUpdatePerson(person.copy(upiId = upi.trim().ifBlank { null }))
                            editingUpi = false
                        })
                    }
                }
            }

            SectionLabel("History")
            if (history.isEmpty()) {
                Card { CenteredHint("Nothing shared yet. Split a bill and it shows up here.") }
            } else {
                Card {
                    history.forEachIndexed { index, entry ->
                        if (index > 0) Divider()
                        val date = entry.whenMillis.toLocalDate()
                        if (confirming == entry.key) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Delete this entry?", style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                                Chip("Keep", false, { confirming = null })
                                Chip("Delete", true, {
                                    confirming = null
                                    if (entry.key.startsWith("e")) onDeleteExpense(entry.key.drop(1).toLong()) else onDeleteSettlement(entry.key.drop(1).toLong())
                                })
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = Type.bodyStrong, color = c.ink, maxLines = 1)
                                    Text("${UiFormat.pastDay(date, snapshot.today)} · ${entry.caption}", style = Type.small, color = c.muted, maxLines = 2)
                                }
                                Amount(entry.effect, hidden, Type.bodyExtra, color = when {
                                    entry.key.startsWith("s") -> c.ink
                                    entry.effect > 0 -> c.positive
                                    else -> c.emberText
                                }, signed = true, showPaise = true)
                                IconTile(LeftoverIcons.Trash, { confirming = entry.key }, "Delete ${entry.title}")
                            }
                        }
                    }
                }
            }
        }
    }
}
