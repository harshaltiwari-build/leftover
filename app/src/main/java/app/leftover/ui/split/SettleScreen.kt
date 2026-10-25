package app.leftover.ui.split

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountType
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettleMethod
import app.leftover.domain.Money
import app.leftover.domain.UpiLink
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
import app.leftover.ui.theme.Type
import kotlin.math.abs

/**
 * Squares a balance with one friend. Paying by UPI only opens your UPI app with the amount filled in:
 * Leftover can't see whether the payment went through, so it asks you before recording anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettleScreen(
    snapshot: Snapshot,
    personId: Long,
    suggested: Long?,
    onClose: () -> Unit,
    onUpdatePerson: (PersonEntity) -> Unit,
    onRecord: (amount: Long, direction: SettleDirection, method: SettleMethod, accountId: Long) -> Unit,
) {
    val c = Leftover.colors
    val context = LocalContext.current
    val book = snapshot.shared
    val hidden = snapshot.settings.hideBalances
    val person = book.person(personId)
    val balance = book.balanceWith(personId)
    val accounts = snapshot.accounts.filter { !it.archived && (it.type == AccountType.BANK || it.type == AccountType.CASH) }
    // Set once a payment is recorded, so the screen can say what happened instead of just closing.
    var recorded by rememberSaveable { mutableStateOf<Long?>(null) }

    if (person != null && recorded != null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(84.dp).clip(Radius.xl).background(c.lime), contentAlignment = Alignment.Center) {
                Icon(LeftoverIcons.Check, null, tint = c.onLime, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(if (balance == 0L) "You and ${person.name} are settled" else "Recorded", style = Type.h1, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                (if (hidden) "The payment is saved." else "${Money.format(recorded ?: 0L, showPaise = true)} is saved.") +
                    if (balance != 0L) " ${if (hidden) "Some is" else Money.format(abs(balance), showPaise = true) + " is"} still ${if (balance < 0) "owed to ${person.name}" else "owed to you"}." else "",
                style = Type.body, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            LButton("Done", onClose, kind = ButtonKind.Lime)
        }
        return
    }

    if (person == null || balance == 0L) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onClose, "Close", close = true)
            CenteredHint(if (person == null) "This friend isn't here any more." else "You and ${person.name} are all settled up.")
        }
        return
    }

    val youPay = balance < 0
    val owed = abs(balance)
    var amountText by rememberSaveable(personId) { mutableStateOf(Money.toInput(suggested?.coerceAtMost(owed) ?: owed)) }
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull { it.type == AccountType.BANK }?.id ?: accounts.firstOrNull()?.id ?: -1L) }
    var method by rememberSaveable { mutableStateOf(SettleMethod.UPI) }
    var launched by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    var upiDraft by rememberSaveable(personId) { mutableStateOf("") }

    val amount = Money.parse(amountText) ?: 0L
    val amountError = when {
        amountText.isNotBlank() && amount <= 0 -> "Enter an amount above zero"
        amount > owed -> "That's more than the ${Money.format(owed, showPaise = true)} owed"
        else -> null
    }
    val valid = amount in 1..owed && accountId >= 0
    val direction = if (youPay) SettleDirection.YOU_PAID else SettleDirection.THEY_PAID
    val upiDraftError = if (upiDraft.isNotBlank() && !UpiLink.isValidId(upiDraft)) "That doesn't look like a UPI id, for example name@bank" else null

    fun openUpi() {
        val link = person.upiId?.let { UpiLink.build(it, person.name, amount, "Leftover settle up") }
        if (link == null) {
            problem = "Save ${person.name}'s UPI id first."
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            problem = null
            launched = true
        } catch (_: ActivityNotFoundException) {
            problem = "No UPI app found on this phone. You can still record a cash or other payment below."
        }
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Settle up", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(padding = PaddingValues(16.dp)) {
                Text(if (youPay) "You owe ${person.name}" else "${person.name} owes you", style = Type.caption, color = c.muted)
                Text(if (hidden) Money.HIDDEN else Money.format(owed, showPaise = true), style = Type.display36, color = c.ink)
            }

            LabeledField(
                if (youPay) "Amount you're paying" else "Amount you received", amountText,
                "0", { amountText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(10) }, KeyboardType.Decimal, prefix = "₹", error = amountError,
            )

            if (accounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(if (youPay) "Paid from" else "Received in")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { account -> Chip(account.name, accountId == account.id, { accountId = account.id }) }
                    }
                }
            }

            if (launched) {
                Card(color = c.lavender, bordered = false, padding = PaddingValues(16.dp)) {
                    Text("Did the payment go through?", style = Type.bodyExtra, color = c.lavenderInk)
                    Text("Leftover can't see inside your UPI app, so it only records the payment once you confirm.", style = Type.small, color = c.lavenderMuted)
                }
                LButton("Yes, record it", enabled = valid, kind = ButtonKind.Lime, onClick = {
                    onRecord(amount, direction, SettleMethod.UPI, accountId)
                    recorded = amount
                })
                LButton("No, go back", kind = ButtonKind.Outline, height = 48.dp, onClick = { launched = false })
            } else if (youPay) {
                if (person.upiId == null) {
                    LabeledField("${person.name}'s UPI id", upiDraft, "name@bank", { upiDraft = it.trim().take(60) }, KeyboardType.Email, error = upiDraftError)
                    LButton("Save UPI id", enabled = upiDraft.isNotBlank() && upiDraftError == null, kind = ButtonKind.Outline, height = 48.dp, onClick = {
                        onUpdatePerson(person.copy(upiId = upiDraft.trim()))
                    })
                } else {
                    LButton("Pay ${Money.format(amount, showPaise = true)} with UPI", enabled = valid, onClick = ::openUpi)
                }
                problem?.let { Banner(it, c.emberBg, c.emberText) }
                LButton("I paid another way", enabled = valid, kind = ButtonKind.Outline, height = 48.dp, onClick = {
                    onRecord(amount, direction, SettleMethod.CASH, accountId)
                    recorded = amount
                })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("How did ${person.name} pay?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("UPI", method == SettleMethod.UPI, { method = SettleMethod.UPI })
                        Chip("Cash", method == SettleMethod.CASH, { method = SettleMethod.CASH })
                    }
                }
                LButton("Record ${Money.format(amount, showPaise = true)} received", enabled = valid, kind = ButtonKind.Lime, onClick = {
                    onRecord(amount, direction, method, accountId)
                    recorded = amount
                })
                Text("Leftover can't ask a friend for money. Once they've paid you, record it here.", style = Type.small, color = c.muted)
            }
        }
    }
}
