package app.leftover.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountType
import app.leftover.data.db.EmiEntity
import app.leftover.domain.EmiSchedule
import app.leftover.domain.Money
import app.leftover.domain.rupees
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Chip
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate

/** The EMI tab of Bills: one card per plan, with how far along it is and the next instalment. */
@Composable
fun EmiList(snapshot: Snapshot, onOpen: (Long) -> Unit, onAdd: () -> Unit, onMarkPaid: (EmiEntity, LocalDate) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val emis = snapshot.emis
    if (emis.isEmpty()) {
        Card(padding = PaddingValues(20.dp)) {
            Text("No EMIs yet", style = Type.bodyExtra, color = c.ink)
            Text("Add a phone on no-cost EMI or a loan and Leftover keeps each instalment out of what you can spend.", style = Type.caption, color = c.muted)
        }
    } else {
        val monthly = emis.sumOf { it.monthlyAmount }
        val left = emis.sumOf { EmiSchedule.summary(it, snapshot.today).remainingToPay }
        Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.lavender).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("EMIs", style = Type.captionBold, color = c.lavenderInk)
            Text("${Money.display(monthly, hidden)} / month", style = Type.h2, color = c.lavenderInk)
            Text("${Money.display(left, hidden)} still to pay across ${UiFormat.count(emis.size, "plan")}", style = Type.caption, color = c.lavenderMuted)
        }
    }
    emis.forEach { emi ->
        val summary = EmiSchedule.summary(emi, snapshot.today)
        val due = summary.nextDue
        val canPay = due != null && !due.isAfter(snapshot.today.plusDays(7)) && (emi.accountId != null || snapshot.accounts.any { it.type == AccountType.BANK })
        Card(modifier = Modifier.clickable(role = Role.Button) { onOpen(emi.id) }, padding = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(emi.name, style = Type.bodyStrong, color = c.ink, maxLines = 1)
                    Text("${emi.paidMonths} of ${emi.totalMonths} paid · ${UiFormat.count(summary.remainingMonths, "month")} left", style = Type.small, color = c.muted)
                }
                Amount(emi.monthlyAmount, hidden, Type.bodyExtra, color = c.ink)
            }
            Spacer(Modifier.height(10.dp))
            ProgressBar(emi.paidMonths.toFloat() / emi.totalMonths, height = 8.dp)
            if (due != null) {
                Spacer(Modifier.height(10.dp))
                Text("Next ${UiFormat.shortDay(due, snapshot.today)} · ${UiFormat.dayMonth(due)}", style = Type.smallBold, color = if (!due.isAfter(snapshot.today.plusDays(1))) c.emberText else c.muted)
            }
            if (canPay && due != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Radius.sm).background(c.surfaceAlt).clickable(role = Role.Button) { onMarkPaid(emi, due) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { Text("Mark instalment paid", style = Type.captionBold, color = c.ink) }
            }
        }
    }
    LButton("Add an EMI or loan", onAdd, kind = ButtonKind.Outline, icon = LeftoverIcons.Plus, height = 52.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEmiScreen(snapshot: Snapshot, onClose: () -> Unit, onSave: (EmiEntity) -> Unit) {
    val c = Leftover.colors
    var name by rememberSaveable { mutableStateOf("") }
    var monthly by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf("0") }
    var dueDay by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    val accounts = snapshot.accounts.filter { it.type == AccountType.BANK || it.type == AccountType.CASH }
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull { it.type == AccountType.BANK }?.id ?: accounts.firstOrNull()?.id ?: -1L) }

    val monthlyPaise = rupees(monthly.toLongOrNull() ?: 0L)
    val total = months.toIntOrNull() ?: 0
    val done = paid.toIntOrNull() ?: 0
    val day = dueDay.toIntOrNull() ?: 0
    val ratePercent = rate.toDoubleOrNull()
    val paidError = if (total > 0 && done >= total) "That's the whole plan. Leave out finished ones." else null
    val valid = name.isNotBlank() && monthlyPaise > 0 && total in 1..360 && done in 0 until total.coerceAtLeast(1) && day in 1..31 && (rate.isBlank() || ratePercent != null)

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Add an EMI or loan", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledField("What is it?", name, "Phone EMI", { name = it.take(30) }, KeyboardType.Text, capitalize = true)
            LabeledField("Instalment each month", monthly, "0", { monthly = it.filter(Char::isDigit).take(7) }, KeyboardType.Number, prefix = "₹")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Total months", months, "12", { months = it.filter(Char::isDigit).take(3) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                LabeledField("Already paid", paid, "0", { paid = it.filter(Char::isDigit).take(3) }, KeyboardType.Number, modifier = Modifier.weight(1f), error = paidError)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Due day of month", dueDay, "5", { dueDay = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                LabeledField("Interest a year (optional)", rate, "12", { rate = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, KeyboardType.Decimal, modifier = Modifier.weight(1f))
            }
            Text("Leave interest empty for a no-cost EMI. It is only used to work out what paying early would save.", style = Type.small, color = c.muted)
            if (accounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Paid from")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { account -> Chip(account.name, accountId == account.id, { accountId = account.id }) }
                    }
                }
            }
        }
        LButton("Add EMI", enabled = valid, onClick = {
            onSave(
                EmiEntity(
                    name = name.trim(), monthlyAmount = monthlyPaise, totalMonths = total, paidMonths = done, dueDay = day,
                    annualRateBps = ratePercent?.let { (it * 100).toInt() }?.takeIf { it > 0 }, accountId = accountId.takeIf { it >= 0 },
                ),
            )
            onClose()
        })
    }
}

@Composable
fun EmiDetailScreen(snapshot: Snapshot, id: Long, onBack: () -> Unit, onMarkPaid: (EmiEntity, LocalDate) -> Unit, onDelete: (EmiEntity) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val emi = snapshot.emis.firstOrNull { it.id == id }
    var early by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (emi == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onBack)
            CenteredHint("This plan is finished or was deleted.")
        }
        return
    }
    val summary = EmiSchedule.summary(emi, snapshot.today)
    val lump = rupees(early.toLongOrNull() ?: 0L)
    val prepay = EmiSchedule.prepay(emi, lump)
    val due = summary.nextDue

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text(emi.name, style = Type.h2, color = c.ink, maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Still to pay", style = Type.caption, color = c.onHeroMuted)
                Amount(summary.remainingToPay, hidden, Type.display36, color = c.heroNumber)
                ProgressBar(emi.paidMonths.toFloat() / emi.totalMonths, color = c.heroNumber, track = c.onHeroMuted.copy(alpha = 0.35f))
                Text("${emi.paidMonths} of ${emi.totalMonths} instalments paid · ${UiFormat.count(summary.remainingMonths, "month")} left", style = Type.caption, color = c.onHeroMuted)
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                DetailLine("Each month", Money.display(emi.monthlyAmount, hidden))
                due?.let { DetailLine("Next instalment", "${UiFormat.dayMonth(it)} · ${UiFormat.shortDay(it, snapshot.today)}") }
                summary.lastDue?.let { DetailLine("Last instalment", UiFormat.dayMonth(it) + " " + it.year) }
                if (emi.annualRateBps != null) {
                    DetailLine("Interest", "${emi.annualRateBps / 100.0}% a year")
                    DetailLine("Loan still owed", Money.display(summary.outstanding, hidden))
                } else DetailLine("Interest", "None (no-cost)")
            }
            if (due != null) {
                LButton("Mark instalment paid", onClick = { onMarkPaid(emi, due) }, kind = ButtonKind.Lime, enabled = emi.accountId != null || snapshot.accounts.any { it.type == AccountType.BANK })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("What if I pay some off early?")
                LabeledField("Extra payment now", early, "0", { early = it.filter(Char::isDigit).take(7) }, KeyboardType.Number, prefix = "₹")
                if (early.isNotEmpty() && prepay == null) {
                    Text("That's more than is still owed.", style = Type.caption, color = c.emberText)
                }
                prepay?.let { p ->
                    Column(Modifier.fillMaxWidth().clip(Radius.md).background(c.lime).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (p.newRemainingMonths == 0) "That would finish it." else "Done ${UiFormat.count(p.monthsSaved, "month")} sooner",
                            style = Type.bodyExtra, color = c.onLime,
                        )
                        Text(
                            buildString {
                                append("Same instalment, ${UiFormat.count(p.newRemainingMonths, "month")} left.")
                                if (p.interestSaved > 0) append(" About ${Money.display(p.interestSaved, hidden)} less interest.")
                            },
                            style = Type.caption, color = c.onLime,
                        )
                    }
                    Text("A guide from the numbers you entered. Check with your lender for fees and their exact rules.", style = Type.small, color = c.muted)
                }
            }

            if (confirmDelete) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Delete this plan?", style = Type.bodyExtra, color = c.ink)
                    Text("Payments already recorded stay in Activity. This only removes the plan and its reminders.", style = Type.caption, color = c.muted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LButton("Keep it", { confirmDelete = false }, modifier = Modifier.weight(1f), height = 48.dp)
                        LButton("Delete", { onDelete(emi); onBack() }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                    }
                }
            } else {
                Text(
                    "Delete this plan", style = Type.bodyStrong, color = c.emberText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { confirmDelete = true }.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.caption, color = c.muted)
        Text(value, style = Type.bodyStrong, color = c.ink)
    }
}
