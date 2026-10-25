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
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.domain.ME
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
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import kotlin.math.abs

/** One group: what it has cost, how that sits against its budget, who owes whom, and the fewest payments to settle. */
@Composable
fun GroupScreen(
    snapshot: Snapshot,
    groupId: Long,
    onBack: () -> Unit,
    onSplitBill: () -> Unit,
    onSettle: (personId: Long, amount: Long) -> Unit,
    onDeleteExpense: (Long) -> Unit,
) {
    val c = Leftover.colors
    val book = snapshot.shared
    val hidden = snapshot.settings.hideBalances
    val summary = book.summary(groupId)
    var confirming by rememberSaveable { mutableStateOf<Long?>(null) }
    if (summary == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onBack)
            CenteredHint("This group isn't here any more.")
        }
        return
    }
    val group = summary.group
    fun nameOf(id: Long) = if (id == ME) "You" else book.person(id)?.name ?: "Friend"
    val budget = group.budget
    val expenses = book.expensesOf(groupId)

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Column {
                Text(group.name, style = Type.h2, color = c.ink, maxLines = 1)
                Text("${group.kind.label()} · ${book.groupMembers(groupId).size + 1} people", style = Type.small, color = c.muted)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Group total", style = Type.caption, color = c.onHeroMuted)
                        Amount(summary.total, hidden, Type.display36, color = c.heroNumber)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Your share", style = Type.caption, color = c.onHeroMuted)
                        Amount(summary.yourShare, hidden, Type.h2, color = c.onHero)
                    }
                }
                if (budget != null && budget > 0) {
                    ProgressBar(summary.total.toFloat() / budget, color = if (summary.total > budget) c.ember else c.heroNumber, track = c.onHeroMuted.copy(alpha = 0.35f))
                    Row {
                        Amount(abs(budget - summary.total), hidden, Type.captionBold, color = c.onHero)
                        Text(if (summary.total > budget) " over budget" else " left of the budget", style = Type.caption, color = c.onHeroMuted)
                    }
                }
            }
            LButton("Add a bill to this group", onSplitBill, kind = ButtonKind.Lime)

            if (summary.byCategory.isNotEmpty()) {
                SectionLabel("Where it went")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    summary.byCategory.forEachIndexed { index, (categoryId, amount) ->
                        if (index > 0) Divider()
                        val category = snapshot.ledger.category(categoryId)
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category?.name ?: "Other", style = Type.bodyStrong, color = c.ink)
                                Amount(amount, hidden, Type.bodyStrong)
                            }
                            ProgressBar(amount.toFloat() / summary.total, color = CategoryPalette.of(category?.color ?: "grey", c.isDark).bar)
                        }
                    }
                }
            }

            SectionLabel("Who owes whom")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                val members = listOf(ME) + book.groupMembers(groupId).map { it.id }
                members.forEachIndexed { index, id ->
                    if (index > 0) Divider()
                    val net = summary.nets[id] ?: 0L
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(book.person(id), size = 36.dp)
                        Text(nameOf(id), style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                        if (net == 0L) Text("settled up", style = Type.caption, color = c.muted)
                        else {
                            Text(if (net > 0) "is owed " else "owes ", style = Type.caption, color = c.muted)
                            Amount(abs(net), hidden, Type.bodyExtra, color = if (net > 0) c.positive else c.emberText, showPaise = true)
                        }
                    }
                }
            }

            if (summary.plan.isNotEmpty()) {
                SectionLabel("Fewest payments to settle")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    summary.plan.forEachIndexed { index, transfer ->
                        if (index > 0) Divider()
                        val involvesYou = transfer.from == ME || transfer.to == ME
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${nameOf(transfer.from)} pays ${nameOf(transfer.to)}", style = Type.bodyStrong, color = c.ink)
                                Amount(transfer.amount, hidden, Type.caption, color = c.muted, showPaise = true)
                            }
                            if (involvesYou) {
                                val other = if (transfer.from == ME) transfer.to else transfer.from
                                Chip("Settle", false, { onSettle(other, transfer.amount) })
                            }
                        }
                    }
                }
                Text("Payments between other people aren't tracked here. Record yours with Settle.", style = Type.small, color = c.muted)
            }

            SectionLabel("Bills")
            if (expenses.isEmpty()) Card { CenteredHint("No bills yet.") }
            else Card {
                expenses.forEachIndexed { index, expense ->
                    if (index > 0) Divider()
                    if (confirming == expense.id) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Delete this bill?", style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                            Chip("Keep", false, { confirming = null })
                            Chip("Delete", true, { confirming = null; onDeleteExpense(expense.id) })
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(expense.title, style = Type.bodyStrong, color = c.ink, maxLines = 1)
                                Text("${UiFormat.pastDay(expense.occurredAt.toLocalDate(), snapshot.today)} · ${nameOf(expense.paidBy)} paid", style = Type.small, color = c.muted)
                            }
                            Amount(expense.total, hidden, Type.bodyExtra)
                            IconTile(LeftoverIcons.Trash, { confirming = expense.id }, "Delete ${expense.title}")
                        }
                    }
                }
            }
        }
    }
}
