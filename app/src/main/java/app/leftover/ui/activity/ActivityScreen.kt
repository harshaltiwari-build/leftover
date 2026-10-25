package app.leftover.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.domain.ActivityFilter
import app.leftover.domain.ActivityGroup
import app.leftover.domain.Money
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.Card
import app.leftover.ui.components.Banner
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconTile
import app.leftover.ui.home.TransactionRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

@Composable
fun ActivityScreen(
    snapshot: Snapshot,
    onOpenTransaction: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val hidden = snapshot.settings.hideBalances
    var group by rememberSaveable { mutableStateOf(ActivityGroup.ALL) }
    var query by rememberSaveable { mutableStateOf("") }

    val shown = remember(snapshot.transactions, group, query) { ActivityFilter.apply(snapshot.transactions, group, query, ledger) }
    val byDay = remember(shown) { shown.groupBy { UiFormat.date(it) }.toList().sortedByDescending { it.first } }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Activity", style = Type.h1, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconTile(LeftoverIcons.Import, onOpenImport, "Import a bank statement")
                    IconTile(LeftoverIcons.Wallet, onOpenAccounts, "Accounts")
                }
            }
            if (snapshot.pendingSms.isNotEmpty()) {
                Banner(
                    "${UiFormat.count(snapshot.pendingSms.size, "auto-tracked payment")} ${if (snapshot.pendingSms.size == 1) "needs" else "need"} a look",
                    c.lavender, c.lavenderInk, trailing = "Review →", onClick = onOpenReview,
                )
            }
            SearchField(query) { query = it }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ActivityGroup.entries) { entry -> Chip(entry.label, group == entry, { group = entry }) }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.clickable(role = Role.Button, onClick = onOpenInsights), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryTile("Spent this cycle", ledger.totalSpent(), hidden, Modifier.weight(1f))
                        SummaryTile("Income", ledger.income(), hidden, Modifier.weight(1f))
                    }
                    Text("See insights and trends →", style = Type.captionBold, color = c.ink)
                }
            }
            if (byDay.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (snapshot.transactions.isEmpty()) "Nothing here yet" else "No matches", style = Type.title, color = c.ink)
                        Text(
                            if (snapshot.transactions.isEmpty()) "Add an expense with the + button and it will show up here." else "Try a different search or filter.",
                            style = Type.caption, color = c.muted,
                        )
                    }
                }
            }
            items(byDay, key = { it.first.toEpochDay() }) { (date, list) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(UiFormat.dayHeader(date, snapshot.today), style = Type.captionBold, color = c.muted)
                        val spend = ActivityFilter.daySpend(list)
                        Text(
                            when {
                                hidden -> ""
                                spend > 0 -> "Spent ${Money.format(spend)}"
                                list.any { it.type == app.leftover.data.db.TxType.REFUND } -> "${Money.format(-spend)} back"
                                else -> "No spending"
                            },
                            style = Type.captionBold, color = c.muted,
                        )
                    }
                    Card {
                        list.forEachIndexed { index, tx ->
                            if (index > 0) Divider()
                            TransactionRow(tx, ledger, hidden) { onOpenTransaction(tx.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Leftover.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Radius.md).background(c.surface).border(1.dp, c.hairline, Radius.md).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(LeftoverIcons.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search merchant, note or amount", style = Type.body, color = c.muted)
            BasicTextField(value, onChange, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryTile(label: String, amount: Long, hidden: Boolean, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(modifier.clip(Radius.md).background(c.surface).border(1.dp, c.hairline, Radius.md).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = Type.smallBold, color = c.muted)
        Amount(amount, hidden, Type.h2, color = c.ink)
    }
}
