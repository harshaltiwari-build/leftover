package app.leftover.ui.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import app.leftover.domain.SafeToday
import app.leftover.domain.AffordAnswer
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
    var typed by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(categories.firstOrNull { it.name == "Shopping" }?.id ?: categories.firstOrNull()?.id) }
    val category = categories.firstOrNull { it.id == categoryId }

    val hidden = snapshot.settings.hideBalances
    val answer = AffordAnswer.of(
        SafeToday.of(ledger), rupees(price.toLong()), snapshot.cycle.nextPayday, category?.name,
        category?.let { ledger.spent(it.id) } ?: 0L, category?.budgetLimit, hidden,
    )
    val (background, foreground) = when (answer.verdict) {
        Verdict.COMFORTABLE -> c.lime to c.onLime
        Verdict.STRETCH -> c.lavender to c.lavenderInk
        Verdict.NOT_BEFORE_PAYDAY -> c.emberBg to c.emberText
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
                    categories.forEach { cat -> Chip(cat.name, cat.id == categoryId, { categoryId = cat.id }, icon = app.leftover.ui.theme.LeftoverIcons.forKey(cat.icon)) }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel("Price")
                    Text(Money.format(rupees(price.toLong())), style = Type.display36, color = c.ink)
                }
                Slider(
                    value = price.coerceIn(500f, 8000f), onValueChange = { price = (it / 100f).toInt() * 100f; typed = "" }, valueRange = 500f..8000f,
                    colors = SliderDefaults.colors(thumbColor = c.chipSelected, activeTrackColor = c.chipSelected, inactiveTrackColor = c.track),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(999f, 2999f, 5999f).forEach { preset -> Chip(Money.format(rupees(preset.toLong())), price == preset, { price = preset; typed = "" }, modifier = Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
                // The slider covers everyday buys; bigger ones (a phone, a trip) are typed.
                app.leftover.ui.components.LabeledField(
                    "Type a price", typed, "25000",
                    { text -> typed = text.filter(Char::isDigit).take(7); typed.toLongOrNull()?.takeIf { it > 0 }?.let { price = it.toFloat() } },
                    androidx.compose.ui.text.input.KeyboardType.Number, prefix = "₹",
                )
            }

            Column(
                Modifier.fillMaxWidth().clip(Radius.xl).background(background).padding(horizontal = 20.dp, vertical = 18.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(answer.title, style = Type.h2, color = foreground)
                Text(answer.body, style = Type.body, color = foreground)
            }

            Card(shape = Radius.xl, padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                answer.rows.forEachIndexed { i, row ->
                    if (i > 0) app.leftover.ui.components.Divider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text(row.name, style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                            Text("${row.from} → ", style = Type.caption, color = c.muted)
                            Text(row.to, style = Type.bodyExtra, color = if (row.warning) c.emberText else c.ink)
                        }
                        Text(row.note, style = Type.small, color = c.muted)
                    }
                }
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

            Column(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LButton(
                "Add to wishlist · decide in 7 days", enabled = item.isNotBlank(),
                onClick = { onAddToWishlist(WishEntity(name = item.trim(), price = rupees(price.toLong()), categoryId = categoryId, decideAt = System.currentTimeMillis() + 7 * 86_400_000L)) },
            )
            val context = LocalContext.current
            var remindMessage by rememberSaveable { mutableStateOf<String?>(null) }
            val gate = rememberNotificationGate(onDenied = { remindMessage = "Notifications are off, so a reminder can't show. Turn them on in Android settings." })
            val payday = snapshot.cycle.nextPayday
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Remind on payday", enabled = item.isNotBlank(), kind = ButtonKind.Outline, height = 50.dp, modifier = Modifier.weight(1f), onClick = {
                    gate {
                        OneShotReminders.schedule(context, "afford-${item.trim().lowercase()}", "It's payday", "Still thinking about ${item.trim()}? A good moment to decide.", "afford", OneShotReminders.nineAm(payday))
                        remindMessage = "Okay. Leftover will remind you on ${UiFormat.dayMonth(payday)}."
                    }
                })
                LButton("Bought it", onBought, kind = ButtonKind.Outline, height = 50.dp, modifier = Modifier.weight(1f))
            }
            remindMessage?.let { Text(it, style = Type.caption, color = c.muted) }
            if (item.isBlank()) Text("Name it to add it to your wishlist or set a reminder.", style = Type.small, color = c.muted)
        }
        }
    }
}
