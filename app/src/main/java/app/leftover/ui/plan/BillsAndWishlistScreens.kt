package app.leftover.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import app.leftover.notify.OneShotReminders
import app.leftover.ui.components.rememberNotificationGate
import app.leftover.ui.components.Banner
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.LeftoverRepository
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.WishEntity
import app.leftover.data.db.WishStatus
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
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.Segmented
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BillsScreen(
    snapshot: Snapshot, onSelectTab: (String) -> Unit, onAddBill: () -> Unit, onMarkPaid: (BillEntity, LocalDate) -> Unit,
    onOpenEmi: (Long) -> Unit, onAddEmi: () -> Unit, onMarkEmiPaid: (app.leftover.data.db.EmiEntity, LocalDate) -> Unit,
    onKeepSubscription: (Long) -> Unit,
) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val hidden = snapshot.settings.hideBalances
    var tab by rememberSaveable { mutableStateOf("Upcoming") }
    val upcoming = snapshot.bills.map { it to ledger.billNextDue(it) }.sortedBy { it.second }
    val beforePayday = upcoming.filter { !it.second.isAfter(snapshot.cycle.nextPayday) }
    val subscriptions = snapshot.bills.filter { it.isSubscription }
    val monthly = subscriptions.sumOf { it.amount }

    PlanScaffold(current = Routes.BILLS, onSelectTab = onSelectTab) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).clip(Radius.lg).background(c.hero).padding(14.dp)) {
                Text("Before payday", style = Type.smallBold, color = c.onHeroMuted)
                Amount(beforePayday.sumOf { it.first.amount }, hidden, Type.h2, color = c.heroNumber)
                Text("${UiFormat.count(beforePayday.size, "payment")} · ${UiFormat.count(snapshot.cycle.daysUntilPayday(snapshot.today), "day")}", style = Type.small, color = c.onHeroMuted)
            }
            Column(Modifier.weight(1f).clip(Radius.lg).background(c.surface).border(1.dp, c.hairline, Radius.lg).padding(14.dp)) {
                Text("All bills / month", style = Type.smallBold, color = c.muted)
                Amount(snapshot.bills.sumOf { it.amount }, hidden, Type.h2, color = c.ink)
                Text(UiFormat.count(snapshot.bills.size, "payment"), style = Type.small, color = c.muted)
            }
        }
        Segmented(listOf("Upcoming", "Subscriptions", "EMIs"), tab, { tab = it })

        if (tab == "Upcoming") {
            if (upcoming.isEmpty()) EmptyCard("No bills yet", "Add rent, recharges and EMIs so they never surprise you.")
            upcoming.forEach { (bill, due) ->
                val soon = !due.isAfter(snapshot.today.plusDays(1))
                val from = snapshot.accounts.firstOrNull { it.id == bill.accountId } ?: snapshot.accounts.firstOrNull { it.type == AccountType.BANK }
                val canPay = from != null && !due.isAfter(snapshot.today.plusDays(7))
                val remind = if (bill.remindDaysBefore == 0) "Reminds same day" else "Reminds ${bill.remindDaysBefore}d before"
                Card(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(
                            Modifier.size(52.dp).clip(Radius.sm).background(if (soon) c.emberBg else c.surfaceAlt),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(due.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), style = Type.smallBold, color = if (soon) c.emberText else c.muted)
                            Text("${due.dayOfMonth}", style = Type.title, color = if (soon) c.emberText else c.ink)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(bill.name, style = Type.bodyStrong, color = c.ink, maxLines = 1)
                            Text(UiFormat.shortDay(due, snapshot.today), style = Type.smallBold, color = if (soon) c.emberText else c.muted)
                            Text(remind, style = Type.small, color = c.muted)
                        }
                        Amount(bill.amount, hidden, Type.bodyExtra, color = c.ink)
                    }
                    if (canPay) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Radius.sm).background(c.surfaceAlt).clickable(role = Role.Button) { onMarkPaid(bill, due) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Mark as paid", style = Type.captionBold, color = c.ink) }
                    }
                }
            }
        } else if (tab == "EMIs") {
            EmiList(snapshot, onOpenEmi, onAddEmi, onMarkEmiPaid)
        } else {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.lavender).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Subscriptions", style = Type.captionBold, color = c.lavenderInk)
                Text("${Money.display(monthly, hidden)} / month", style = Type.h2, color = c.lavenderInk)
                Text("${Money.display(monthly * 12, hidden)} a year", style = Type.caption, color = c.lavenderMuted)
            }
            val context = LocalContext.current
            var nudgeMessage by rememberSaveable { mutableStateOf<String?>(null) }
            val gate = rememberNotificationGate(onDenied = { nudgeMessage = "Notifications are off, so a reminder can't show. Turn them on in Android settings." })
            val nudge = subscriptions
                .filter { it.id.toString() !in snapshot.settings.keptSubscriptions && !ledger.billNextDue(it).isAfter(snapshot.today.plusDays(10)) }
                .minByOrNull { ledger.billNextDue(it) }
            if (nudge != null) {
                val renews = ledger.billNextDue(nudge)
                val days = java.time.temporal.ChronoUnit.DAYS.between(snapshot.today, renews).toInt()
                Card(padding = PaddingValues(16.dp), modifier = Modifier.border(1.5.dp, c.ink, Radius.lg)) {
                    Text(if (days <= 0) "Renews today" else "Renews in ${UiFormat.count(days, "day")}", style = Type.label, color = c.muted)
                    Text("${nudge.name} · ${Money.display(nudge.amount, hidden)} / month", style = Type.bodyExtra, color = c.ink, modifier = Modifier.padding(top = 4.dp))
                    Text("Still using it? Cancelling would save ${Money.display(nudge.amount * 12, hidden)} a year.", style = Type.caption, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LButton("Keep it", { onKeepSubscription(nudge.id) }, modifier = Modifier.weight(1f), height = 48.dp)
                        LButton("Remind me to cancel", {
                            gate {
                                OneShotReminders.schedule(context, "cancel-${nudge.id}", "Cancel ${nudge.name}?", "It renews ${UiFormat.dayMonth(renews)}. This is your reminder to cancel it if you don't need it.", "bills", OneShotReminders.nineAm(maxOf(renews.minusDays(1), snapshot.today)))
                                nudgeMessage = "Okay. Leftover will remind you before ${nudge.name} renews."
                                onKeepSubscription(nudge.id)
                            }
                        }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                    }
                }
            }
            nudgeMessage?.let { Text(it, style = Type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp)) }
            if (subscriptions.isEmpty()) EmptyCard("No subscriptions tracked", "Mark a bill as a subscription when you add it and it'll show up here.")
            Card(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                subscriptions.forEachIndexed { index, bill ->
                    if (index > 0) Divider()
                    val due = ledger.billNextDue(bill)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp).clip(Radius.sm).background(CategoryPalette.of("rose", c.isDark).tile), contentAlignment = Alignment.Center) {
                            Icon(LeftoverIcons.forKey(bill.icon), null, tint = c.ink, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(bill.name, style = Type.bodyStrong, color = c.ink)
                            Text("Renews ${UiFormat.dayMonth(due)}", style = Type.small, color = c.muted)
                        }
                        Amount(bill.amount, hidden, Type.bodyExtra, color = c.ink)
                    }
                }
            }
        }
        if (tab != "EMIs") LButton("Add a bill or subscription", onAddBill, kind = ButtonKind.Outline, icon = LeftoverIcons.Plus, height = 52.dp)
    }
}


@Composable
private fun EmptyCard(title: String, body: String) {
    val c = Leftover.colors
    Card(padding = PaddingValues(20.dp)) {
        Text(title, style = Type.bodyExtra, color = c.ink)
        Text(body, style = Type.caption, color = c.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBillScreen(snapshot: Snapshot, onClose: () -> Unit, onSave: (BillEntity) -> Unit) {
    val c = Leftover.colors
    val presets = listOf("Rent" to 9000L, "Recharge" to 349L, "Electricity" to 2728L, "EMI" to 2250L, "OTT" to 199L, "Wi-Fi" to 799L, "Gym" to 2000L, "Other" to 0L)
    var preset by rememberSaveable { mutableStateOf("Wi-Fi") }
    var amountDigits by rememberSaveable { mutableStateOf("799") }
    var dueDay by rememberSaveable { mutableStateOf("5") }
    var remind by rememberSaveable { mutableStateOf(3) }
    var subscription by rememberSaveable { mutableStateOf(false) }
    var otherName by rememberSaveable { mutableStateOf("") }
    val payFrom = snapshot.accounts.filter { !it.archived && it.type != AccountType.SAVINGS }
    var accountId by rememberSaveable { mutableStateOf(payFrom.firstOrNull { it.type == AccountType.BANK }?.id ?: payFrom.firstOrNull()?.id) }
    val amount = amountDigits.toLongOrNull() ?: 0L
    val day = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: 1

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Add a bill", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (name, value) ->
                    Chip(name, preset == name, { preset = name; if (value > 0) amountDigits = value.toString(); if (name == "OTT") subscription = true }, icon = LeftoverIcons.forKey(LeftoverRepository.billIcon(name)))
                }
            }
            if (preset == "Other") LabeledField("What is it?", otherName, "Name this bill", { otherName = it.take(24) }, androidx.compose.ui.text.input.KeyboardType.Text, capitalize = true)
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                NumberRow("Amount", "₹", amountDigits, "0") { amountDigits = it.take(7) }
                Divider()
                NumberRow("Due on day", "", dueDay, "5") { dueDay = it.take(2) }
            }
            if (payFrom.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Paid from")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        payFrom.forEach { account -> Chip(account.name, accountId == account.id, { accountId = account.id }) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Remind me")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Same day" to 0, "1 day" to 1, "3 days" to 3, "7 days" to 7).forEach { (label, days) -> Chip(label, remind == days, { remind = days }, modifier = Modifier.weight(1f)) }
                }
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow("It's a subscription", "Shows up under Subscriptions with a yearly total", subscription, { subscription = it })
            }
        }
        LButton(
            text = "Add ${if (preset == "Other") "bill" else preset}", enabled = amount > 0,
            onClick = {
                onSave(BillEntity(name = if (preset == "Other") otherName.trim().ifBlank { "Bill" } else preset, amount = rupees(amount), dueDay = day, icon = LeftoverRepository.billIcon(preset), accountId = accountId, remindDaysBefore = remind, isSubscription = subscription))
                onClose()
            },
        )
    }
}

@Composable
private fun NumberRow(label: String, prefix: String, value: String, hint: String, onChange: (String) -> Unit) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.caption, color = c.muted, modifier = Modifier.width(110.dp))
        if (prefix.isNotEmpty()) Text(prefix, style = Type.title, color = c.muted, modifier = Modifier.padding(end = 4.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = Type.title, color = c.subtle)
            BasicTextField(
                value, { onChange(it.filter(Char::isDigit)) }, textStyle = Type.title.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun WishlistScreen(snapshot: Snapshot, onSelectTab: (String) -> Unit, onAdd: (WishEntity) -> Unit, onResolve: (WishEntity, WishStatus) -> Unit, onPostpone: (WishEntity, Int) -> Unit, onBuy: () -> Unit, onRestore: (WishEntity) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val now = System.currentTimeMillis()
    val kept = snapshot.wishes.filter { it.status == WishStatus.SKIPPED }.sumOf { it.price }
    val skipped = snapshot.wishes.count { it.status == WishStatus.SKIPPED }
    val bought = snapshot.wishes.count { it.status == WishStatus.BOUGHT }
    val waiting = snapshot.wishes.filter { it.status == WishStatus.WAITING }
    val ready = waiting.filter { it.decideAt <= now }
    val cooling = waiting.filter { it.decideAt > now }
    var name by rememberSaveable { mutableStateOf("") }
    var priceDigits by rememberSaveable { mutableStateOf("") }
    var cooldown by rememberSaveable { mutableStateOf(7) }
    // The last decision, with the wish as it was before, so it can be undone.
    var undo by remember { mutableStateOf<Pair<WishEntity, String>?>(null) }

    PlanScaffold(current = Routes.WISHLIST, onSelectTab = onSelectTab) {
        undo?.let { (before, message) ->
            Banner(message, c.lavender, c.lavenderInk, trailing = "Undo", onClick = { onRestore(before); undo = null })
        }
        Row(Modifier.fillMaxWidth().clip(Radius.lg).background(c.lime).padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Kept by waiting", style = Type.captionBold, color = c.onLime)
                Amount(kept, hidden, Type.h1, color = c.onLime)
            }
            Text("$skipped skipped\n$bought bought", style = Type.captionBold, color = c.onLime, textAlign = TextAlign.End)
        }

        ready.forEach { wish ->
            Card(shape = Radius.lg, padding = PaddingValues(16.dp), modifier = Modifier.border(1.5.dp, c.ink, Radius.lg)) {
                Text("COOLDOWN OVER · DECIDE", style = Type.label, color = c.ink)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(wish.name, style = Type.title, color = c.ink)
                    Amount(wish.price, hidden, Type.title, color = c.ink)
                }
                Text("Saved ${UiFormat.count(((now - wish.addedAt) / 86_400_000L).toInt(), "day")} ago. Buying now leaves ${Money.display(snapshot.ledger.leftToSpend.left - wish.price, hidden)} to spend until payday.", style = Type.caption, color = c.muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LButton("Don't need it", { onResolve(wish, WishStatus.SKIPPED); undo = wish to "Removed. ${Money.display(wish.price, hidden)} added to money kept." }, modifier = Modifier.weight(1f), height = 48.dp)
                    LButton("Wait 7 days", { onPostpone(wish, 7); undo = wish to "Okay. We'll ask again in 7 days." }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                    LButton("Buy", { onResolve(wish, WishStatus.BOUGHT); onBuy() }, modifier = Modifier.weight(0.7f), kind = ButtonKind.Outline, height = 48.dp)
                }
            }
        }

        if (cooling.isNotEmpty()) {
            Text("Cooling down", style = Type.title, color = c.ink)
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                cooling.forEachIndexed { index, wish ->
                    if (index > 0) Divider()
                    val total = (wish.decideAt - wish.addedAt).coerceAtLeast(1)
                    val elapsed = ((now - wish.addedAt).toFloat() / total).coerceIn(0f, 1f)
                    val daysLeft = (((wish.decideAt - now) + 86_399_999L) / 86_400_000L).toInt()
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(wish.name, style = Type.bodyStrong, color = c.ink)
                            Amount(wish.price, hidden, Type.bodyExtra, color = c.ink)
                        }
                        ProgressBar(elapsed, height = 6.dp, color = c.lavenderMuted)
                        Text("$daysLeft ${if (daysLeft == 1) "day" else "days"} left", style = Type.smallBold, color = c.muted)
                    }
                }
            }
        }

        Card(padding = PaddingValues(16.dp)) {
            Text("Add something you want", style = Type.bodyExtra, color = c.ink)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldBox(name, "Item name", { name = it.take(30) }, Modifier.weight(1f), false)
                FieldBox(priceDigits, "₹ Price", { priceDigits = it.filter(Char::isDigit).take(7) }, Modifier.width(110.dp), true)
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Decide after")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("24 h" to 1, "3 days" to 3, "7 days" to 7, "30 days" to 30).forEach { (label, days) -> Chip(label, cooldown == days, { cooldown = days }, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
            LButton(
                "Add to wishlist", enabled = name.isNotBlank() && (priceDigits.toLongOrNull() ?: 0L) > 0, height = 48.dp,
                onClick = {
                    onAdd(WishEntity(name = name.trim(), price = rupees(priceDigits.toLong()), decideAt = System.currentTimeMillis() + cooldown * 86_400_000L))
                    name = ""; priceDigits = ""
                },
            )
        }
    }
}

@Composable
private fun FieldBox(value: String, hint: String, onChange: (String) -> Unit, modifier: Modifier, numeric: Boolean) {
    val c = Leftover.colors
    Box(modifier.height(48.dp).clip(Radius.sm).background(c.surfaceAlt).border(1.dp, c.border, Radius.sm).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, style = Type.bodyStrong, color = c.subtle)
        BasicTextField(
            value, onChange, textStyle = Type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text), modifier = Modifier.fillMaxWidth(),
        )
    }
}
