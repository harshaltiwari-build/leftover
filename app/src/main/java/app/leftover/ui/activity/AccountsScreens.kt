package app.leftover.ui.activity

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
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.domain.Money
import app.leftover.domain.rupees
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Card
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ListRow
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

@Composable
fun AccountsScreen(snapshot: Snapshot, onBack: () -> Unit, onAdd: () -> Unit, onAddCash: () -> Unit, onOpenCard: (Long) -> Unit, onOpenCash: (Long) -> Unit, onOpenGoals: () -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val hidden = snapshot.settings.hideBalances
    var showNetWorth by rememberSaveable { mutableStateOf(false) }
    val left = ledger.leftToSpend
    val cardsOwed = snapshot.accounts.filter { it.type == AccountType.CARD }.sumOf { ledger.cardOwed(it) }
    val netWorth = snapshot.accounts.sumOf { ledger.balance(it.id) }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Accounts", style = Type.h2, color = c.ink, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(Radius.md).background(c.hero).clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(LeftoverIcons.Plus, "Add account", tint = c.heroNumber, modifier = Modifier.size(20.dp)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.xl).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("In spending accounts", style = Type.caption, color = c.onHeroMuted)
                        Amount(left.inAccounts, hidden, Type.display36, color = c.heroNumber)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Card owed", style = Type.caption, color = c.onHeroMuted)
                        Amount(cardsOwed, hidden, Type.title, color = c.onHero)
                    }
                }
                Text(
                    if (showNetWorth) "Hide net worth" else "Show net worth", style = Type.smallBold, color = c.onHero,
                    modifier = Modifier.clip(Radius.pill).border(1.dp, c.onHeroMuted, Radius.pill).clickable(role = Role.Button) { showNetWorth = !showNetWorth }.padding(horizontal = 14.dp, vertical = 12.dp),
                )
                if (showNetWorth) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net worth · all accounts − card owed", style = Type.caption, color = c.onHeroMuted)
                        Amount(netWorth, hidden, Type.bodyExtra, color = c.onHero)
                    }
                }
            }

            AccountGroup("Bank & UPI", snapshot.accounts.filter { it.type == AccountType.BANK }, snapshot, hidden)
            val wallets = snapshot.accounts.filter { it.type == AccountType.CASH }
            if (wallets.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Cash")
                    Card { ListRow(onClick = onAddCash, leading = { Box(Modifier.size(42.dp).clip(Radius.sm).background(c.track), contentAlignment = Alignment.Center) { Icon(LeftoverIcons.Cash, null, tint = c.ink, modifier = Modifier.size(20.dp)) } }, title = "Add a cash wallet", subtitle = "Track ATM withdrawals and what you spend in cash") }
                }
            } else AccountGroup("Cash", wallets, snapshot, hidden, onOpenCash = onOpenCash)
            AccountGroup("Savings for goals", snapshot.accounts.filter { it.type == AccountType.SAVINGS }, snapshot, hidden, onOpenGoals = onOpenGoals)
            AccountGroup("Credit cards", snapshot.accounts.filter { it.type == AccountType.CARD }, snapshot, hidden, onOpenCard)

            Row(Modifier.fillMaxWidth().clip(Radius.md).background(c.track).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(LeftoverIcons.Info, null, tint = c.ink, modifier = Modifier.size(18.dp))
                Text("Card bill payments are saved as transfers. Your purchases on the card were already counted, so they're never counted twice.", style = Type.caption, color = c.ink)
            }
        }
    }
}

@Composable
private fun AccountGroup(title: String, accounts: List<AccountEntity>, snapshot: Snapshot, hidden: Boolean, onOpenCard: (Long) -> Unit = {}, onOpenCash: (Long) -> Unit = {}, onOpenGoals: () -> Unit = {}) {
    if (accounts.isEmpty()) return
    val c = Leftover.colors
    val ledger = snapshot.ledger
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Card {
            accounts.forEachIndexed { index, account ->
                if (index > 0) Divider()
                val balance = ledger.balance(account.id)
                ListRow(
                    onClick = when (account.type) { AccountType.CARD -> ({ onOpenCard(account.id) }); AccountType.CASH -> ({ onOpenCash(account.id) }); AccountType.SAVINGS -> onOpenGoals; else -> null },
                    leading = {
                        Box(Modifier.size(42.dp).clip(Radius.sm).background(c.track), contentAlignment = Alignment.Center) {
                            Icon(
                                when (account.type) {
                                    AccountType.BANK -> LeftoverIcons.Bank
                                    AccountType.CASH -> LeftoverIcons.Cash
                                    AccountType.CARD -> LeftoverIcons.Card
                                    AccountType.SAVINGS -> LeftoverIcons.Savings
                                },
                                null, tint = c.ink, modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    title = account.name,
                    subtitle = when {
                        account.type == AccountType.CARD && ledger.cardCycle(account) != null -> ledger.cardCycle(account)!!.let { cycle ->
                            if (cycle.billed > 0) "Bill due ${UiFormat.dayMonth(cycle.billDue)} · ${UiFormat.count(cycle.interestFreeDays, "day")} interest-free" else "${UiFormat.count(cycle.interestFreeDays, "day")} interest-free today"
                        }
                        account.type == AccountType.CARD && account.dueDay != null -> "Due on the ${account.dueDay}${suffix(account.dueDay)} · tap to add the statement day"
                        account.type == AccountType.CARD -> "Tap to add the billing days"
                        account.type == AccountType.CASH -> "Tap to take cash out or count it"
                        account.type == AccountType.SAVINGS -> "Holds ${UiFormat.count(snapshot.goals.size, "goal")} · not in Left to spend"
                        !account.includeInLeft -> "Not counted in Left to spend"
                        else -> "Counted in Left to spend"
                    },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Amount(if (account.type == AccountType.CARD) ledger.cardOwed(account) else balance, hidden, Type.bodyExtra, color = c.ink)
                            if (account.type == AccountType.CARD) Text("owed", style = Type.small, color = c.muted)
                        }
                    },
                )
            }
        }
    }
}

private fun suffix(day: Int) = if (day in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

@Composable
fun AddAccountScreen(onClose: () -> Unit, onSave: (AccountEntity) -> Unit, initialType: AccountType = AccountType.BANK) {
    val c = Leftover.colors
    var type by rememberSaveable { mutableStateOf(initialType) }
    var name by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var dueDay by rememberSaveable { mutableStateOf("") }
    var statementDay by rememberSaveable { mutableStateOf("") }
    var include by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val defaults = mapOf(
        AccountType.BANK to Triple("Bank account", "Savings, salary, UPI", "Current balance"),
        AccountType.CASH to Triple("Cash", "Wallet, piggy bank", "Cash in hand"),
        AccountType.CARD to Triple("Credit card", "Tracks what you owe", "Amount owed now"),
        AccountType.SAVINGS to Triple("Goal savings", "Money kept aside", "Saved so far"),
    )
    val counts = include ?: (type != AccountType.SAVINGS)
    val amount = rupees(balance.toLongOrNull() ?: 0L)
    val effectiveName = name.ifBlank { defaults.getValue(type).first }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Add an account", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountType.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { option ->
                            val on = option == type
                            val (label, sub, _) = defaults.getValue(option)
                            Column(
                                Modifier.weight(1f).height(76.dp).clip(Radius.md).background(if (on) c.chipSelected else c.surface)
                                    .border(1.dp, if (on) Color.Transparent else c.border, Radius.md)
                                    .clickable(role = Role.RadioButton) { type = option; include = null }.padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(label, style = Type.bodyExtra, color = if (on) c.onChipSelected else c.ink)
                                Text(sub, style = Type.small, color = if (on) c.onChipSelected else c.muted)
                            }
                        }
                    }
                }
            }
            LabeledField("Name", name, defaults.getValue(type).first, { name = it }, KeyboardType.Text)
            LabeledField(defaults.getValue(type).third, balance, "0", { balance = it.filter(Char::isDigit).take(9) }, KeyboardType.Number, prefix = "₹")
            if (type == AccountType.CARD) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Statement day", statementDay, "12", { statementDay = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                    LabeledField("Bill due day", dueDay, "28", { dueDay = it.filter(Char::isDigit).take(2) }, KeyboardType.Number, modifier = Modifier.weight(1f))
                }
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow(
                    title = if (type == AccountType.CARD) "Subtract dues before payday" else "Count in Left to spend",
                    subtitle = when {
                        type == AccountType.CARD -> "Bills due before payday reduce Left to spend"
                        type == AccountType.SAVINGS -> "Off by default — goal money isn't for spending"
                        else -> "Money here is available to spend"
                    },
                    checked = counts, onChange = { include = it },
                )
            }
            Text("We never ask for bank logins or card numbers. Balances update from your entries.", style = Type.small, color = c.muted)
        }
        LButton("Add account", onClick = {
            onSave(
                AccountEntity(
                    name = effectiveName, type = type,
                    openingBalance = if (type == AccountType.CARD) -amount else amount,
                    includeInLeft = counts,
                    dueDay = if (type == AccountType.CARD) dueDay.toIntOrNull()?.coerceIn(1, 31) else null,
                    statementDay = if (type == AccountType.CARD) statementDay.toIntOrNull()?.coerceIn(1, 31) else null,
                ),
            )
            onClose()
        })
    }
}

