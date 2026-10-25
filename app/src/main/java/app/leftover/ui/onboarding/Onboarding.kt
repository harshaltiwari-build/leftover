package app.leftover.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.OnboardingAnswers
import app.leftover.domain.KeypadInput
import app.leftover.domain.PayCycle
import app.leftover.ui.Routes
import app.leftover.ui.UiFormat
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SettingsRow
import app.leftover.domain.Money
import app.leftover.domain.rupees
import app.leftover.ui.components.AmountDisplay
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Keypad
import app.leftover.ui.components.LButton
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate

/** What the user has told us so far; kept across the onboarding screens and committed at the end. */
class OnboardingDraft {
    var firstDigits by mutableStateOf("")
    var firstCategory by mutableStateOf<String?>(null)
    var incomeType by mutableStateOf("Salary")
    var incomeDigits by mutableStateOf("")
    var paydayLabel by mutableStateOf("")
    var balanceDigits by mutableStateOf("")
    val billsOn = mutableStateOf(emptySet<String>())

    /** The day of the month chosen, e.g. "5th" is 5. "Last working day" is treated as the 28th and "It varies" as the 1st. */
    val paydayDay: Int get() = paydayLabel.takeWhile { it.isDigit() }.toIntOrNull() ?: if (paydayLabel == "Last working day") 28 else 1

    private val suggestedBills = listOf("Spotify" to 119L, "Recharge" to 349L, "Electricity" to 682L, "Gym" to 2000L, "Card bill" to 2500L, "Rent" to 9000L)
    /** Bills the user typed in with "Other", added after the suggestions and switched on straight away. */
    val customBills = mutableStateOf(emptyList<Pair<String, Long>>())
    val billOptions: List<Pair<String, Long>> get() = suggestedBills + customBills.value
    val billsTotalRupees: Long get() = billOptions.filter { it.first in billsOn.value }.sumOf { it.second }

    fun addCustomBill(name: String, rupees: Long) {
        val clean = name.trim()
        if (clean.isEmpty() || rupees <= 0 || billOptions.any { it.first.equals(clean, true) }) return
        customBills.value = customBills.value + (clean to rupees)
        billsOn.value = billsOn.value + clean
    }
    val balanceRupees: Long get() = balanceDigits.toLongOrNull() ?: 0L

    fun toAnswers() = OnboardingAnswers(
        firstExpenseAmount = KeypadInput.toPaise(firstDigits).takeIf { it > 0 },
        firstExpenseCategory = firstCategory,
        incomeType = incomeType,
        usualIncome = rupees(incomeDigits.toLongOrNull() ?: 0L),
        paydayDay = paydayDay,
        balanceNow = rupees(balanceRupees),
        bills = billOptions.filter { it.first in billsOn.value }.map { it.first to rupees(it.second) },
    )
}

@Composable
private fun ProgressDots(step: Int, total: Int = 3) {
    val c = Leftover.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(Modifier.size(width = 24.dp, height = 6.dp).clip(Radius.pill).background(if (index < step) c.ink else c.border))
        }
    }
}

@Composable
private fun OnboardingFrame(step: Int, onBack: (() -> Unit)?, onSkip: (() -> Unit)?, skipLabel: String = "Skip", content: @Composable ColumnScope.() -> Unit) {
    val c = Leftover.colors
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (onBack != null) BackButton(onBack) else Spacer(Modifier.size(44.dp))
            ProgressDots(step)
            if (onSkip != null) {
                Box(Modifier.height(44.dp).clip(Radius.sm).clickable(role = Role.Button, onClick = onSkip), contentAlignment = Alignment.Center) {
                    Text(skipLabel, style = Type.bodyStrong, color = c.muted, modifier = Modifier.padding(horizontal = 6.dp))
                }
            } else Spacer(Modifier.width(44.dp))
        }
        content()
    }
}


@Composable
fun FirstExpenseScreen(draft: OnboardingDraft, onBack: () -> Unit, onNext: () -> Unit) {
    val c = Leftover.colors
    val categories = listOf("Coffee", "Eating out", "Groceries", "Transport", "Shopping", "Other")
    val ready = draft.firstDigits.isNotEmpty() && draft.firstDigits != "0" && draft.firstCategory != null
    OnboardingFrame(step = 1, onBack = onBack, onSkip = onNext) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What did you last spend on?", style = Type.h1, color = c.ink)
            Text("Try it — this is how fast every entry is.", style = Type.body, color = c.muted)
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(Radius.xl).background(c.surface).border(1.dp, c.hairline, Radius.xl).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            AmountDisplay(draft.firstDigits)
            Spacer(Modifier.height(8.dp))
            Text(
                if (draft.firstDigits.isEmpty()) "Tap the keypad to enter an amount" else "Now pick what it was for",
                style = Type.caption, color = c.muted, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        CategoryGrid(categories, draft.firstCategory) { draft.firstCategory = it }
        Spacer(Modifier.height(16.dp))
        Keypad({ draft.firstDigits = KeypadInput.press(draft.firstDigits, it) }, keyHeight = 60.dp)
        Spacer(Modifier.height(16.dp))
        LButton(
            text = when {
                draft.firstDigits.isEmpty() || draft.firstDigits == "0" -> "Enter an amount"
                draft.firstCategory == null -> "Pick a category"
                else -> "Save ₹${KeypadInput.display(draft.firstDigits)} · ${draft.firstCategory}"
            },
            onClick = onNext, enabled = ready,
        )
    }
}

/** Three equal chips per row, labels centred, so the grid lines up with the keypad below it. */
@Composable
private fun CategoryGrid(items: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { name -> Chip(name, selected == name, { onSelect(name) }, modifier = Modifier.weight(1f), center = true) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RhythmScreen(draft: OnboardingDraft, onBack: () -> Unit, onNext: () -> Unit) {
    val c = Leftover.colors
    val types = listOf("Salary" to "Fixed monthly", "Pocket money" to "From family", "Freelance" to "Irregular", "A mix" to "Salary + side gigs")
    val days = listOf("1st", "5th", "7th", "10th", "15th", "20th", "25th", "28th", "Last working day", "It varies")
    OnboardingFrame(step = 2, onBack = onBack, onSkip = onNext) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What's your money rhythm?", style = Type.h1, color = c.ink)
                Text("We use this to work out what you can spend before your next payday. You can change it any time.", style = Type.body, color = c.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Money comes from")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                    types.forEach { (name, sub) ->
                        val on = draft.incomeType == name
                        Column(
                            Modifier.weight(1f).height(72.dp).clip(Radius.md)
                                .background(if (on) c.chipSelected else c.surface)
                                .border(1.dp, if (on) Color.Transparent else c.border, Radius.md)
                                .clickable(role = Role.Button) { draft.incomeType = name }
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(name, style = Type.bodyStrong, color = if (on) c.onChipSelected else c.ink)
                            Text(sub, style = Type.small, color = if (on) c.onChipSelected else c.muted)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Usual amount each cycle")
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clip(Radius.md).background(c.surface).border(1.dp, c.border, Radius.md).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("₹", style = Type.h1, color = c.muted)
                    Spacer(Modifier.width(6.dp))
                    AmountField(draft.incomeDigits, { draft.incomeDigits = it }, hint = "42,000")
                }
                Text("Freelance income can be added separately when it arrives.", style = Type.caption, color = c.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Payday")
                val dates = days.filter { it.first().isDigit() }
                val others = days - dates.toSet()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dates.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { day -> Chip(day, draft.paydayLabel == day, { draft.paydayLabel = day }, modifier = Modifier.weight(1f), center = true) }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { day -> Chip(day, draft.paydayLabel == day, { draft.paydayLabel = day }, modifier = Modifier.weight(1f), center = true) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(color = c.lavender, bordered = false, padding = PaddingValues(14.dp)) {
            Text(paydaySummary(draft), style = Type.caption, color = c.lavenderInk)
        }
        Spacer(Modifier.height(12.dp))
        LButton("Continue", onNext, enabled = draft.paydayLabel.isNotEmpty())
    }
}

private fun paydaySummary(draft: OnboardingDraft): String = when (draft.paydayLabel) {
    "" -> "Pick your payday and we'll work out your cycle."
    "It varies" -> "We'll use a rolling 30-day cycle and the average of your last 3 incomes."
    else -> {
        val next = app.leftover.domain.PayCycle.forPayday(draft.paydayDay, LocalDate.now()).nextPayday
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), next)
        "Your cycle runs ${draft.paydayDay}${ordinal(draft.paydayDay)} to the day before. Next payday is ${next.dayOfMonth} ${next.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} — in $days days."
    }
}

private fun ordinal(day: Int) = if (day in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "") {
    val c = Leftover.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty() && hint.isNotEmpty()) Text(hint, style = Type.h1, color = c.subtle)
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.filter(Char::isDigit).take(7)) },
            textStyle = Type.h1.copy(color = c.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(c.ink),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BalanceScreen(draft: OnboardingDraft, onBack: () -> Unit, onNext: () -> Unit) {
    val c = Leftover.colors
    val left = draft.balanceRupees - draft.billsTotalRupees
    var adding by rememberSaveable { mutableStateOf(false) }
    var otherName by rememberSaveable { mutableStateOf("") }
    var otherAmount by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val cycle = PayCycle.forPayday(draft.paydayDay, today)
    OnboardingFrame(step = 3, onBack = onBack, onSkip = onNext, skipLabel = "Later") {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Two quick numbers", style = Type.h1, color = c.ink)
                Text("Without these we can't tell you what's safe to spend. Rough is fine.", style = Type.body, color = c.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Money you have right now")
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clip(Radius.md).background(c.surface).border(1.dp, c.border, Radius.md).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("₹", style = Type.h1, color = c.muted)
                    Spacer(Modifier.width(6.dp))
                    AmountField(draft.balanceDigits, { draft.balanceDigits = it }, hint = "12,000")
                }
                Text("Bank + cash together. You can split it into accounts later.", style = Type.caption, color = c.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Bills before ${UiFormat.dayMonth(cycle.nextPayday)}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.billOptions.forEach { (name, amount) ->
                        val on = name in draft.billsOn.value
                        Chip(
                            text = "$name ${Money.format(rupees(amount))}",
                            selected = on,
                            onClick = { draft.billsOn.value = if (on) draft.billsOn.value - name else draft.billsOn.value + name },
                            icon = if (on) LeftoverIcons.Check else LeftoverIcons.Plus,
                        )
                    }
                    Chip(text = "Other", selected = false, onClick = { adding = !adding }, icon = LeftoverIcons.Plus, dashed = true)
                }
                if (adding) {
                    Card(padding = PaddingValues(14.dp)) {
                        LabeledField("What is it?", otherName, "Wi-Fi", { otherName = it.take(20) }, KeyboardType.Text, capitalize = true)
                        Spacer(Modifier.height(10.dp))
                        LabeledField("How much?", otherAmount, "0", { otherAmount = it.filter(Char::isDigit).take(7) }, KeyboardType.Number, prefix = "₹")
                        Spacer(Modifier.height(12.dp))
                        LButton("Add this bill", {
                            draft.addCustomBill(otherName, otherAmount.toLongOrNull() ?: 0)
                            otherName = ""; otherAmount = ""; adding = false
                        }, kind = ButtonKind.Lime, enabled = otherName.isNotBlank() && (otherAmount.toLongOrNull() ?: 0) > 0, height = 48.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your left to spend would be", style = Type.caption, color = c.onHeroMuted)
            Text(Money.format(rupees(left)), style = Type.display36, color = c.heroNumber)
            Text("${Money.format(rupees(draft.balanceRupees))} − ${Money.format(rupees(draft.billsTotalRupees))} in bills", style = Type.caption, color = c.onHeroMuted)
            if (left > 0) Text("About ${Money.format(rupees(left / cycle.daysUntilPayday(today)))} a day until ${UiFormat.dayMonth(cycle.nextPayday)}", style = Type.captionBold, color = c.onHero)
        }
        Spacer(Modifier.height(12.dp))
        LButton("Looks right", onNext)
    }
}

@Composable
fun SetupDoneScreen(draft: OnboardingDraft, onFinish: (next: String?) -> Unit) {
    val c = Leftover.colors
    val left = draft.balanceRupees - draft.billsTotalRupees
    val today = LocalDate.now()
    val payday = PayCycle.forPayday(draft.paydayDay, today).nextPayday
    val firstAmount = KeypadInput.toPaise(draft.firstDigits).takeIf { it > 0 }
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(72.dp).clip(Radius.xl).background(c.lime), contentAlignment = Alignment.Center) {
                Icon(LeftoverIcons.Check, null, tint = c.onLime, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(if (firstAmount != null) "First one, done." else "You're set.", style = Type.h1, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                if (firstAmount != null) "${Money.format(firstAmount)}${draft.firstCategory?.let { " · $it" }.orEmpty()}, saved. That's how fast every entry should feel."
                else "Here's what's safe to spend until payday, built only from what you told us.",
                style = Type.body, color = c.muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().clip(Radius.xl).background(c.hero).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your left to spend until ${UiFormat.dayMonth(payday)}", style = Type.caption, color = c.onHeroMuted)
                Text(Money.format(rupees(left)), style = Type.display48, color = c.heroNumber)
                Text(
                    "${Money.format(rupees(draft.balanceRupees))} you have − ${Money.format(rupees(draft.billsTotalRupees))} in bills you added. Start a goal and we'll set money aside for it.",
                    style = Type.caption, color = c.onHeroMuted,
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Optional next steps")
                Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    SettingsRow("Track automatically", "Log payments from bank texts", { onFinish(Routes.SMS) })
                    Divider()
                    SettingsRow("Start a savings goal", "A phone, a trip, an emergency fund", { onFinish(Routes.ADD_GOAL) })
                    Divider()
                    SettingsRow("Add rent and recharges", "So they never surprise you", { onFinish(Routes.ADD_BILL) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LButton("Go to home", { onFinish(null) })
    }
}
