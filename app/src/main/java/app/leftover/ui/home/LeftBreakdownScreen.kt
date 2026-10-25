package app.leftover.ui.home

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import app.leftover.domain.rupees
import app.leftover.ui.components.LabeledField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.domain.Deduction
import app.leftover.domain.Money
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Card
import app.leftover.ui.components.Divider
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

/** "How is this calculated?": every number behind Left to spend, with accounts you can include or exclude. */
@Composable
fun LeftBreakdownScreen(snapshot: Snapshot, onBack: () -> Unit, onSetIncluded: (AccountEntity, Boolean) -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val left = ledger.leftToSpend
    val hidden = snapshot.settings.hideBalances
    val spendable = snapshot.accounts.filter { it.type == AccountType.BANK || it.type == AccountType.CASH }
    // A what-if only: money you expect isn't part of the real number until it arrives.
    var expected by rememberSaveable { mutableStateOf("") }
    var countExpected by rememberSaveable { mutableStateOf(false) }
    val extra = if (countExpected) rupees(expected.toLongOrNull() ?: 0L) else 0L
    val shownLeft = left.left + extra
    val shownPerDay = if (extra > 0) maxOf(shownLeft, 0L) / left.daysUntilPayday.coerceAtLeast(1) else left.perDay

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("How this is calculated", style = Type.title, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (extra > 0) "If it arrives" else "Left to spend", style = Type.caption, color = c.onHeroMuted)
                    Amount(shownLeft, hidden, Type.display36, color = c.heroNumber)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Per day · ${left.daysUntilPayday} ${if (left.daysUntilPayday == 1) "day" else "days"}", style = Type.caption, color = c.onHeroMuted)
                    Amount(shownPerDay, hidden, Type.h2, color = c.onHero)
                }
            }

            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SectionTotal("Money in accounts", left.inAccounts, hidden, minus = false)
                spendable.forEach { account ->
                    Divider()
                    ToggleRow(
                        title = account.name,
                        subtitle = Money.display(ledger.balance(account.id), hidden) + if (account.includeInLeft) "" else " · not counted",
                        checked = account.includeInLeft,
                        onChange = { onSetIncluded(account, it) },
                    )
                }
            }
            DeductionCard("Bills before payday", left.bills, left.billsTotal, hidden, "No bills due before payday.")
            DeductionCard("Card dues", left.cardDues, left.cardDuesTotal, hidden, "No card payments due.")
            DeductionCard("Set aside for goals", left.goals, left.goalsTotal, hidden, "Nothing left to set aside this cycle.")
            Card(padding = PaddingValues(16.dp)) {
                Text("Expecting money before payday?", style = Type.bodyStrong, color = c.ink)
                Text("An invoice or a gift you haven't received. Try it here to see what you could spend. It isn't counted until it arrives.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                LabeledField("Expected amount", expected, "0", { expected = it.filter(Char::isDigit).take(8) }, KeyboardType.Number, prefix = "₹")
                ToggleRow("Count it for now", if (countExpected && extra > 0) "Showing the number above as if it has arrived" else null, countExpected, { countExpected = it })
            }
            if (left.comingBack > 0 || left.owedByYou > 0) {
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    SectionLabel("Friends · not counted above", Modifier.padding(vertical = 14.dp))
                    if (left.comingBack > 0) FriendLine("Coming back to you", left.comingBack, hidden)
                    if (left.owedByYou > 0) {
                        if (left.comingBack > 0) Divider()
                        FriendLine("You owe friends", left.owedByYou, hidden)
                    }
                    Text(
                        "Money friends owe you only counts once they pay you back. Money you owe isn't subtracted either, since it has no due date.",
                        style = Type.small, color = c.muted, modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
            }
            Text(
                "Transfers between your own accounts and card repayments are never counted as spending. Money already saved into goals sits in a goal-savings account and isn't counted here.",
                style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun FriendLine(label: String, amount: Long, hidden: Boolean) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, color = c.ink)
        Amount(amount, hidden, Type.bodyStrong, color = c.ink)
    }
}

@Composable
private fun SectionTotal(label: String, amount: Long, hidden: Boolean, minus: Boolean) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(label)
        Row {
            if (minus && !hidden) Text("− ", style = Type.bodyExtra, color = c.ink)
            Amount(amount, hidden, Type.bodyExtra, color = c.ink)
        }
    }
}

@Composable
private fun DeductionCard(title: String, lines: List<Deduction>, total: Long, hidden: Boolean, emptyText: String) {
    val c = Leftover.colors
    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        SectionTotal(title, total, hidden, minus = true)
        if (lines.isEmpty()) {
            Divider()
            Text(emptyText, style = Type.caption, color = c.muted, modifier = Modifier.padding(vertical = 14.dp))
        }
        lines.forEach { line ->
            Divider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.label, style = Type.body, color = c.ink)
                    if (line.detail.isNotBlank()) Text(line.detail, style = Type.small, color = c.muted)
                }
                Amount(line.amount, hidden, Type.bodyStrong, color = c.ink)
            }
        }
    }
}
