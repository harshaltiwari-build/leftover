package app.leftover.ui.insights

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.TxType
import app.leftover.data.db.WishStatus
import app.leftover.domain.CategorySpend
import app.leftover.domain.InsightText
import app.leftover.domain.CategoryDrivers
import app.leftover.domain.FeeDetector
import app.leftover.domain.Insights
import app.leftover.domain.Money
import app.leftover.domain.RangePreset
import app.leftover.domain.RangeReport
import app.leftover.ui.components.Segmented
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import app.leftover.domain.toLocalDate
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(snapshot: Snapshot, onBack: () -> Unit, onOpenRecap: () -> Unit, onOpenReports: () -> Unit, onOpenAsk: () -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val insights = Insights.build(snapshot.ledger)
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Insights", style = Type.h1, color = c.ink)
            // A recap of a cycle with nothing in it would be five empty cards.
            if (snapshot.ledger.totalSpent(snapshot.cycle.previous()) > 0) Text(
                "${snapshot.cycle.previous().start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} recap", style = Type.captionBold, color = c.heroNumber,
                modifier = Modifier.clip(Radius.pill).background(c.hero).clickable(role = Role.Button, onClick = onOpenRecap).padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PeriodOverview(snapshot, hidden)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinkTile("Reports", "Any dates · PDF", onOpenReports, Modifier.weight(1f))
                LinkTile("Ask", "Questions about your money", onOpenAsk, Modifier.weight(1f))
            }
            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                Text(InsightText.comparison(insights.comparison, hidden), style = Type.title, color = c.ink)
                if (insights.comparison.hasHistory && insights.averageSpent > 0 && !hidden) {
                    Spacer(Modifier.height(6.dp))
                    Text("Your usual full cycle is about ${Money.format(insights.averageSpent)}.", style = Type.caption, color = c.muted)
                }
                Spacer(Modifier.height(14.dp))
                // A line of zeros says nothing; the chart appears once two cycles have spending in them.
                if (insights.trend.count { it.spent > 0 } >= 2) TrendChart(insights, hidden)
                else Text("Your trend appears here after your second cycle.", style = Type.caption, color = c.muted)
            }

            val (fees, feeTotal) = FeeDetector.feesIn(snapshot.transactions, snapshot.cycle)
            if (fees.isNotEmpty()) {
                Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Fees and charges", style = Type.title, color = c.ink)
                        Text(Money.display(feeTotal, hidden), style = Type.bodyExtra, color = c.ink)
                    }
                    Text("Money that went to the bank this cycle, not to anything you bought.", style = Type.caption, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                    fees.take(5).forEach { fee ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fee.merchant.ifBlank { fee.note }, style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(Money.display(fee.amount, hidden), style = Type.body, color = c.ink)
                        }
                    }
                    Text("Worth asking the bank to waive an annual or late fee. They often do the first time.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }

            val plan = if (snapshot.settings.irregularIncome) snapshot.ledger.incomePlan else null
            if (snapshot.settings.irregularIncome) {
                Card(shape = Radius.xl, padding = PaddingValues(18.dp), color = c.lavender, bordered = false) {
                    Text("Your income, smoothed", style = Type.title, color = c.lavenderInk)
                    Spacer(Modifier.height(6.dp))
                    if (plan == null) {
                        Text("Log your income for a couple of cycles and Leftover will find your leanest month to plan on.", style = Type.caption, color = c.lavenderMuted)
                    } else {
                        Text(
                            if (hidden) "Plan on your leanest recent month, and keep anything above it as a buffer for slow months."
                            else "Plan on ${Money.format(plan.floor)} a cycle, your leanest of the last ${plan.cyclesUsed}. Your average is ${Money.format(plan.average)}. " +
                                if (plan.extra > 0) "This cycle you're ${Money.format(plan.extra)} above the floor: set that aside as a buffer." else "This cycle hasn't passed the floor yet.",
                            style = Type.caption, color = c.lavenderMuted,
                        )
                    }
                }
            }

            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                Text(if (hidden) "Where your money goes" else InsightText.topCategories(insights.categories, insights.spent), style = Type.title, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(if (insights.categories.isEmpty()) "" else "Tap a category to see why", style = Type.small, color = c.muted)
                Spacer(Modifier.height(10.dp))
                val top = insights.categories.maxOfOrNull { it.spent } ?: 1L
                insights.categories.forEach { entry ->
                    CategoryBar(entry, top, hidden, entry.category.id == selected) { selected = if (selected == entry.category.id) null else entry.category.id }
                }
                val chosen = insights.categories.firstOrNull { it.category.id == selected }
                if (chosen != null) {
                    val count = snapshot.transactions.count { it.categoryId == chosen.category.id && snapshot.cycle.contains(it.occurredAt.toLocalDate()) }
                    Spacer(Modifier.height(10.dp))
                    val change = CategoryDrivers.changes(snapshot.ledger).firstOrNull { it.category.id == chosen.category.id }
                    Text(
                        "${chosen.category.name}: $count ${if (count == 1) "payment" else "payments"} this cycle" + (if (hidden) "." else ", ${Money.format(chosen.spent)} in total, about ${Money.format(chosen.spent / snapshot.cycle.dayOfCycle(snapshot.today))} a day.") +
                            " " + CategoryDrivers.explain(change, hidden),
                        style = Type.caption, color = c.lavenderInk,
                        modifier = Modifier.fillMaxWidth().clip(Radius.md).background(c.lavender).padding(12.dp),
                    )
                }
            }

            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                Text(InsightText.kept(insights.keptPercent), style = Type.title, color = c.ink)
                Spacer(Modifier.height(12.dp))
                MoneyInOut(insights, hidden)
            }
        }
    }
}

private enum class Period(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** Week · Month · Year: four stat tiles for the period, and for the month, each budget against the pace of the cycle. */
@Composable
private fun PeriodOverview(snapshot: Snapshot, hidden: Boolean) {
    val c = Leftover.colors
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    val today = snapshot.today
    val span = when (period) {
        Period.WEEK -> RangePreset.LAST_7.span(today, snapshot.cycle)
        Period.MONTH -> RangePreset.THIS_CYCLE.span(today, snapshot.cycle)
        Period.YEAR -> RangePreset.THIS_YEAR.span(today, snapshot.cycle)
    }
    val r = remember(snapshot.ledger, span) { RangeReport.build(snapshot.ledger, span) }
    Segmented(Period.entries.map { it.label }, period.label, { picked -> period = Period.entries.first { it.label == picked } })
    val change = if (r.previousSpent > 0) ((r.spent - r.previousSpent) * 100 / r.previousSpent).toInt() else null
    val top = r.categories.firstOrNull()
    val since = when (period) { Period.WEEK -> "the week before"; Period.MONTH -> "the same days last cycle"; Period.YEAR -> "the same days before" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Spent", Money.display(r.spent, hidden), change?.let { "${if (it <= 0) "↓" else "↑"} ${kotlin.math.abs(it)}% vs $since" } ?: "Nothing to compare yet", if (change != null && change <= 0) c.positive else if (change != null) c.emberText else c.muted, Modifier.weight(1f))
            StatTile("Daily average", Money.display(r.perDay, hidden), "Over ${r.span.days} ${if (r.span.days == 1) "day" else "days"}", c.muted, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Biggest category", top?.category?.name ?: "—", top?.let { "${it.spent * 100 / r.spent.coerceAtLeast(1)}% of spending" } ?: "No spending yet", c.muted, Modifier.weight(1f))
            val regretCount = r.entries.count { it.feeling == app.leftover.data.db.TxFeeling.REGRET }
            StatTile("Regrets", Money.display(r.regret, hidden), if (regretCount == 0) "Mark spends on their page" else "$regretCount ${if (regretCount == 1) "spend" else "spends"} you marked", if (r.regret > 0) c.emberText else c.muted, Modifier.weight(1f))
        }
    }
    if (period == Period.MONTH) BudgetVsActual(snapshot, hidden)
}

@Composable
private fun StatTile(label: String, value: String, note: String, noteColor: Color, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Card(shape = Radius.lg, padding = PaddingValues(14.dp), modifier = modifier) {
        Text(label, style = Type.smallBold, color = c.muted)
        Text(value, style = Type.h2, color = c.ink, maxLines = 1)
        Text(note, style = Type.small, color = noteColor, maxLines = 2)
    }
}

/** Each budget against how far through the cycle you are: over pace, worth watching, or on pace. */
@Composable
private fun BudgetVsActual(snapshot: Snapshot, hidden: Boolean) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val pace = snapshot.cycle.dayOfCycle(snapshot.today).toFloat() / snapshot.cycle.totalDays
    val rows = ledger.categories.filter { it.kind == app.leftover.data.db.CategoryKind.EXPENSE && !it.hidden && (it.budgetLimit ?: 0) > 0 }
        .map { it to ledger.spent(it.id).coerceAtLeast(0) }.sortedByDescending { it.second.toFloat() / it.first.budgetLimit!! }
    if (rows.isEmpty()) return
    Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Budget vs actual", style = Type.title, color = c.ink)
            Text("${(pace * 100).toInt()}% of the cycle gone", style = Type.smallBold, color = c.muted)
        }
        Spacer(Modifier.height(10.dp))
        rows.forEach { (cat, spent) ->
            val limit = cat.budgetLimit!!
            val used = spent.toFloat() / limit
            val (tag, bg, fg) = when {
                spent > limit || used > pace + 0.15f -> Triple("Over pace", c.emberBg, c.emberText)
                used > pace -> Triple("Watch", CategoryPalette.of("butter", c.isDark).tile, c.ink)
                else -> Triple("On pace", if (c.isDark) c.surfaceAlt else Color(0xFFE3F0C9), c.positive)
            }
            val palette = CategoryPalette.of(cat.color, c.isDark)
            Column(Modifier.padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(Radius.pill).background(palette.bar))
                    Text(cat.name, style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(if (hidden) Money.HIDDEN else "${Money.format(spent)} / ${Money.format(limit)}", style = Type.captionBold, color = c.ink)
                    Text(tag, style = Type.smallBold, color = fg, modifier = Modifier.clip(Radius.pill).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(Radius.pill).background(c.track))
                    Box(Modifier.fillMaxWidth(used.coerceIn(0.02f, 1f)).height(8.dp).clip(Radius.pill).background(if (spent > limit) c.ember else palette.bar))
                    Box(Modifier.fillMaxWidth(pace.coerceIn(0f, 1f)).height(8.dp), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.width(2.dp).height(14.dp).background(c.muted))
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkTile(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(
        modifier.clip(Radius.lg).background(c.surface).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$title →", style = Type.bodyExtra, color = c.ink)
        Text(subtitle, style = Type.small, color = c.muted, maxLines = 2)
    }
}

@Composable
private fun TrendChart(insights: Insights, hidden: Boolean) {
    val c = Leftover.colors
    val trend = insights.trend
    val max = (trend.maxOfOrNull { it.spent } ?: 0L).coerceAtLeast(1L).toFloat()
    val description = trend.joinToString("; ") { "${it.cycle.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${if (hidden) "hidden" else Money.format(it.spent)}" }
    Column(Modifier.semantics { contentDescription = "Spending per cycle: $description" }) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val stepX = if (trend.size > 1) size.width / (trend.size - 1) else size.width
            val points = trend.mapIndexed { i, t -> Offset(i * stepX, size.height - 8.dp.toPx() - (t.spent / max) * (size.height - 20.dp.toPx())) }
            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            if (insights.averageSpent > 0) {
                val y = size.height - 8.dp.toPx() - (insights.averageSpent / max) * (size.height - 20.dp.toPx())
                drawLine(c.muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            drawPath(path, c.ink, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, p ->
                if (trend[i].isCurrent) {
                    drawCircle(c.ink, 7.dp.toPx(), p)
                    drawCircle(c.lime, 4.5.dp.toPx(), p)
                } else drawCircle(c.ink, 3.5.dp.toPx(), p)
            }
        }
        Spacer(Modifier.height(6.dp))
        val current = trend.firstOrNull { it.isCurrent }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (current != null) Text("● this cycle ${Money.display(current.spent, hidden)} so far", style = Type.small, color = c.ink)
            if (insights.averageSpent > 0) Text("┄ usual ${Money.display(insights.averageSpent, hidden)}", style = Type.small, color = c.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            trend.forEach { t ->
                Text(t.cycle.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = if (t.isCurrent) Type.smallBold else Type.small, color = if (t.isCurrent) c.ink else c.muted)
            }
        }
    }
}

@Composable
private fun CategoryBar(entry: CategorySpend, top: Long, hidden: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = Leftover.colors
    val palette = CategoryPalette.of(entry.category.color, c.isDark)
    Column(
        Modifier.fillMaxWidth().clip(Radius.sm).background(if (selected) c.surfaceAlt else Color.Transparent).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.category.name, style = Type.captionBold, color = c.ink)
            Text(Money.display(entry.spent, hidden), style = Type.bodyExtra, color = c.ink)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(Radius.pill).background(c.track)) {
            Box(Modifier.fillMaxWidth((entry.spent.toFloat() / top).coerceIn(0.02f, 1f)).height(10.dp).clip(Radius.pill).background(palette.bar))
        }
    }
}

@Composable
private fun MoneyInOut(insights: Insights, hidden: Boolean) {
    val c = Leftover.colors
    val max = maxOf(insights.income, insights.spent, 1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Money in", style = Type.captionBold, color = c.ink)
                Text(Money.display(insights.income, hidden), style = Type.bodyExtra, color = c.ink)
            }
            Box(Modifier.fillMaxWidth(insights.income / max).height(14.dp).clip(Radius.xs).background(c.lime))
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Spending", style = Type.captionBold, color = c.ink)
                Text(Money.display(insights.spent, hidden), style = Type.bodyExtra, color = c.ink)
            }
            Box(Modifier.fillMaxWidth((insights.spent / max).coerceAtLeast(0.01f)).height(14.dp).clip(Radius.xs).background(c.ink))
        }
    }
}

private data class RecapCard(val kicker: String, val big: String, val line: String, val sub: String, val dark: Boolean, val accent: Boolean = false)

@Composable
fun RecapScreen(snapshot: Snapshot, onClose: () -> Unit) {
    val c = Leftover.colors
    val context = LocalContext.current
    val ledger = snapshot.ledger
    val cycle = snapshot.cycle.previous()
    val before = cycle.previous()
    var index by rememberSaveable { mutableIntStateOf(0) }
    var hideAmounts by rememberSaveable { mutableStateOf(true) }

    val spent = ledger.totalSpent(cycle)
    val spentBefore = ledger.totalSpent(before)
    val top = ledger.categories.map { it to ledger.spent(it.id, cycle) }.filter { it.second > 0 }.maxByOrNull { it.second }
    val saved = snapshot.transactions.filter { it.type == TxType.GOAL_SAVE && cycle.contains(it.occurredAt.toLocalDate()) }.sumOf { it.amount }
    val skipped = snapshot.wishes.filter { it.status == WishStatus.SKIPPED }
    val monthName = cycle.start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    val hasData = spent > 0 || saved > 0
    val cards = listOf(
        RecapCard("${cycle.start.dayOfMonth} ${cycle.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} – ${cycle.end.dayOfMonth} ${cycle.end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}", "$monthName.", "Your cycle in money, in five cards.", "Tap through. Nothing is shared unless you choose to.", true),
        RecapCard("You spent", if (hideAmounts) "—" else Money.format(spent), when {
            !hasData -> "Not enough data for a recap yet."
            spentBefore <= 0 -> "Your first tracked cycle."
            spent <= spentBefore -> if (hideAmounts) "Less than the cycle before." else "${Money.format(spentBefore - spent)} less than the cycle before."
            else -> if (hideAmounts) "More than the cycle before." else "${Money.format(spent - spentBefore)} more than the cycle before."
        }, "Recaps appear once you have a full cycle of entries.", false, accent = true),
        RecapCard("Biggest category", top?.first?.name ?: "—", if (top == null) "No spending recorded." else if (hideAmounts) "${(top.second * 100 / spent.coerceAtLeast(1))}% of spending" else "${Money.format(top.second)} · ${(top.second * 100 / spent.coerceAtLeast(1))}% of spending", "", true),
        RecapCard("Into your goals", if (hideAmounts) "—" else "+${Money.format(saved)}", if (saved > 0) "You moved money into goals this cycle." else "Nothing moved into goals this cycle.", "Small and steady beats big and rare.", false),
        RecapCard("Kept by waiting", if (hideAmounts) "${skipped.size} skips" else Money.format(skipped.sumOf { it.price }), if (skipped.isEmpty()) "No wishlist items skipped yet." else "${skipped.size} wishlist ${if (skipped.size == 1) "item" else "items"} you decided you didn't need.", if (hideAmounts) "Sharing shows percentages and counts only." else "Amounts will be visible to anyone you share with.", true),
    )
    val card = cards[index]
    val background = if (card.dark) c.hero else if (card.accent) c.lime else c.ground
    val foreground = if (card.dark) c.onHero else c.ink
    val numberColor = if (card.dark) c.heroNumber else c.ink

    Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cards.indices.forEach { i -> Box(Modifier.weight(1f).height(4.dp).clip(Radius.pill).background(if (i <= index) foreground else foreground.copy(alpha = 0.2f))) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Leftover · recap", style = Type.captionBold, color = foreground.copy(alpha = 0.8f))
            BackButton(onClose, "Close recap", close = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(card.kicker, style = Type.bodyStrong, color = foreground.copy(alpha = 0.85f))
            Spacer(Modifier.height(16.dp))
            Text(card.big, style = if (card.big.length > 8) Type.display48 else Type.display64, color = numberColor)
            Spacer(Modifier.height(16.dp))
            Text(card.line, style = Type.h2, color = foreground)
            Spacer(Modifier.height(8.dp))
            Text(card.sub, style = Type.body, color = foreground.copy(alpha = 0.8f))
        }
        if (index == cards.lastIndex) {
            Card(color = foreground.copy(alpha = 0.1f), bordered = false, padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ToggleRow("Hide amounts when sharing", null, hideAmounts, { hideAmounts = it })
            }
            Spacer(Modifier.height(10.dp))
            LButton("Share my recap", {
                val change = if (spentBefore > 0) ((spentBefore - spent) * 100 / spentBefore).toInt() else null
                val text = "My Leftover recap for $monthName: " + when {
                    hideAmounts && change != null -> "${kotlin.math.abs(change)}% ${if (change >= 0) "below" else "above"} the cycle before, ${skipped.size} wishlist skips."
                    hideAmounts -> "my first tracked cycle, ${skipped.size} wishlist skips."
                    else -> "spent ${Money.format(spent)}, ${Money.format(saved)} into goals."
                }
                val share = (top?.second ?: 0L) * 100 / spent.coerceAtLeast(1)
                val stats = listOf(
                    RecapStat(
                        "You spent",
                        when { !hideAmounts -> Money.format(spent); change != null -> "${kotlin.math.abs(change)}% ${if (change >= 0) "less" else "more"}"; else -> "First cycle" },
                        if (change == null) "My first tracked cycle" else if (change >= 0) "Less than the cycle before" else "More than the cycle before",
                    ),
                    RecapStat("Biggest category", top?.first?.name ?: "—", if (top == null) "No spending recorded" else "$share% of spending"),
                    RecapStat("Into my goals", if (hideAmounts) (if (saved > 0) "Saved" else "Not yet") else "+${Money.format(saved)}", if (saved > 0) "Moved money on payday" else "Next cycle"),
                    RecapStat("Kept by waiting", if (hideAmounts) "${skipped.size} ${if (skipped.size == 1) "skip" else "skips"}" else Money.format(skipped.sumOf { it.price }), "Wishlist items I didn't need"),
                )
                val period = "${cycle.start.dayOfMonth} ${cycle.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} – ${cycle.end.dayOfMonth} ${cycle.end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
                val image = runCatching { RecapImage.write(context, monthName, period, stats) }.getOrNull()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (image != null) {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", image))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else type = "text/plain"
                }
                context.startActivity(Intent.createChooser(intent, "Share recap"))
            }, kind = ButtonKind.Lime)
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LButton("Back", { index = (index - 1).coerceAtLeast(0) }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, enabled = index > 0, height = 52.dp)
            LButton(if (index == cards.lastIndex) "Start over" else "Next", { index = if (index == cards.lastIndex) 0 else index + 1 }, modifier = Modifier.weight(2f), kind = if (card.dark) ButtonKind.Lime else ButtonKind.Primary, height = 52.dp)
        }
    }
}
