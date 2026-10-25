package app.leftover.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CardPerkEntity
import app.leftover.domain.CardRewards
import app.leftover.ui.components.ProgressBar
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.rupees
import app.leftover.ui.components.Banner
import app.leftover.ui.components.Chip
import app.leftover.domain.Money
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

@OptIn(ExperimentalLayoutApi::class)
/** One credit card: what is billed and when it is due, what has been spent since, and how long a purchase today stays interest-free. */
@Composable
fun CardScreen(snapshot: Snapshot, id: Long, onBack: () -> Unit, onSave: (AccountEntity) -> Unit, onAddTransaction: (TransactionEntity) -> Unit, onSavePerk: (CardPerkEntity) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val card = snapshot.accounts.firstOrNull { it.id == id }
    if (card == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onBack)
            CenteredHint("This card isn't here any more.")
        }
        return
    }
    var statement by rememberSaveable(card.id) { mutableStateOf(card.statementDay?.toString().orEmpty()) }
    var due by rememberSaveable(card.id) { mutableStateOf(card.dueDay?.toString().orEmpty()) }
    val cycle = snapshot.ledger.cardCycle(card)
    val owed = snapshot.ledger.cardOwed(card)
    val statementDay = statement.toIntOrNull()?.takeIf { it in 1..31 }
    val dueDay = due.toIntOrNull()?.takeIf { it in 1..31 }
    val changed = statementDay != card.statementDay || dueDay != card.dueDay
    val valid = statement.isBlank() == (statementDay == null) && due.isBlank() == (dueDay == null) && (statementDay == null || dueDay != null)

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text(card.name, style = Type.h2, color = c.ink, maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("Owed on this card", style = Type.caption, color = c.onHeroMuted)
                Amount(owed, hidden, Type.display36, color = c.heroNumber)
            }

            if (cycle != null) {
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    Line("On the last statement", Money.display(cycle.billed, hidden), "${UiFormat.dayMonth(cycle.lastStatement)} statement")
                    Divider()
                    Line("Due", UiFormat.dayMonth(cycle.billDue), UiFormat.shortDay(cycle.billDue, snapshot.today), emphasise = !cycle.billDue.isAfter(snapshot.today.plusDays(3)) && cycle.billed > 0)
                    Divider()
                    Line("Spent since the statement", Money.display(cycle.unbilled, hidden), "Goes on the ${UiFormat.dayMonth(cycle.nextStatement)} statement")
                }
                Column(Modifier.fillMaxWidth().clip(Radius.md).background(c.lavender).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("A purchase today is interest-free for ${UiFormat.count(cycle.interestFreeDays, "day")}", style = Type.bodyExtra, color = c.lavenderInk)
                    Text("It lands on the ${UiFormat.dayMonth(cycle.nextStatement)} statement and is due on ${UiFormat.dayMonth(cycle.nextBillDue)}. Buying just after a statement gives the longest wait.", style = Type.caption, color = c.lavenderMuted)
                }
            } else {
                Column(Modifier.fillMaxWidth().clip(Radius.md).background(c.surfaceAlt).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Add the statement and due days", style = Type.bodyExtra, color = c.ink)
                    Text("Then Leftover can show what is billed, what is due and how long a purchase stays interest-free, and only sets aside the billed part in Left to spend.", style = Type.caption, color = c.muted)
                }
            }

            val banks = snapshot.accounts.filter { it.type == AccountType.BANK && !it.archived }
            if (banks.isNotEmpty() && owed > 0) {
                var payFrom by rememberSaveable(card.id) { mutableStateOf(banks.first().id) }
                var payAmount by rememberSaveable(card.id) { mutableStateOf("") }
                var paid by rememberSaveable(card.id) { mutableStateOf(false) }
                val suggested = (cycle?.billed?.takeIf { it > 0 } ?: owed) / 100
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Record a bill payment")
                    Card(padding = PaddingValues(16.dp)) {
                        LabeledField("Amount paid", payAmount, suggested.toString(), { payAmount = it.filter(Char::isDigit).take(8); paid = false }, KeyboardType.Number, prefix = "₹")
                        Spacer(Modifier.height(12.dp))
                        Text("Paid from", style = Type.captionBold, color = c.muted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            banks.forEach { bank -> Chip(bank.name, payFrom == bank.id, { payFrom = bank.id }) }
                        }
                        Text("Saved as a transfer, so your purchases aren't counted twice.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 10.dp))
                        Spacer(Modifier.height(12.dp))
                        val value = payAmount.toLongOrNull() ?: suggested
                        LButton(if (paid) "Saved" else "Record ${Money.display(rupees(value), hidden)} paid", enabled = value > 0 && !paid, kind = ButtonKind.Lime, height = 48.dp, onClick = {
                            onAddTransaction(TransactionEntity(type = TxType.TRANSFER, amount = rupees(value), accountId = payFrom, toAccountId = card.id, merchant = "${card.name} bill", occurredAt = System.currentTimeMillis()))
                            paid = true
                        })
                    }
                }
            }

            RewardsSection(snapshot, card, onSavePerk)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Billing days")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Statement day", statement, "12", { statement = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                    LabeledField("Due day", due, "1", { due = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                }
                Text("Both are on your card statement or in your bank's app. Day of the month, 1 to 31.", style = Type.small, color = c.muted)
                LButton("Save", enabled = changed && valid, kind = ButtonKind.Lime, height = 52.dp, onClick = { onSave(card.copy(statementDay = statementDay, dueDay = dueDay)) })
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, note: String, emphasise: Boolean = false) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.bodyStrong, color = c.ink)
            Text(note, style = Type.small, color = if (emphasise) c.emberText else c.muted)
        }
        Spacer(Modifier.height(1.dp))
        Text(value, style = Type.bodyExtra, color = if (emphasise) c.emberText else c.ink)
    }
}

/** Credit limit, annual-fee waiver progress and reward points: the three things a card quietly costs or earns. */
@Composable
private fun RewardsSection(snapshot: Snapshot, card: AccountEntity, onSavePerk: (CardPerkEntity) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val perk = snapshot.perks.firstOrNull { it.accountId == card.id }
    val today = snapshot.today
    fun rupeesText(paise: Long?) = paise?.let { (it / 100).toString() }.orEmpty()
    var limit by rememberSaveable(card.id) { mutableStateOf(rupeesText(perk?.creditLimit)) }
    var fee by rememberSaveable(card.id) { mutableStateOf(rupeesText(perk?.annualFee)) }
    var waiver by rememberSaveable(card.id) { mutableStateOf(rupeesText(perk?.waiverSpend)) }
    val start = perk?.yearStartEpochDay?.let(java.time.LocalDate::ofEpochDay)
    var startDay by rememberSaveable(card.id) { mutableStateOf(start?.dayOfMonth?.toString().orEmpty()) }
    var startMonth by rememberSaveable(card.id) { mutableStateOf(start?.monthValue?.toString().orEmpty()) }
    var points by rememberSaveable(card.id) { mutableStateOf(perk?.points?.takeIf { it > 0 }?.toString().orEmpty()) }
    val expiry = perk?.pointsExpiryEpochDay?.let(java.time.LocalDate::ofEpochDay)
    var expDay by rememberSaveable(card.id) { mutableStateOf(expiry?.dayOfMonth?.toString().orEmpty()) }
    var expMonth by rememberSaveable(card.id) { mutableStateOf(expiry?.monthValue?.toString().orEmpty()) }
    var expYear by rememberSaveable(card.id) { mutableStateOf(expiry?.year?.toString().orEmpty()) }

    val waiverInfo = CardRewards.waiver(perk, card.id, snapshot.transactions, today)
    val usedFraction = CardRewards.limitUsed(perk, snapshot.ledger.cardOwed(card))
    val lapse = CardRewards.pointsLapseIn(perk, today)

    fun parseDate(d: String, m: String, y: String): java.time.LocalDate? =
        runCatching { java.time.LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
    val startValue = startDay.toIntOrNull()?.takeIf { it in 1..31 }?.let { d -> startMonth.toIntOrNull()?.takeIf { it in 1..12 }?.let { m -> CardRewards.yearStart(d, m, today) } }
    val expiryValue = parseDate(expDay, expMonth, expYear)
    val feeValid = (startDay.isBlank() && startMonth.isBlank()) || startValue != null
    val expiryValid = (expDay.isBlank() && expMonth.isBlank() && expYear.isBlank()) || expiryValue != null
    val next = CardPerkEntity(
        accountId = card.id,
        annualFee = fee.toLongOrNull()?.let(::rupees),
        waiverSpend = waiver.toLongOrNull()?.let(::rupees),
        yearStartEpochDay = startValue?.toEpochDay(),
        points = points.toLongOrNull() ?: 0,
        pointsExpiryEpochDay = expiryValue?.toEpochDay(),
        creditLimit = limit.toLongOrNull()?.takeIf { it > 0 }?.let(::rupees),
    )
    val changed = next != (perk ?: CardPerkEntity(card.id))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Limit, fee and rewards")
        if (usedFraction != null) {
            Card(padding = PaddingValues(16.dp)) {
                Text("${(usedFraction * 100).toInt()}% of your limit is in use", style = Type.bodyStrong, color = c.ink)
                Spacer(Modifier.height(8.dp))
                ProgressBar(usedFraction.coerceAtMost(1f), color = if (usedFraction >= 0.7f) c.ember else c.ink, track = c.track)
                Text("Using under about 30% of the limit tends to be kinder to your credit score.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (waiverInfo != null) {
            Card(padding = PaddingValues(16.dp)) {
                Text(if (waiverInfo.met) "Fee waiver reached" else "Towards the annual-fee waiver", style = Type.bodyStrong, color = c.ink)
                Spacer(Modifier.height(8.dp))
                ProgressBar((waiverInfo.spent.toFloat() / waiverInfo.target).coerceAtMost(1f), color = if (waiverInfo.met) c.positive else c.ink, track = c.track)
                Text(
                    if (waiverInfo.met) "You've spent ${Money.display(waiverInfo.spent, hidden)} this card year, which is enough."
                    else "${Money.display(waiverInfo.spent, hidden)} of ${Money.display(waiverInfo.target, hidden)} since ${UiFormat.dayMonth(waiverInfo.windowStart)}. ${UiFormat.count(waiverInfo.daysLeft, "day")} left" +
                        if (waiverInfo.perDayNeeded > 0 && !hidden) ", about ${Money.format(waiverInfo.perDayNeeded)} a day on the card." else ".",
                    style = Type.caption, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (lapse != null) {
            Banner("${perk!!.points} reward points lapse in ${UiFormat.count(lapse, "day")}. Worth redeeming.", c.lavender, c.lavenderInk)
        }
        Card(padding = PaddingValues(16.dp)) {
            LabeledField("Credit limit", limit, "50000", { limit = it.filter(Char::isDigit).take(8) }, KeyboardType.Number, prefix = "₹")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Annual fee", fee, "500", { fee = it.filter(Char::isDigit).take(7) }, KeyboardType.Number, prefix = "₹", modifier = Modifier.weight(1f))
                LabeledField("Waived above", waiver, "100000", { waiver = it.filter(Char::isDigit).take(8) }, KeyboardType.Number, prefix = "₹", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text("Card year starts on (day and month of your card's anniversary)", style = Type.captionBold, color = c.muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Day", startDay, "15", { startDay = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                LabeledField("Month", startMonth, "3", { startMonth = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            LabeledField("Reward points", points, "0", { points = it.filter(Char::isDigit).take(8) }, KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            Text("Points expire on (optional)", style = Type.captionBold, color = c.muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Day", expDay, "31", { expDay = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                LabeledField("Month", expMonth, "12", { expMonth = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                LabeledField("Year", expYear, "2026", { expYear = it.filter(Char::isDigit).take(4) }, KeyboardType.Number, modifier = Modifier.weight(1f))
            }
            if (!feeValid || !expiryValid) {
                Text(if (!feeValid) "Enter a real day (1 to 31) and month (1 to 12) for the card year." else "That expiry date doesn't exist.", style = Type.small, color = c.emberText, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            LButton("Save", enabled = changed && feeValid && expiryValid, kind = ButtonKind.Lime, height = 48.dp, onClick = { onSavePerk(next) })
        }
    }
}
