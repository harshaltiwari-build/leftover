package app.leftover.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.TxType
import app.leftover.domain.GoalPace
import app.leftover.domain.GoalProgress
import app.leftover.domain.GoalStatus
import app.leftover.domain.KeypadInput
import app.leftover.domain.Money
import app.leftover.domain.rupees
import app.leftover.ui.Routes
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.Keypad
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy")

private fun progressOf(snapshot: Snapshot, goal: GoalEntity): GoalProgress {
    val saved = snapshot.ledger.goalSaved(goal.id)
    return GoalPace.progress(saved, goal.target, goal.perCycle, goal.targetEpochDay?.let(LocalDate::ofEpochDay), snapshot.cycle.nextPayday)
}

private fun statusLine(progress: GoalProgress, hidden: Boolean): Pair<String, Boolean> = when (progress.status) {
    GoalStatus.DONE -> "Reached!" to true
    GoalStatus.ON_TRACK -> "On track · ${progress.finishDate?.format(monthYear)}" to true
    GoalStatus.STEADY -> (progress.finishDate?.let { "At this pace · ${it.format(monthYear)}" } ?: "Nothing set aside yet") to true
    GoalStatus.BEHIND -> "${Money.display(progress.shortfallPerCycle, hidden)} per payday behind" to false
}

private fun coverColor(key: String, dark: Boolean) = CategoryPalette.of(key, dark).tile

@Composable
fun GoalsScreen(snapshot: Snapshot, onSelectTab: (String) -> Unit, onOpenGoal: (Long) -> Unit, onAddGoal: () -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val ledger = snapshot.ledger
    val totalSaved = snapshot.goals.sumOf { ledger.goalSaved(it.id) }
    val reserved = ledger.leftToSpend.goalsTotal

    PlanScaffold(current = Routes.GOALS, onSelectTab = onSelectTab) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Saved across ${snapshot.goals.size} ${if (snapshot.goals.size == 1) "goal" else "goals"}", style = Type.caption, color = c.muted)
                Amount(totalSaved, hidden, Type.h1, color = c.ink)
            }
            if (reserved > 0) {
                Text(
                    "${Money.display(reserved, hidden)} still to set aside this cycle", style = Type.smallBold, color = c.lavenderInk,
                    modifier = Modifier.clip(Radius.pill).background(c.lavender).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (snapshot.goals.isEmpty()) {
            Card(padding = PaddingValues(20.dp)) {
                Text("What are you saving for?", style = Type.h2, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text("A phone, a trip, an emergency fund. Leftover sets money aside each payday so saving feels real, not theoretical.", style = Type.body, color = c.muted)
            }
        }

        snapshot.goals.forEach { goal ->
            val progress = progressOf(snapshot, goal)
            val (status, good) = statusLine(progress, hidden)
            Card(shape = Radius.xl, modifier = Modifier.clickable(role = Role.Button) { onOpenGoal(goal.id) }) {
                Box(Modifier.fillMaxWidth().height(88.dp).background(coverColor(goal.cover, c.isDark)), contentAlignment = Alignment.CenterStart) {
                    Text(
                        if (progress.status == GoalStatus.DONE) "Reached" else "${progress.percent}% there", style = Type.smallBold, color = c.onLime,
                        modifier = Modifier.padding(start = 18.dp).clip(Radius.pill).background(c.lime).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(goal.name, style = Type.title, color = c.ink)
                        Text("${progress.percent}%", style = Type.bodyStrong, color = c.ink)
                    }
                    ProgressBar(progress.fraction, height = 10.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${Money.display(progress.saved, hidden)} of ${Money.display(progress.target, hidden)}", style = Type.caption, color = c.muted)
                        Text(status, style = Type.captionBold, color = if (good) c.positive else c.emberText)
                    }
                }
            }
        }

        LButton("New goal", onAddGoal, kind = ButtonKind.Lime, icon = LeftoverIcons.Plus, height = 52.dp)
    }
}

@Composable
fun GoalDetailScreen(
    snapshot: Snapshot,
    id: Long,
    onBack: () -> Unit,
    onUpdate: (GoalEntity) -> Unit,
    onMoveMoney: (GoalEntity, Long, Long, Boolean) -> Unit,
) {
    val c = Leftover.colors
    val goal = snapshot.goals.firstOrNull { it.id == id }
    val hidden = snapshot.settings.hideBalances
    if (goal == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) { BackButton(onBack) }
        return
    }
    val ledger = snapshot.ledger
    val progress = progressOf(snapshot, goal)
    var monthly by remember(goal.id) { mutableFloatStateOf((goal.perCycle / 100).toFloat()) }
    var moving by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var changingCover by rememberSaveable { mutableStateOf(false) }
    var digits by rememberSaveable { mutableStateOf("") }

    val whatIf = GoalPace.progress(progress.saved, goal.target, rupees(monthly.toLong()), goal.targetEpochDay?.let(LocalDate::ofEpochDay), snapshot.cycle.nextPayday)
    val delta = rupees(monthly.toLong()) - goal.perCycle
    val history = snapshot.transactions.filter { it.goalId == goal.id }.take(6)
    val mainAccount = snapshot.accounts.firstOrNull { it.includeInLeft && it.type != app.leftover.data.db.AccountType.CARD }

    Column(Modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.fillMaxWidth().background(coverColor(goal.cover, c.isDark)).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BackButton(onBack)
                Text(
                    if (changingCover) "Done" else "Change cover", style = Type.captionBold, color = c.ink,
                    modifier = Modifier.heightIn(min = 44.dp).clip(Radius.pill).border(1.dp, c.ink, Radius.pill).clickable(role = Role.Button) { changingCover = !changingCover }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            AnimatedVisibility(visible = changingCover) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("sky", "lime", "lavender", "peach", "mint", "rose").forEach { key ->
                        Box(
                            Modifier.size(44.dp).clip(Radius.pill).background(coverColor(key, c.isDark))
                                .border(if (goal.cover == key) 3.dp else 1.dp, if (goal.cover == key) c.ink else c.ink.copy(alpha = 0.3f), Radius.pill)
                                .clickable(role = Role.RadioButton) { onUpdate(goal.copy(cover = key)) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ProgressRing(progress.fraction)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(goal.name, style = Type.h1, color = c.ink)
                    Text("${Money.display(progress.saved, hidden)} of ${Money.display(progress.target, hidden)}", style = Type.bodyStrong, color = c.ink)
                    Text(statusLine(progress, hidden).first, style = Type.caption, color = c.ink)
                }
            }
            val milestone = when {
                progress.fraction >= 1f -> "You did it. ${goal.name} is fully funded."
                progress.fraction >= 0.5f -> "Halfway there."
                else -> null
            }
            AnimatedVisibility(visible = milestone != null, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
                Text(milestone.orEmpty(), style = Type.bodyExtra, color = c.ink, modifier = Modifier.clip(Radius.pill).background(c.surface.copy(alpha = 0.7f)).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        if (moving != null) {
            val deposit = moving == true
            val movingPaise = KeypadInput.toPaise(digits)
            val tooMuch = !deposit && movingPaise > progress.saved
            Column(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (deposit) "Add money to ${goal.name}" else "Take money out of ${goal.name}", style = Type.title, color = c.ink)
                app.leftover.ui.components.AmountDisplay(digits)
                Text(
                    if (deposit) "Comes from ${mainAccount?.name ?: "your account"} and stops counting toward Left to spend." else "Goes back to ${mainAccount?.name ?: "your account"} and counts toward Left to spend.",
                    style = Type.caption, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                if (tooMuch) {
                    Text("Only ${Money.display(progress.saved, hidden)} is saved here.", style = Type.captionBold, color = c.emberText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.weight(1f))
                Keypad({ digits = KeypadInput.press(digits, it) }, keyHeight = 48.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.imePadding().navigationBarsPadding()) {
                    LButton("Cancel", { moving = null; digits = "" }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline)
                    LButton(
                        if (deposit) "Add" else "Take out", modifier = Modifier.weight(1f), enabled = digits.isNotEmpty() && digits != "0" && mainAccount != null && !tooMuch,
                        onClick = { onMoveMoney(goal, KeypadInput.toPaise(digits), mainAccount!!.id, deposit); moving = null; digits = "" },
                    )
                }
            }
            return@Column
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("What if I save", style = Type.bodyExtra, color = c.ink)
                    Text("${Money.format(rupees(monthly.toLong()))} / payday", style = Type.h2, color = c.ink)
                }
                Slider(
                    value = monthly, onValueChange = { monthly = (it / 500f).toInt() * 500f }, valueRange = 500f..20000f,
                    colors = SliderDefaults.colors(thumbColor = c.chipSelected, activeTrackColor = c.chipSelected, inactiveTrackColor = c.track),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoBox("You'd get it by", whatIf.finishDate?.format(monthYear) ?: "—", c.lime, c.onLime, Modifier.weight(1f))
                    InfoBox("Left to spend impact", if (delta == 0L) "No change" else (if (delta > 0) "−" else "+") + Money.format(kotlin.math.abs(delta)) + "/cycle", c.surfaceAlt, c.ink, Modifier.weight(1f))
                }
                if (delta != 0L) {
                    Spacer(Modifier.height(10.dp))
                    LButton("Use ${Money.format(rupees(monthly.toLong()))} per payday", { onUpdate(goal.copy(perCycle = rupees(monthly.toLong()))) }, height = 48.dp, kind = ButtonKind.Outline)
                }
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow("Auto set-aside on payday", "${Money.display(goal.perCycle, hidden)} reserved every cycle", goal.autoSave, { onUpdate(goal.copy(autoSave = it)) })
            }
            SectionLabel("History")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                if (history.isEmpty()) Text("No money moved yet. Add some to get started.", style = Type.caption, color = c.muted, modifier = Modifier.padding(vertical = 16.dp))
                history.forEachIndexed { index, tx ->
                    if (index > 0) Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(if (tx.type == TxType.GOAL_SAVE) "Added" else "Taken out", style = Type.bodyStrong, color = c.ink)
                            Text(UiFormat.pastDay(UiFormat.date(tx), snapshot.today), style = Type.small, color = c.muted)
                        }
                        Amount(if (tx.type == TxType.GOAL_SAVE) tx.amount else -tx.amount, hidden, Type.bodyExtra, color = if (tx.type == TxType.GOAL_SAVE) c.positive else c.ink, signed = true)
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LButton("Take out", { moving = false }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, enabled = progress.saved > 0)
            LButton("Add money", { moving = true }, modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun InfoBox(label: String, value: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(Radius.md).background(background).padding(12.dp)) {
        Text(label, style = Type.smallBold, color = foreground.copy(alpha = 0.75f))
        Text(value, style = Type.title, color = foreground)
    }
}

@Composable
private fun ProgressRing(fraction: Float) {
    val c = Leftover.colors
    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = if (c.isDark) 0.15f else 0.8f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(c.ink, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fraction * 100).toInt()}%", style = Type.h1, color = c.ink)
            Text("saved", style = Type.smallBold, color = c.ink)
        }
    }
}

@Composable
fun AddGoalScreen(snapshot: Snapshot, onClose: () -> Unit, onSave: (GoalEntity) -> Unit) {
    val c = Leftover.colors
    var name by rememberSaveable { mutableStateOf("") }
    var cover by rememberSaveable { mutableStateOf("sky") }
    var target by rememberSaveable { mutableStateOf(65000L) }
    var months by rememberSaveable { mutableStateOf(8) }
    var template by rememberSaveable { mutableStateOf<String?>(null) }
    val hidden = snapshot.settings.hideBalances
    val targets = if (template != null) listOf(5000L, 10000L, 20000L) else listOf(40000L, 65000L, 90000L)
    val monthChoices = if (template != null) listOf(2, 3, 4) else listOf(5, 8, 14)

    val perCycle = ((target + months - 1) / months + 99) / 100 * 100
    val finish = GoalPace.finishDate(snapshot.cycle.nextPayday, months)
    val others = snapshot.goals.sumOf { it.perCycle / 100 }
    val effectiveName = name.ifBlank { "New goal" }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("New goal", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Start from a festival or occasion")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FestivalFund.all) { fund ->
                        Chip(fund.name, template == fund.name, {
                            if (template == fund.name) template = null
                            else {
                                template = fund.name; name = fund.name; cover = fund.cover; target = fund.target; months = fund.months
                            }
                        })
                    }
                }
            }
            Column(Modifier.fillMaxWidth().height(96.dp).clip(Radius.xl).background(coverColor(cover, c.isDark)).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.Bottom) {
                Text("Saving for", style = Type.smallBold, color = c.ink.copy(alpha = 0.7f))
                androidx.compose.foundation.text.BasicTextField(
                    value = name, onValueChange = { name = it.take(28) }, singleLine = true,
                    textStyle = Type.h2.copy(color = c.ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.ink), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (name.isEmpty()) Text("New laptop", style = Type.h2, color = c.ink.copy(alpha = 0.4f)); inner() },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cover", style = Type.captionBold, color = c.muted)
                listOf("sky", "lime", "lavender", "peach", "mint", "rose").forEach { key ->
                    Box(
                        Modifier.size(44.dp).clip(Radius.pill).background(coverColor(key, c.isDark))
                            .border(if (cover == key) 2.dp else 1.dp, if (cover == key) c.ink else c.border, Radius.pill)
                            .clickable(role = Role.RadioButton) { cover = key },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Target")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { value -> Chip(Money.format(rupees(value)), target == value, { target = value }, modifier = Modifier.weight(1f), center = true) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("By when")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    monthChoices.forEach { m ->
                        Chip(GoalPace.finishDate(snapshot.cycle.nextPayday, m).format(monthYear), months == m, { months = m }, modifier = Modifier.weight(1f), center = true)
                    }
                }
            }
            Card(padding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Set aside each payday", style = Type.bodyStrong, color = c.ink)
                    Text(Money.format(rupees(perCycle)), style = Type.h2, color = c.ink)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "From ${UiFormat.dayMonth(snapshot.cycle.nextPayday)} your Left to spend will be ${Money.format(rupees(perCycle))} lower each cycle. " +
                        if (perCycle > 9000) "That's more than your other goals combined — consider a later date." else if (others > 0) "Your other goals stay on track." else "You'll finish in ${finish.format(monthYear)}.",
                    style = Type.caption, color = c.muted,
                )
            }
        }
        LButton("Start goal", {
            onSave(GoalEntity(name = effectiveName, target = rupees(target), perCycle = rupees(perCycle), cover = cover, targetEpochDay = finish.toEpochDay()))
            onClose()
        })
    }
}

/** Ready-made goals for the occasions that quietly cost a lot. The amount and months are only a starting point. */
data class FestivalFund(val name: String, val cover: String, val target: Long, val months: Int) {
    companion object {
        val all = listOf(
            FestivalFund("Diwali", "peach", 10000L, 3),
            FestivalFund("Eid", "mint", 10000L, 3),
            FestivalFund("Raksha Bandhan", "rose", 5000L, 2),
            FestivalFund("Wedding season", "lavender", 20000L, 4),
            FestivalFund("Birthday gifts", "sky", 5000L, 2),
        )
    }
}
