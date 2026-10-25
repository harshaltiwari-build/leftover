package app.leftover.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.Ledger
import app.leftover.domain.LeftToSpend
import app.leftover.domain.CategoryDrivers
import androidx.compose.ui.platform.LocalDensity
import app.leftover.domain.Money
import app.leftover.domain.Runway
import app.leftover.domain.RunwayStatus
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.Banner
import app.leftover.ui.components.Card
import app.leftover.ui.components.CategoryTile
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconTile
import app.leftover.ui.components.ListRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    snapshot: Snapshot,
    onToggleHide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBreakdown: () -> Unit,
    onOpenAfford: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onOpenBackup: () -> Unit,
    onAddBalance: () -> Unit,
) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val hidden = snapshot.settings.hideBalances
    val left = ledger.leftToSpend
    var showHow by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(c.ground).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Header(snapshot, onToggleHide, onOpenSettings, onOpenBills)
        if (snapshot.settings.lastBackupError != null) {
            Banner("Couldn't back up your data", c.emberBg, c.emberText, trailing = "Details", onClick = onOpenBackup)
        }
        if (snapshot.accounts.none { it.includeInLeft && it.type != app.leftover.data.db.AccountType.CARD }) {
            Banner("Add your balance to see what's safe to spend", c.lavender, c.lavenderInk, trailing = "Add balance", onClick = onAddBalance)
        }
        if (snapshot.pendingSms.isNotEmpty()) {
            val n = snapshot.pendingSms.size
            Banner("$n bank ${if (n == 1) "text needs" else "texts need"} a look", c.lavender, c.lavenderInk, trailing = "Review", onClick = onOpenReview)
        }
        HeroCard(left, ledger.runway, hidden, showHow, onToggleHow = { showHow = !showHow }, onOpenBreakdown = onOpenBreakdown)
        QuickActions(onOpenVoice, onOpenAfford, onOpenInsights, onOpenGoals)
        ComingUp(snapshot, hidden, onOpenBills)
        InsightCard(snapshot, hidden, onOpenInsights)
        Recent(snapshot, hidden, onOpenActivity, onOpenTransaction)
    }
}

@Composable
private fun Header(snapshot: Snapshot, onToggleHide: () -> Unit, onOpenSettings: () -> Unit, onOpenBills: () -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val name = snapshot.settings.name.ifBlank { "there" }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(44.dp).clip(Radius.pill).background(c.lavender).clickable(role = Role.Button, onClick = onOpenSettings)
                .semantics { contentDescription = "Profile and settings" },
            contentAlignment = Alignment.Center,
        ) { Text(name.take(1).uppercase(), style = Type.bodyStrong, color = c.lavenderInk) }
        Column(Modifier.weight(1f)) {
            Text(snapshot.today.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = Type.caption, color = c.muted)
            Text("${UiFormat.greeting(LocalTime.now().hour)}, $name", style = Type.title, color = c.ink)
        }
        IconTile(if (hidden) LeftoverIcons.EyeOff else LeftoverIcons.Eye, onToggleHide, if (hidden) "Show balances" else "Hide balances")
        val dueSoon = snapshot.bills.count { snapshot.ledger.billNextDue(it) <= snapshot.today.plusDays(2) }
        Box {
            IconTile(LeftoverIcons.Bell, onOpenBills, if (dueSoon > 0) "Bills, $dueSoon due soon" else "Bills")
            if (dueSoon > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(9.dp).clip(Radius.pill).background(c.ember))
        }
    }
}

@Composable
private fun HeroCard(left: LeftToSpend, runway: Runway, hidden: Boolean, showHow: Boolean, onToggleHow: () -> Unit, onOpenBreakdown: () -> Unit) {
    val c = Leftover.colors
    Column(Modifier.fillMaxWidth().clip(Radius.xl).background(c.hero).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Left to spend", style = Type.bodyStrong, color = c.onHero)
            Text(
                "${left.daysUntilPayday} ${if (left.daysUntilPayday == 1) "day" else "days"} to payday",
                style = Type.smallBold, color = c.heroNumber,
                modifier = Modifier.clip(Radius.pill).background(c.heroNumber.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Amount(left.left, hidden, Type.display48, color = c.heroNumber, modifier = Modifier.weight(1f))
            Row(
                Modifier.height(44.dp).clip(Radius.pill).clickable(role = Role.Button, onClick = onToggleHow).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("How?", style = Type.captionBold, color = c.onHero)
                Icon(LeftoverIcons.ChevronDown, null, tint = c.onHero, modifier = Modifier.size(14.dp))
            }
        }
        Row {
            Text("About ", style = Type.body, color = c.onHeroMuted)
            Amount(left.perDay, hidden, Type.bodyStrong, color = c.onHero)
            Text(" a day until payday", style = Type.body, color = c.onHeroMuted)
        }
        RunwayPill(runway)
        SplitBar(left)
        Legend(c.onHeroMuted)
        AnimatedVisibility(showHow) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.onHeroMuted.copy(alpha = 0.3f)))
                BreakdownLine("In your accounts", left.inAccounts, false, hidden)
                BreakdownLine("Bills before payday", left.billsTotal, true, hidden)
                BreakdownLine("Card dues", left.cardDuesTotal, true, hidden)
                BreakdownLine("Set aside for goals", left.goalsTotal, true, hidden)
                Text(
                    "See full breakdown →", style = Type.captionBold, color = c.heroNumber,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenBreakdown).padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** One calm line about whether the money lasts at the recent pace. It says nothing until there are a few days of history. */
@Composable
private fun RunwayPill(runway: Runway) {
    val c = Leftover.colors
    val (text, tight) = when (runway.status) {
        RunwayStatus.NOT_ENOUGH_DATA -> return
        RunwayStatus.REACHES_PAYDAY -> "At your recent pace, this lasts until payday" to false
        RunwayStatus.RUNS_OUT -> "At your recent pace, this runs out ${runway.runsOutOn?.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))}" to true
    }
    Text(
        text, style = Type.smallBold, color = if (tight) c.emberText else c.heroNumber,
        modifier = Modifier.clip(Radius.pill).background(if (tight) c.emberBg else c.heroNumber.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun BreakdownLine(label: String, amount: Long, minus: Boolean, hidden: Boolean) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Type.body, color = c.onHeroMuted)
        Row {
            if (minus && !hidden) Text("− ", style = Type.bodyStrong, color = c.onHero)
            Amount(amount, hidden, Type.bodyStrong, color = c.onHero)
        }
    }
}

@Composable
private fun SplitBar(left: LeftToSpend) {
    val c = Leftover.colors
    val colors = listOf(Color(0xFF8A8F80), Color(0xFFB9BDB0), Color(0xFFB9A8F5), if (c.isDark) c.ink else c.heroNumber)
    val slices = left.slices()
    Row(Modifier.fillMaxWidth().height(10.dp).clip(Radius.pill), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        slices.forEachIndexed { index, share ->
            if (share > 0f) Box(Modifier.weight(share).height(10.dp).background(colors[index]))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(textColor: Color) {
    val c = Leftover.colors
    val entries = listOf("Bills" to Color(0xFF8A8F80), "Card due" to Color(0xFFB9BDB0), "Goals" to Color(0xFFB9A8F5), "Left to spend" to if (c.isDark) c.ink else c.heroNumber)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(Radius.xs).background(color))
                Text(name, style = Type.small, color = textColor)
            }
        }
    }
}

@Composable
private fun QuickActions(onVoice: () -> Unit, onAfford: () -> Unit, onInsights: () -> Unit, onGoals: () -> Unit) {
    val items = listOf(
        Triple("Voice", LeftoverIcons.Mic, onVoice),
        Triple("Afford?", LeftoverIcons.Target, onAfford),
        Triple("Insights", LeftoverIcons.Trend, onInsights),
        Triple("Goals", LeftoverIcons.Savings, onGoals),
    )
    // At large text sizes the four tiles reflow to two rows of two instead of squeezing.
    val perRow = if (LocalDensity.current.fontScale >= 1.4f) 2 else 4
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.withIndex().chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (index, item) ->
                    ActionTile(item.first, item.second, item.third, highlighted = index == 0, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit, highlighted: Boolean, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(
        modifier.heightIn(min = 84.dp).clip(Radius.lg).background(if (highlighted) c.lime else c.surface).padding(vertical = 8.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (highlighted) c.onLime else c.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = Type.smallBold, color = if (highlighted) c.onLime else c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ComingUp(snapshot: Snapshot, hidden: Boolean, onOpenBills: () -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val upcoming = snapshot.bills.map { it to ledger.billNextDue(it) }.sortedBy { it.second }
    if (upcoming.isEmpty()) return
    val shown = upcoming.take(3)
    val more = upcoming.size - shown.size
    val dueSoon = upcoming.filter { !it.second.isAfter(snapshot.cycle.nextPayday) }.sumOf { it.first.amount }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Coming up", style = Type.title, color = c.ink)
                if (dueSoon > 0) Text("${Money.display(dueSoon, hidden)} due before payday", style = Type.small, color = c.muted)
            }
            Box(Modifier.heightIn(min = 44.dp).clip(Radius.sm).clickable(role = Role.Button, onClick = onOpenBills).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text("All bills", style = Type.captionBold, color = c.muted)
            }
        }
        Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.surface).border(1.dp, c.hairline, Radius.lg)) {
            shown.forEachIndexed { index, (bill, due) ->
                if (index > 0) Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(c.hairline))
                val soon = !due.isAfter(snapshot.today.plusDays(1))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = onOpenBills).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(Radius.sm).background(if (soon) c.emberBg else c.surfaceAlt), contentAlignment = Alignment.Center) {
                        Icon(LeftoverIcons.forKey(bill.icon), null, tint = if (soon) c.emberText else c.ink, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(bill.name, style = Type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val relative = UiFormat.shortDay(due, snapshot.today)
                        Text(
                            if (relative.contains("day") || relative == "Today") "$relative · ${UiFormat.dayMonth(due)}" else relative, style = Type.small,
                            color = if (soon) c.emberText else c.muted, maxLines = 1,
                        )
                    }
                    Amount(bill.amount, hidden, Type.bodyExtra, color = c.ink)
                }
            }
            if (more > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpenBills), contentAlignment = Alignment.Center) {
                    Text("+$more more", style = Type.captionBold, color = c.muted)
                }
            }
        }
    }
}


@Composable
private fun InsightCard(snapshot: Snapshot, hidden: Boolean, onOpen: () -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val cycle = snapshot.cycle
    val day = cycle.dayOfCycle(snapshot.today)
    val spentNow = ledger.totalSpent(cycle)
    val previous = cycle.previous()
    val spentBefore = ledger.totalSpent(previous, upToDayOfCycle = day)
    val message = when {
        snapshot.transactions.isEmpty() -> "Add your first few expenses and Leftover will start spotting patterns."
        spentBefore <= 0 -> "This is your first full cycle — insights get sharper next month."
        hidden -> if (spentNow <= spentBefore) "You're spending less than at this point last cycle." else "You're spending more than at this point last cycle."
        spentNow <= spentBefore -> "By day $day you've spent ${Money.format(spentBefore - spentNow)} less than at this point last cycle."
        else -> "By day $day you've spent ${Money.format(spentNow - spentBefore)} more than at this point last cycle."
    }
    Row(
        Modifier.fillMaxWidth().clip(Radius.lg).background(c.lavender).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(Radius.sm).background(c.surface), contentAlignment = Alignment.Center) {
            Icon(LeftoverIcons.Trend, null, tint = c.lavenderMuted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, style = Type.bodyStrong, color = c.lavenderInk)
            val driver = if (hidden || spentBefore <= 0) null else CategoryDrivers.headline(CategoryDrivers.changes(ledger))
            if (driver != null) Text(driver, style = Type.caption, color = c.lavenderMuted)
        }
    }
}

@Composable
private fun Recent(snapshot: Snapshot, hidden: Boolean, onSeeAll: () -> Unit, onOpen: (Long) -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", style = Type.title, color = c.ink)
            Text("See all", style = Type.captionBold, color = c.muted, modifier = Modifier.clickable(role = Role.Button, onClick = onSeeAll).padding(8.dp))
        }
        Card {
            val recent = snapshot.transactions.take(4)
            if (recent.isEmpty()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("No spending yet", style = Type.bodyStrong, color = c.ink)
                    Text("Tap + to add your first one — it takes 5 seconds.", style = Type.caption, color = c.muted)
                }
            }
            recent.forEachIndexed { index, tx ->
                if (index > 0) Divider()
                TransactionRow(tx, ledger, hidden, { onOpen(tx.id) })
            }
        }
    }
}

@Composable
fun TransactionRow(tx: TransactionEntity, ledger: Ledger, hidden: Boolean, onClick: () -> Unit) {
    val c = Leftover.colors
    val signed = UiFormat.signed(tx)
    val color = when {
        tx.type == TxType.TRANSFER -> c.muted
        signed > 0 -> c.positive
        else -> c.ink
    }
    ListRow(
        onClick = onClick,
        leading = { CategoryTile(UiFormat.iconKey(tx, ledger), UiFormat.colorKey(tx, ledger)) },
        title = UiFormat.title(tx, ledger),
        subtitle = UiFormat.subtitle(tx, ledger),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Amount(signed, hidden, Type.bodyExtra, color = color, signed = tx.type != TxType.TRANSFER)
                val tag = UiFormat.tag(tx, ledger)
                Text((if (tag != null) "$tag · " else "") + UiFormat.pastDay(UiFormat.date(tx), ledger.today), style = Type.small, color = c.muted)
            }
        },
    )
}
