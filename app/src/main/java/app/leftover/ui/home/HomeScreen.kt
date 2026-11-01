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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.leftover.domain.PaceWarning
import app.leftover.domain.SafeToday
import app.leftover.domain.SafeTodayCopy
import app.leftover.ui.theme.CategoryPalette
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
import app.leftover.ui.theme.FeatureIcons
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
    onOpenProfile: () -> Unit = onOpenSettings,
    onOpenTimeline: () -> Unit = {},
    onOpenRecap: () -> Unit = {},
    onOpenSplit: () -> Unit = {},
    onOpenPlan: () -> Unit = onOpenBills,
) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val hidden = snapshot.settings.hideBalances
    val left = ledger.leftToSpend
    val safe = remember(ledger) { SafeToday.of(ledger) }
    var showHow by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(c.ground).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header(snapshot, onToggleHide, onOpenProfile, onOpenBills)
        if (snapshot.settings.lastBackupError != null) {
            Banner("Couldn't back up your data", c.emberBg, c.emberText, trailing = "Details", onClick = onOpenBackup)
        }
        val noBalance = snapshot.accounts.none { it.includeInLeft && it.type != app.leftover.data.db.AccountType.CARD }
        if (snapshot.pendingSms.isNotEmpty()) {
            val n = snapshot.pendingSms.size
            Banner("$n bank ${if (n == 1) "text needs" else "texts need"} a look", c.lavender, c.lavenderInk, trailing = "Review", onClick = onOpenReview)
        }
        val hero = remember(ledger, hidden) { app.leftover.domain.HeroState.of(ledger, snapshot.settings.usualIncome, hidden) }
        HeroCard(
            snapshot, left, hidden, showHow, onToggleHow = { showHow = !showHow }, onOpenBreakdown = onOpenBreakdown, onOpenTimeline = onOpenTimeline,
            hero = hero, onHeroAction = {
                when (hero.kind) {
                    app.leftover.domain.HeroKind.NO_BALANCE -> onAddBalance()
                    app.leftover.domain.HeroKind.OVER -> onOpenTimeline()
                    app.leftover.domain.HeroKind.PAYDAY -> onOpenRecap()
                    app.leftover.domain.HeroKind.NORMAL -> Unit
                }
            },
        )
        if (!noBalance) SafeTodayCard(safe, hidden, onOpenAfford)
        QuickActions(onOpenVoice, onOpenAfford, onOpenSplit, onOpenGoals)
        val warning = remember(ledger) { PaceWarning.worst(ledger) }
        if (warning != null) WarningLine(warning.text(hidden), onOpenPlan) else InsightCard(snapshot, hidden, onOpenInsights)
        CategoryTiles(snapshot, hidden, onOpenPlan)
        NextBills(snapshot, hidden, onOpenBills)
        Recent(snapshot, hidden, onOpenActivity, onOpenTransaction)
    }
}

@Composable
private fun Header(snapshot: Snapshot, onToggleHide: () -> Unit, onOpenProfile: () -> Unit, onOpenBills: () -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val name = snapshot.settings.name.ifBlank { "there" }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(46.dp).border(2.dp, c.lime, Radius.pill).padding(3.dp).clip(Radius.pill)
                .clickable(role = Role.Button, onClick = onOpenProfile).semantics { contentDescription = "Your profile" },
            contentAlignment = Alignment.Center,
        ) { app.leftover.ui.components.Avatar(name, snapshot.settings.profilePhoto, 40.dp, Type.bodyExtra) }
        Column(Modifier.weight(1f)) {
            Text("${UiFormat.greeting(LocalTime.now().hour)},", style = Type.caption, color = c.muted)
            Text(name, style = Type.h2, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun HeroCard(
    snapshot: Snapshot, left: LeftToSpend, hidden: Boolean, showHow: Boolean, onToggleHow: () -> Unit, onOpenBreakdown: () -> Unit, onOpenTimeline: () -> Unit,
    hero: app.leftover.domain.HeroState, onHeroAction: () -> Unit,
) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val cycle = snapshot.cycle
    val day = cycle.dayOfCycle(snapshot.today)
    val spentNow = ledger.totalSpent(cycle)
    val spentBefore = ledger.totalSpent(cycle.previous(), upToDayOfCycle = day)
    Column(
        Modifier.fillMaxWidth().let { if (c.isDark) it else it.shadow(16.dp, Radius.xl, ambientColor = c.ink.copy(alpha = 0.25f), spotColor = c.ink.copy(alpha = 0.25f)) }
            .clip(Radius.xl).background(c.hero).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(hero.title, style = Type.bodyStrong, color = if (hero.kind == app.leftover.domain.HeroKind.PAYDAY) c.heroNumber else c.onHero)
            Text(
                "${left.daysUntilPayday} ${if (left.daysUntilPayday == 1) "day" else "days"} · ${UiFormat.dayMonth(cycle.nextPayday)}",
                style = Type.smallBold, color = c.onHeroMuted,
            )
        }
        if (hero.kind != app.leftover.domain.HeroKind.NO_BALANCE) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            val over = hero.kind == app.leftover.domain.HeroKind.OVER
            // Over until payday shows by how much, in the warm colour; the title already says it's over.
            app.leftover.ui.components.CountingAmount(if (over) -left.left else left.left, hidden, Type.display48, color = if (over) c.ember else c.heroNumber, modifier = Modifier.weight(1f))
            Row(
                Modifier.height(40.dp).clip(Radius.pill).border(1.dp, c.onHeroMuted.copy(alpha = 0.4f), Radius.pill).clickable(role = Role.Button, onClick = onToggleHow).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("How?", style = Type.captionBold, color = c.onHero)
                Icon(LeftoverIcons.ChevronDown, null, tint = c.onHero, modifier = Modifier.size(14.dp))
            }
        }
        hero.line?.let { Text(it, style = Type.caption, color = c.onHeroMuted) }
        hero.action?.let { label ->
            Text(
                label, style = Type.captionBold, color = c.onLime,
                modifier = Modifier.clip(Radius.md).background(c.lime).clickable(role = Role.Button, onClick = onHeroAction).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (spentBefore > 0 && !hidden) {
            val less = spentNow <= spentBefore
            Text(
                "${if (less) "↓" else "↑"} ${Money.format(kotlin.math.abs(spentBefore - spentNow))} ${if (less) "less" else "more"} spent than last cycle by today",
                style = Type.smallBold, color = if (less) c.heroNumber else c.emberText,
                modifier = Modifier.clip(Radius.pill).background(if (less) c.heroNumber.copy(alpha = 0.14f) else c.emberBg).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.onHeroMuted.copy(alpha = 0.25f)))
        Row(Modifier.fillMaxWidth()) {
            HeroStat("Came in", ledger.income(), hidden, plus = true, modifier = Modifier.weight(1f), emptyText = "Not logged yet")
            HeroStat("Spent", spentNow, hidden, minus = true, modifier = Modifier.weight(1f))
            HeroStat("For goals", left.goalsTotal, hidden, modifier = Modifier.weight(1f))
        }
        AnimatedVisibility(showHow) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitBar(left)
                Legend(c.onHeroMuted)
                BreakdownLine("In your accounts", left.inAccounts, false, hidden)
                BreakdownLine("Bills before payday", left.billsTotal, true, hidden)
                BreakdownLine("Card dues", left.cardDuesTotal, true, hidden)
                BreakdownLine("Set aside for goals", left.goalsTotal, true, hidden)
                RunwayPill(ledger.runway)
                Text(
                    "Day by day until payday →", style = Type.captionBold, color = c.heroNumber,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenTimeline).padding(vertical = 8.dp),
                )
                Text(
                    "See full breakdown →", style = Type.captionBold, color = c.heroNumber,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenBreakdown).padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, amount: Long, hidden: Boolean, modifier: Modifier = Modifier, plus: Boolean = false, minus: Boolean = false, emptyText: String? = null) {
    val c = Leftover.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Type.small, color = c.onHeroMuted)
        // Income that hasn't been logged isn't "+₹0": say so.
        if (amount == 0L && emptyText != null) { Text(emptyText, style = Type.captionBold, color = c.onHeroMuted, maxLines = 1, modifier = Modifier.padding(top = 2.dp)); return@Column }
        Text(
            if (hidden) Money.HIDDEN else (if (plus) "+" else if (minus) "−" else "") + Money.format(amount),
            style = Type.bodyExtra, color = if (plus) c.heroNumber else c.onHero, maxLines = 1,
        )
    }
}

/** The daily target: a ring of how much of today's safe amount is used, and what tomorrow looks like. */
@Composable
private fun SafeTodayCard(safe: SafeToday, hidden: Boolean, onOpenAfford: () -> Unit) {
    val c = Leftover.colors
    val ringColor = if (safe.over || safe.leftNow <= 0) c.ember else c.limeDeep
    // The ring sweeps to its new size when you spend; with "Remove animations" on it just changes.
    val sweep by androidx.compose.animation.core.animateFloatAsState(
        if (safe.over || safe.leftNow <= 0) 1f else 1f - safe.fraction,
        androidx.compose.animation.core.tween(app.leftover.ui.theme.Motion.durationMs(app.leftover.ui.theme.rememberReduceMotion())), label = "safe-ring",
    )
    val track = c.track
    val copy = SafeTodayCopy.of(safe, hidden)
    val description = "${copy.title}. ${copy.amount} ${copy.of}. ${copy.line}"
    Card(shape = Radius.lg, padding = PaddingValues(16.dp), modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Can I afford something?", onClick = onOpenAfford)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
            Canvas(Modifier.size(56.dp)) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2
                drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                val remaining = sweep
                if (remaining > 0f) drawArc(ringColor, -90f, 360f * remaining, false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(copy.title, style = Type.captionBold, color = c.muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(copy.amount, style = Type.h2, color = if (copy.warning) c.emberText else c.ink)
                    if (copy.of.isNotEmpty()) Text("  ${copy.of}", style = Type.caption, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text(copy.line, style = Type.small, color = c.muted)
            }
            Icon(LeftoverIcons.Chevron, null, tint = c.subtle, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WarningLine(text: String, onOpen: () -> Unit) {
    val c = Leftover.colors
    Card(shape = Radius.lg, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), modifier = Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(Radius.pill).background(c.hero), contentAlignment = Alignment.Center) {
                Icon(LeftoverIcons.Info, null, tint = c.heroNumber, modifier = Modifier.size(18.dp))
            }
            Text(text, style = Type.captionBold, color = c.ink, modifier = Modifier.weight(1f))
            Icon(LeftoverIcons.Chevron, null, tint = c.subtle, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SectionHead(title: String, extra: String?, action: String, onAction: () -> Unit) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = Type.title, color = c.ink)
            if (extra != null) Text("  · $extra", style = Type.captionBold, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(action, style = Type.captionBold, color = c.positive, modifier = Modifier.clip(Radius.sm).clickable(role = Role.Button, onClick = onAction).padding(8.dp))
    }
}

/** Where the money went this cycle, as swipeable tiles with each category's share of its limit. */
@Composable
private fun CategoryTiles(snapshot: Snapshot, hidden: Boolean, onOpen: () -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val cats = remember(ledger) {
        ledger.categories.filter { it.kind == app.leftover.data.db.CategoryKind.EXPENSE && !it.hidden }
            .map { it to ledger.spent(it.id).coerceAtLeast(0) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
    }
    if (cats.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead("Spending by category", null, "See all", onOpen)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            items(cats, key = { it.first.id }) { (cat, spent) ->
                val palette = CategoryPalette.of(cat.color, c.isDark)
                val limit = cat.budgetLimit
                Card(shape = Radius.lg, padding = PaddingValues(12.dp), modifier = Modifier.width(118.dp).clickable(role = Role.Button, onClick = onOpen)) {
                    Box(Modifier.fillMaxWidth().height(54.dp).clip(Radius.md).background(palette.tile), contentAlignment = Alignment.Center) {
                        Icon(LeftoverIcons.forKey(cat.icon), null, tint = c.ink, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(cat.name, style = Type.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.display(spent, hidden), style = Type.bodyExtra, color = c.ink, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(Radius.pill).background(c.track)) {
                        val share = if (limit != null && limit > 0) (spent.toFloat() / limit).coerceIn(0.03f, 1f) else 0f
                        if (share > 0f) Box(Modifier.fillMaxWidth(share).height(5.dp).clip(Radius.pill).background(if (limit != null && spent > limit) c.ember else palette.bar))
                    }
                }
            }
        }
    }
}

/** Bills in the next 7 days as cards, with their total. They're already set aside in Left until payday. */
@Composable
private fun NextBills(snapshot: Snapshot, hidden: Boolean, onOpenBills: () -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val upcoming = remember(ledger) {
        snapshot.bills.filter { !it.archived }.map { it to ledger.billNextDue(it) }
            .filter { !it.second.isAfter(snapshot.today.plusDays(7)) }.sortedBy { it.second }
    }
    if (upcoming.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead("Next 7 days", Money.display(upcoming.sumOf { it.first.amount }, hidden), "All bills", onOpenBills)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            items(upcoming, key = { it.first.id }) { (bill, due) ->
                val soon = !due.isAfter(snapshot.today.plusDays(1))
                Card(shape = Radius.lg, padding = PaddingValues(14.dp), modifier = Modifier.width(128.dp).clickable(role = Role.Button, onClick = onOpenBills)) {
                    Text(
                        UiFormat.shortDay(due, snapshot.today), style = Type.smallBold, color = if (soon) c.emberText else c.ink,
                        modifier = Modifier.clip(Radius.pill).background(if (soon) c.emberBg else c.surfaceAlt).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(bill.name, style = Type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Amount(bill.amount, hidden, Type.title, color = c.ink)
                }
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
private fun QuickActions(onVoice: () -> Unit, onAfford: () -> Unit, onSplit: () -> Unit, onGoals: () -> Unit) {
    val items = listOf(
        Triple("Say it", FeatureIcons.quickActions.getValue("Say it"), onVoice),
        Triple("Afford?", FeatureIcons.quickActions.getValue("Afford?"), onAfford),
        Triple("Split", FeatureIcons.quickActions.getValue("Split"), onSplit),
        Triple("Goals", FeatureIcons.quickActions.getValue("Goals"), onGoals),
    )
    // At large text sizes the four tiles reflow to two rows of two instead of squeezing.
    val perRow = if (LocalDensity.current.fontScale >= 1.4f) 2 else 4
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.withIndex().chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (index, item) ->
                    ActionTile(item.first, item.second, item.third, highlighted = index == 1, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit, highlighted: Boolean, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(
        modifier.heightIn(min = 80.dp)
            .let { if (highlighted || c.isDark) it else it.shadow(6.dp, Radius.lg, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.10f)) }
            .clip(Radius.lg).background(if (highlighted) c.lime else c.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        app.leftover.ui.components.FeatureIcon(icon, if (highlighted) c.onLime else c.ink, 22.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, style = Type.smallBold, color = if (highlighted) c.onLime else c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
