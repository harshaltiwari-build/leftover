package app.leftover.ui.activity

import androidx.compose.foundation.background
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
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.CashCount
import app.leftover.domain.Money
import app.leftover.domain.rupees
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Banner
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Chip
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

/** A cash wallet: take cash out of the bank, and count what is really in it when the numbers drift. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashScreen(
    snapshot: Snapshot,
    id: Long,
    onBack: () -> Unit,
    onAddTransaction: (TransactionEntity) -> Unit,
    onUpdateAccount: (AccountEntity) -> Unit,
) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val wallet = snapshot.accounts.firstOrNull { it.id == id && it.type == AccountType.CASH }
    if (wallet == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onBack)
            CenteredHint("This wallet isn't here any more.")
        }
        return
    }
    val banks = snapshot.accounts.filter { it.type == AccountType.BANK && !it.archived }
    val expected = snapshot.ledger.balance(wallet.id)
    var withdraw by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf(banks.firstOrNull()?.id) }
    var counted by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val countedPaise = counted.toLongOrNull()?.let(::rupees)
    val result = countedPaise?.let { CashCount.compare(expected, it) }
    val other = snapshot.categories.firstOrNull { it.kind == CategoryKind.EXPENSE && it.name.equals("Other", true) }
        ?: snapshot.categories.firstOrNull { it.kind == CategoryKind.EXPENSE }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text(wallet.name, style = Type.h2, color = c.ink, maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("Cash in hand, by your entries", style = Type.caption, color = c.onHeroMuted)
                Amount(expected, hidden, Type.display36, color = c.heroNumber)
            }
            message?.let { Banner(it, c.lavender, c.lavenderInk) }

            SectionLabel("Took cash out at an ATM")
            Card(padding = PaddingValues(16.dp)) {
                if (banks.isEmpty()) {
                    Text("Add a bank account first. A withdrawal moves money from a bank account into this wallet.", style = Type.caption, color = c.muted)
                } else {
                    LabeledField("Amount", withdraw, "0", { withdraw = it.filter(Char::isDigit).take(7) }, KeyboardType.Number, prefix = "₹")
                    Spacer(Modifier.height(12.dp))
                    Text("From", style = Type.captionBold, color = c.muted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        banks.forEach { bank -> Chip(bank.name, from == bank.id, { from = bank.id }) }
                    }
                    Text("It's a transfer, so it isn't counted as spending. What you spend from the cash is.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(12.dp))
                    LButton("Save withdrawal", enabled = (withdraw.toLongOrNull() ?: 0) > 0 && from != null, kind = ButtonKind.Lime, height = 48.dp, onClick = {
                        val amount = rupees(withdraw.toLong())
                        onAddTransaction(TransactionEntity(type = TxType.TRANSFER, amount = amount, accountId = from!!, toAccountId = wallet.id, merchant = "ATM", occurredAt = now))
                        message = "Saved. ${Money.display(amount, hidden)} moved into ${wallet.name}."
                        withdraw = ""
                    })
                }
            }

            SectionLabel("Count your cash")
            Card(padding = PaddingValues(16.dp)) {
                Text("Open your wallet and count. If it doesn't match, Leftover fixes the difference.", style = Type.caption, color = c.muted)
                Spacer(Modifier.height(12.dp))
                LabeledField("What's in it now", counted, "0", { counted = it.filter(Char::isDigit).take(7); message = null }, KeyboardType.Number, prefix = "₹")
                when (result) {
                    null -> {}
                    CashCount.Matches -> Text("That matches. Nothing to change.", style = Type.caption, color = c.positive, modifier = Modifier.padding(top = 10.dp))
                    is CashCount.Missing -> Text("${Money.display(result.amount, hidden)} less than expected. It will be saved as an untracked cash spend.", style = Type.caption, color = c.ink, modifier = Modifier.padding(top = 10.dp))
                    is CashCount.Extra -> Text("${Money.display(result.amount, hidden)} more than expected. The wallet's starting amount goes up to match.", style = Type.caption, color = c.ink, modifier = Modifier.padding(top = 10.dp))
                }
                if (result != null && result != CashCount.Matches) {
                    Spacer(Modifier.height(12.dp))
                    LButton("Fix the difference", kind = ButtonKind.Lime, height = 48.dp, enabled = result !is CashCount.Missing || other != null, onClick = {
                        when (result) {
                            is CashCount.Missing -> onAddTransaction(
                                TransactionEntity(type = TxType.EXPENSE, amount = result.amount, accountId = wallet.id, categoryId = other?.id, merchant = "Untracked cash", occurredAt = now),
                            )
                            is CashCount.Extra -> onUpdateAccount(wallet.copy(openingBalance = wallet.openingBalance + result.amount))
                            CashCount.Matches -> {}
                        }
                        message = "Done. ${wallet.name} now matches what you counted."
                        counted = ""
                    })
                }
            }
        }
    }
}
