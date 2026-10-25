package app.leftover.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import app.leftover.domain.GoalImpact
import app.leftover.ui.UiFormat
import app.leftover.ui.theme.LeftoverIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import app.leftover.notify.OneShotReminders
import app.leftover.ui.components.rememberNotificationGate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.WishEntity
import app.leftover.domain.AffordCheck
import app.leftover.domain.Money
import app.leftover.domain.Verdict
import app.leftover.domain.rupees
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

/** Shows what a purchase would do to Left to spend and the category budget before the user commits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AffordScreen(snapshot: Snapshot, onBack: () -> Unit, onAddToWishlist: (WishEntity) -> Unit, onBought: () -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val left = ledger.leftToSpend
    val categories = snapshot.categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden }
    var item by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableFloatStateOf(2999f) }
    var categoryId by rememberSaveable { mutableStateOf(categories.firstOrNull { it.name == "Shopping" }?.id ?: categories.firstOrNull()?.id) }
    val category = categories.firstOrNull { it.id == categoryId }

    val result = AffordCheck.check(
        leftNow = left.left, price = rupees(price.toLong()), daysUntilPayday = left.daysUntilPayday,
        categorySpent = category?.let { ledger.spent(it.id) } ?: 0L, categoryLimit = category?.budgetLimit,
    )
    val (background, foreground) = when (result.verdict) {
        Verdict.COMFORTABLE -> c.lime to c.onLime
        Verdict.STRETCH -> c.lavender to c.lavenderInk
        Verdict.NOT_BEFORE_PAYDAY -> c.emberBg to c.emberText
    }
    val title = when (result.verdict) {
        Verdict.COMFORTABLE -> "Yes, comfortably"
        Verdict.STRETCH -> "You can, but it's a stretch"
        Verdict.NOT_BEFORE_PAYDAY -> "Not before payday"
    }
    val body = when (result.verdict) {
        Verdict.COMFORTABLE -> "You'd still have about ${Money.format(result.perDayAfter)} a day until payday — close to your usual ${Money.format(left.perDay)}."
        Verdict.STRETCH -> buildString {
            if (result.categoryOverBy != null && category != null) append("${category.name} would end up ${Money.format(result.categoryOverBy)} over budget. ")
            append("You'd have ${Money.format(result.perDayAfter)} a day left. Waiting ${left.daysUntilPayday} days for payday costs you nothing.")
        }
        Verdict.NOT_BEFORE_PAYDAY -> "This is ${Money.format(result.shortBy)} more than you have free. It would come out of bills or goal money."
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Can I afford this?", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                SectionLabel("What")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(48.dp).clip(Radius.sm).background(c.surfaceAlt).border(1.dp, c.border, Radius.sm).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        if (item.isEmpty()) Text("Wireless earbuds", style = Type.bodyStrong, color = c.subtle)
                        BasicTextField(item, { item = it.take(30) }, textStyle = Type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat -> Chip(cat.name, cat.id == categoryId, { categoryId = cat.id }) }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel("Price")
                    Text(Money.format(rupees(price.toLong())), style = Type.display36, color = c.ink)
                }
                Slider(
                    value = price, onValueChange = { price = (it / 100f).toInt() * 100f }, valueRange = 500f..8000f,
                    colors = SliderDefaults.colors(thumbColor = c.chipSelected, activeTrackColor = c.chipSelected, inactiveTrackColor = c.track),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(999f, 2999f, 5999f).forEach { preset -> Chip(Money.format(rupees(preset.toLong())), price == preset, { price = preset }, modifier = Modifier.weight(1f)) }
                }
            }

            Column(Modifier.fillMaxWidth().clip(Radius.xl).background(background).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = Type.h2, color = foreground)
                Text(body, style = Type.body, color = foreground)
            }

            val goal = snapshot.goals.filter { ledger.goalSaved(it.id) < it.target }.maxByOrNull { it.perCycle }
            val closer = goal?.let { GoalImpact.daysCloser(rupees(price.toLong()), it.perCycle, snapshot.cycle.totalDays) }
            if (goal != null && closer != null) {
                Row(Modifier.fillMaxWidth().clip(Radius.md).background(c.surfaceAlt).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(LeftoverIcons.Target, null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Text(
                        "Saved instead, ${Money.format(rupees(price.toLong()))} would bring ${goal.name} about ${UiFormat.count(closer, "day")} closer at your current saving rate.",
                        style = Type.caption, color = c.ink,
                    )
                }
            }

            Card(shape = Radius.xl, padding = PaddingValues(horizontal = 18.dp)) {
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Left to spend", style = Type.bodyStrong, color = c.ink)
                        Text("${Money.format(left.left)} → ${Money.format(result.leftAfter)}", style = Type.bodyExtra, color = if (result.leftAfter < 0) c.emberText else c.ink)
                    }
                    ProgressBar(if (left.left > 0) (result.leftAfter.toFloat() / left.left).coerceIn(0f, 1f) else 0f)
                    Text(if (result.leftAfter < 0) "Would dip into money reserved for bills and goals" else "About ${Money.format(result.perDayAfter)} a day after buying", style = Type.small, color = c.muted)
                }
                if (category?.budgetLimit != null) {
                    val spent = ledger.spent(category.id)
                    val limit = category.budgetLimit
                    val overNow = spent > limit
                    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${category.name} budget", style = Type.bodyStrong, color = c.ink)
                            val after = spent + rupees(price.toLong())
                            Text(if (after > limit) "${Money.format(after - limit)} over" else "${Money.format(limit - after)} left", style = Type.bodyExtra, color = if (after > limit) c.emberText else c.ink)
                        }
                        ProgressBar(((spent + rupees(price.toLong())).toFloat() / limit).coerceIn(0f, 1f), color = if (spent + rupees(price.toLong()) > limit) c.ember else c.ink, track = if (overNow) c.emberBg else c.track)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LButton(
                "Add to wishlist · decide in 7 days", enabled = item.isNotBlank(),
                onClick = { onAddToWishlist(WishEntity(name = item.trim(), price = rupees(price.toLong()), categoryId = categoryId, decideAt = System.currentTimeMillis() + 7 * 86_400_000L)) },
            )
            val context = LocalContext.current
            var remindMessage by rememberSaveable { mutableStateOf<String?>(null) }
            val gate = rememberNotificationGate(onDenied = { remindMessage = "Notifications are off, so a reminder can't show. Turn them on in Android settings." })
            val payday = snapshot.cycle.nextPayday
            LButton("Remind me on payday", enabled = item.isNotBlank(), kind = ButtonKind.Outline, height = 52.dp, onClick = {
                gate {
                    OneShotReminders.schedule(context, "afford-${item.trim().lowercase()}", "It's payday", "Still thinking about ${item.trim()}? A good moment to decide.", "afford", OneShotReminders.nineAm(payday))
                    remindMessage = "Okay. Leftover will remind you on ${UiFormat.dayMonth(payday)}."
                }
            })
            remindMessage?.let { Text(it, style = Type.caption, color = c.muted) }
            LButton("Bought it", onBought, kind = ButtonKind.Outline, height = 52.dp)
        }
    }
}
