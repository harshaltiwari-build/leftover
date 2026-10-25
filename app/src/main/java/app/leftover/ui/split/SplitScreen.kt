package app.leftover.ui.split

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import app.leftover.domain.Money
import app.leftover.domain.toLocalDate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ListRow
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import kotlin.math.abs

/** The Split tab: who owes whom at a glance, with every friend and group one tap away. */
@Composable
fun SplitScreen(
    snapshot: Snapshot,
    onSplitBill: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onAddGroup: () -> Unit,
    onOpenInsights: () -> Unit,
    onStopRepeat: (Long) -> Unit,
) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val book = snapshot.shared
    val people = book.people.sortedByDescending { abs(book.balanceWith(it.id)) }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Split", style = Type.h1, color = c.ink)
            Box(
                Modifier.heightIn(min = 44.dp).clip(Radius.pill).background(c.surface).border(1.dp, c.border, Radius.pill)
                    .clickable(role = Role.Button, onClick = onOpenInsights).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Insights", style = Type.captionBold, color = c.ink) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val owedBy = book.balances.values.count { it > 0 }
                val owedTo = book.balances.values.count { it < 0 }
                BalanceTile("You're owed", book.owedToYou, hidden, dark = true, note = if (owedBy == 0) "Nobody yet" else "by ${UiFormat.count(owedBy, "person", "people")}", modifier = Modifier.weight(1f).fillMaxHeight())
                BalanceTile("You owe", book.youOwe, hidden, dark = false, note = if (owedTo == 0) "Nobody" else "to ${UiFormat.count(owedTo, "person", "people")}", modifier = Modifier.weight(1f).fillMaxHeight())
            }
            if (book.owedToYou > 0 || book.youOwe > 0) {
                val net = book.owedToYou - book.youOwe
                Text(
                    (if (hidden) "" else if (net >= 0) "Overall you're up ${Money.format(net)}. " else "Overall you're down ${Money.format(-net)}. ") +
                        "Money owed to you isn't counted in Left to spend until it arrives.",
                    style = Type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            LButton("Split a bill", onSplitBill, kind = ButtonKind.Lime, icon = LeftoverIcons.Plus)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Friends", "Add friend", onAddPerson)
                if (people.isEmpty()) {
                    Card(padding = PaddingValues(vertical = 8.dp)) {
                        CenteredHint("Add the people you share costs with. Everything stays on this phone.")
                    }
                } else {
                    Card {
                        people.forEachIndexed { index, person ->
                            if (index > 0) Divider()
                            val balance = book.balanceWith(person.id)
                            ListRow(
                                onClick = { onOpenPerson(person.id) },
                                leading = { PersonAvatar(person) },
                                title = person.name,
                                subtitle = balanceCaption(balance),
                                trailing = { if (balance != 0L) Amount(abs(balance), hidden, Type.bodyExtra, color = if (balance > 0) c.positive else c.emberText) },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Groups", "New group", onAddGroup)
                if (book.groups.isEmpty()) {
                    Card(padding = PaddingValues(vertical = 8.dp)) {
                        CenteredHint("Flatmates, a trip or an event: put people together to see one total and the fewest payments to settle.")
                    }
                } else {
                    Card {
                        book.groups.forEachIndexed { index, group ->
                            if (index > 0) Divider()
                            val summary = book.summary(group.id)
                            ListRow(
                                onClick = { onOpenGroup(group.id) },
                                leading = {
                                    Icon(group.kind.icon(), null, tint = c.ink, modifier = Modifier.size(44.dp).clip(Radius.sm).background(CategoryPalette.of("lavender", c.isDark).tile).padding(11.dp))
                                },
                                title = group.name,
                                subtitle = "${group.kind.label()} · ${book.groupMembers(group.id).size + 1} people",
                                trailing = { Amount(summary?.total ?: 0, hidden, Type.bodyExtra) },
                            )
                        }
                    }
                }
            }

            val recent = (book.expenses.map { Triple(it.occurredAt, it.title, if (it.paidBy == app.leftover.domain.ME) "You paid ${Money.display(it.total, hidden)}" else "${book.person(it.paidBy)?.name ?: "A friend"} paid ${Money.display(it.total, hidden)}") } +
                book.settlements.map { s -> Triple(s.occurredAt, if (s.direction == app.leftover.data.db.SettleDirection.THEY_PAID) "${book.person(s.personId)?.name ?: "A friend"} paid you back" else "You paid ${book.person(s.personId)?.name ?: "a friend"} back", Money.display(s.amount, hidden)) })
                .sortedByDescending { it.first }.take(5)
            if (recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Recent")
                    Card {
                        recent.forEachIndexed { index, (at, title, detail) ->
                            if (index > 0) Divider()
                            ListRow(title = title, subtitle = "$detail · ${UiFormat.pastDay(at.toLocalDate(), snapshot.today)}")
                        }
                    }
                }
            }

            if (snapshot.recurring.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Repeating bills")
                    Card {
                        snapshot.recurring.forEachIndexed { index, rule ->
                            if (index > 0) Divider()
                            ListRow(
                                leading = { Icon(LeftoverIcons.Repeat, null, tint = c.ink, modifier = Modifier.size(44.dp).clip(Radius.sm).background(c.track).padding(11.dp)) },
                                title = rule.title,
                                subtitle = "Next on ${UiFormat.dayMonth(app.leftover.domain.RecurringSplits.next(rule, snapshot.today))} · ${rule.participantIds.size + if (rule.includesMe) 1 else 0} people",
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Amount(rule.total, hidden, Type.bodyExtra)
                                        Text(
                                            "Stop", style = Type.captionBold, color = c.emberText,
                                            modifier = Modifier.heightIn(min = 44.dp).clip(Radius.sm).clickable(role = Role.Button) { onStopRepeat(rule.id) }.padding(horizontal = 8.dp, vertical = 12.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    Text("Each one is added by itself on its day. Stopping keeps what was already added.", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** One of the two totals at the top of Split. Both tiles share the same layout so the figures line up. */
@Composable
private fun BalanceTile(label: String, amount: Long, hidden: Boolean, dark: Boolean, note: String, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(
        modifier.clip(Radius.lg).background(if (dark) c.hero else c.surface).let { if (dark) it else it.border(1.dp, c.hairline, Radius.lg) }.padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Type.caption, color = if (dark) c.onHeroMuted else c.muted)
        Column {
            Amount(amount, hidden, Type.h1, color = if (dark) c.heroNumber else c.ink)
            Text(note, style = Type.small, color = if (dark) c.onHeroMuted else c.muted)
        }
    }
}

/** A section label on the left and a small action on the right, centred on one line. */
@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title)
        Row(
            Modifier.heightIn(min = 44.dp).clip(Radius.sm).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(LeftoverIcons.Plus, null, tint = c.ink, modifier = Modifier.size(14.dp))
            Text(action, style = Type.captionBold, color = c.ink)
        }
    }
}
