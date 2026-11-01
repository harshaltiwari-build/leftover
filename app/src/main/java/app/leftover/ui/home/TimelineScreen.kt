package app.leftover.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.domain.Money
import app.leftover.domain.MoneyTimeline
import app.leftover.domain.TimelineEvent
import app.leftover.domain.TimelineKind
import app.leftover.ui.UiFormat
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Card
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconBadge
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

/**
 * Left until payday, day by day: goal money set aside now, each bill, EMI and card due on its date with what's left
 * after it, and payday at the end. It ends on the same number as Home, so the headline can be followed step by step.
 */
@Composable
fun TimelineScreen(snapshot: Snapshot, onBack: () -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val timeline = remember(snapshot.ledger, snapshot.settings.usualIncome) { MoneyTimeline.build(snapshot.ledger, snapshot.settings.usualIncome) }
    val today = snapshot.today
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Until payday", style = Type.h2, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(Radius.xl).background(c.hero).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("In your accounts now", style = Type.caption, color = c.onHeroMuted)
                        Text(Money.display(timeline.start, hidden), style = Type.h2, color = c.onHero)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Left on ${UiFormat.dayMonth(timeline.payday.date)}", style = Type.caption, color = c.onHeroMuted)
                        Text(Money.display(timeline.leftAtPayday, hidden), style = Type.h1, color = if (timeline.leftAtPayday < 0) c.ember else c.heroNumber)
                    }
                }
                Text(
                    if (timeline.outgoings.isEmpty()) "Nothing is due before payday."
                    else "${UiFormat.count(timeline.outgoings.size, "payment")} before payday. Everyday spending isn't in here; Safe today covers that.",
                    style = Type.caption, color = c.onHeroMuted,
                )
            }

            timeline.byDay.forEach { (date, events) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(dayLabel(date, today))
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        events.forEachIndexed { i, event ->
                            if (i > 0) Divider()
                            EventRow(event, snapshot, hidden)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.lime).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Payday", style = Type.bodyExtra, color = c.onLime)
                    Text(dayLabel(timeline.payday.date, today), style = Type.captionBold, color = c.onLime)
                }
                Text(
                    if (timeline.payday.amount > 0) "About ${Money.display(timeline.payday.amount, hidden)} usually comes in, and a fresh cycle starts."
                    else "A fresh cycle starts.",
                    style = Type.caption, color = c.onLime,
                )
            }
        }
    }
}

/** "Today", "Tomorrow", else weekday and date: "Sat 28 Nov". */
private fun dayLabel(date: java.time.LocalDate, today: java.time.LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + UiFormat.dayMonth(date)
}

@Composable
private fun EventRow(event: TimelineEvent, snapshot: Snapshot, hidden: Boolean) {
    val c = Leftover.colors
    val icon = when (event.kind) {
        TimelineKind.GOAL -> LeftoverIcons.Flag
        TimelineKind.EMI, TimelineKind.CARD -> LeftoverIcons.Card
        TimelineKind.BILL -> snapshot.bills.firstOrNull { it.name == event.title }?.let { LeftoverIcons.forKey(it.icon) } ?: LeftoverIcons.Receipt
        TimelineKind.PAYDAY -> LeftoverIcons.Income
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, c.track, c.ink, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(event.title, style = Type.bodyStrong, color = c.ink, maxLines = 1)
            Text(
                when (event.kind) {
                    TimelineKind.GOAL -> "Set aside for this goal"
                    TimelineKind.BILL -> "Bill"
                    TimelineKind.EMI -> "EMI"
                    TimelineKind.CARD -> "Card bill"
                    TimelineKind.PAYDAY -> "Payday"
                },
                style = Type.small, color = c.muted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("−" + Money.display(event.amount, hidden), style = Type.captionBold, color = c.ink)
            Box { Text(Money.display(event.after, hidden) + " after", style = Type.small, color = if (event.after < 0) c.emberText else c.muted) }
        }
    }
}
