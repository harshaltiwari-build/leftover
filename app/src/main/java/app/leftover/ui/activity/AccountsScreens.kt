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
import app.leftover.ui.components.SettingsRow
import app.leftover.ui.components.ButtonKind
import app.leftover.domain.AccountForm
import app.leftover.ui.components.ListRow
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

@Composable
fun AccountsScreen(snapshot: Snapshot, onBack: () -> Unit, onAdd: () -> Unit, onAddCash: () -> Unit, onOpenCard: (Long) -> Unit, onOpenCash: (Long) -> Unit, onOpenGoals: () -> Unit, onEdit: (Long) -> Unit = {}) {
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

            val shown = snapshot.accounts.filter { !it.archived }
            AccountGroup("Bank & UPI", shown.filter { it.type == AccountType.BANK }, snapshot, hidden, onEdit)
            val wallets = shown.filter { it.type == AccountType.CASH }
            if (wallets.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Cash")
                    Card { ListRow(onClick = onAddCash, leading = { Box(Modifier.size(42.dp).clip(Radius.sm).background(c.track), contentAlignment = Alignment.Center) { Icon(LeftoverIcons.Cash, null, tint = c.ink, modifier = Modifier.size(20.dp)) } }, title = "Add a cash wallet", subtitle = "Track ATM withdrawals and what you spend in cash") }
                }
            } else AccountGroup("Cash", wallets, snapshot, hidden, onEdit)
            AccountGroup("Savings for goals", shown.filter { it.type == AccountType.SAVINGS }, snapshot, hidden, onEdit)
            AccountGroup("Credit cards", shown.filter { it.type == AccountType.CARD }, snapshot, hidden, onEdit)
            val archived = snapshot.hiddenAccounts
            if (archived.isNotEmpty()) {
                var showArchived by rememberSaveable { mutableStateOf(false) }
                Text(
                    "${UiFormat.count(archived.size, "hidden account")} · ${if (showArchived) "hide" else "show"}", style = Type.captionBold, color = c.muted,
                    modifier = Modifier.clip(Radius.sm).clickable(role = Role.Button) { showArchived = !showArchived }.padding(vertical = 10.dp, horizontal = 4.dp),
                )
                if (showArchived) AccountGroup("Hidden", archived, snapshot, hidden, onEdit)
            }

            Row(Modifier.fillMaxWidth().clip(Radius.md).background(c.track).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(LeftoverIcons.Info, null, tint = c.ink, modifier = Modifier.size(18.dp))
                Text("Card bill payments are saved as transfers. Your purchases on the card were already counted, so they're never counted twice.", style = Type.caption, color = c.ink)
            }
        }
    }
}

@Composable
private fun AccountGroup(title: String, accounts: List<AccountEntity>, snapshot: Snapshot, hidden: Boolean, onEdit: (Long) -> Unit) {
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
                    onClick = { onEdit(account.id) },
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
                        account.archived -> "Hidden · tap to show it again"
                        account.type == AccountType.CARD && account.dueDay != null -> "Due on the ${account.dueDay}${suffix(account.dueDay)} · tap to add the statement day"
                        account.type == AccountType.CARD -> "Tap to add the billing days"
                        account.type == AccountType.CASH -> "Cash in hand"
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

/**
 * Adds an account, or edits one ([existing]). Editing sets today's balance without touching entries, can hide an empty
 * account, and links to the card, cash or goals screen for that account's own actions.
 */
@Composable
fun AddAccountScreen(
    onClose: () -> Unit, onSave: (AccountEntity) -> Unit, initialType: AccountType = AccountType.BANK,
    existing: AccountEntity? = null, currentBalance: Long = 0, accounts: List<AccountEntity> = emptyList(),
    onSetHidden: (AccountEntity, Boolean) -> Unit = { _, _ -> }, onOpenDetail: (() -> Unit)? = null,
) {
    val c = Leftover.colors
    var type by rememberSaveable { mutableStateOf(existing?.type ?: initialType) }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    val shownAmount = if (existing == null) 0L else if (existing.type == AccountType.CARD) -currentBalance else currentBalance
    var balance by rememberSaveable { mutableStateOf(if (existing == null) "" else (shownAmount.coerceAtLeast(0) / 100).toString()) }
    var dueDay by rememberSaveable { mutableStateOf(existing?.dueDay?.toString().orEmpty()) }
    var statementDay by rememberSaveable { mutableStateOf(existing?.statementDay?.toString().orEmpty()) }
    var include by rememberSaveable { mutableStateOf(existing?.includeInLeft) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }

    val defaults = mapOf(
        AccountType.BANK to Triple("Bank account", "Savings, salary, UPI", "Current balance"),
        AccountType.CASH to Triple("Cash", "Wallet, piggy bank", "Cash in hand"),
        AccountType.CARD to Triple("Credit card", "Tracks what you owe", "Amount owed now"),
        AccountType.SAVINGS to Triple("Goal savings", "Money kept aside", "Saved so far"),
    )
    val counts = include ?: (type != AccountType.SAVINGS)

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text(if (existing == null) "Add an account" else "Edit account", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (existing == null) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            LabeledField("Name", name, defaults.getValue(type).first, { name = it.take(AccountForm.MAX_NAME); problem = null }, KeyboardType.Text, capitalize = true)
            LabeledField(defaults.getValue(type).third, balance, "0", { balance = it.filter(Char::isDigit).take(11); problem = null }, KeyboardType.Number, prefix = "₹")
            if (existing != null) Text(
                "Set what it holds today. Your entries stay as they are; the starting balance moves to match.",
                style = Type.small, color = c.muted,
            )
            if (type == AccountType.CARD) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Statement day", statementDay, "12", { statementDay = it.filter(Char::isDigit).take(2); problem = null }, KeyboardType.Number, modifier = Modifier.weight(1f))
                    LabeledField("Bill due day", dueDay, "28", { dueDay = it.filter(Char::isDigit).take(2); problem = null }, KeyboardType.Number, modifier = Modifier.weight(1f))
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
            if (existing != null && onOpenDetail != null) {
                SettingsRow(
                    when (existing.type) { AccountType.CARD -> "Card bills and payments"; AccountType.CASH -> "Take out cash or count it"; else -> "See goals" },
                    null, onOpenDetail, icon = null,
                )
            }
            problem?.let { Text(it, style = Type.captionBold, color = c.emberText) }
            if (existing != null) {
                val why = AccountForm.whyNotHide(existing, currentBalance, accounts)
                if (existing.archived) LButton("Show again", { onSetHidden(existing, false); onClose() }, kind = ButtonKind.Outline, height = 48.dp)
                else {
                    LButton("Hide account", { if (why == null) { onSetHidden(existing, true); onClose() } else problem = why }, kind = ButtonKind.Outline, height = 48.dp)
                    Text(
                        why ?: "Hidden accounts keep their past entries and can be shown again from the accounts list.",
                        style = Type.small, color = c.muted,
                    )
                }
            } else Text("We never ask for bank logins or card numbers. Balances update from your entries.", style = Type.small, color = c.muted)
        }
        LButton(if (existing == null) "Add account" else "Save changes", onClick = {
            when (val result = AccountForm.save(existing, type, currentBalance, name, balance, counts, dueDay, statementDay, accounts)) {
                is AccountForm.Result.Ok -> { onSave(result.account); onClose() }
                is AccountForm.Result.Invalid -> problem = result.message
            }
        })
    }
}
