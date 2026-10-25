package app.leftover.ui.sms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.PendingSmsEntity
import app.leftover.data.db.TxType
import app.leftover.domain.PendingReason
import app.leftover.domain.SmsKind
import app.leftover.domain.toLocalDate
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Banner
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Chip
import app.leftover.ui.components.LButton
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Type

private fun PendingReason.explain(): String = when (this) {
    PendingReason.NEW_SENDER -> "New sender. Allow it on the Automatic tracking screen to save its clean spends on their own."
    PendingReason.NEW_MERCHANT -> "New merchant. Pick a category and Leftover remembers it next time."
    PendingReason.NEW_ACCOUNT -> "Which account is this? Leftover remembers the answer for these digits."
    PendingReason.POSSIBLE_TRANSFER -> "Looks like a card payment or money moving between your own accounts. Skip it unless it's real spending or income."
    PendingReason.POSSIBLE_DUPLICATE -> "You may have added this already. Skip it if you did."
    PendingReason.UNSURE -> "This text wasn't clear enough to read on its own. Check the details."
    PendingReason.CREDIT -> "Money came in. Is it income, a refund, or a friend paying you back?"
    PendingReason.FROM_APP -> "Another app sent this to Leftover. Nothing is saved until you say so."
    PendingReason.ASK_FIRST -> "You asked Leftover to check every payment first. Turn that off in Automatic tracking to save clear ones by themselves."
}

/** Bank texts that need a human: new merchants, new accounts, money coming in, and anything that looks doubtful. */
@Composable
fun ReviewScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onSave: (PendingSmsEntity, TxType, Long?, Long) -> Unit,
    onSkip: (Long) -> Unit,
    onAcceptAll: () -> Unit,
) {
    val c = Leftover.colors
    val ready = snapshot.pendingSms.count { it.kind == SmsKind.DEBIT && it.accountId != null && it.suggestedCategoryId != null && it.reason != PendingReason.POSSIBLE_TRANSFER && it.reason != PendingReason.POSSIBLE_DUPLICATE }
    val autoSaved = snapshot.transactions.count { (it.source == app.leftover.data.db.TxSource.SMS || it.source == app.leftover.data.db.TxSource.NOTIFICATION) }
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("To review", style = Type.h2, color = c.ink)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Auto-saved", autoSaved.toString(), Modifier.weight(1f))
            Stat("Need you", snapshot.pendingSms.size.toString(), Modifier.weight(1f))
        }
        if (ready > 1) {
            Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                LButton("Accept $ready clear suggestions", onAcceptAll, kind = ButtonKind.Lime, height = 48.dp)
            }
        }
        AnimatedVisibility(visible = snapshot.pendingSms.isEmpty(), enter = fadeIn(tween(300)) + expandVertically(tween(300)), exit = fadeOut(tween(150))) {
            Box(Modifier.padding(horizontal = 20.dp)) {
                Card { CenteredHint("All caught up. New bank texts that need your help show up here.") }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(snapshot.pendingSms, key = { it.id }) { item ->
                Box(Modifier.animateItem(fadeInSpec = tween(260), placementSpec = spring(stiffness = 380f), fadeOutSpec = tween(180))) {
                    ReviewCard(snapshot, item, onSave, onSkip)
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Leftover.colors
    Column(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp)) {
        Text(value, style = Type.title, color = c.ink)
        Text(label, style = Type.small, color = c.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(snapshot: Snapshot, item: PendingSmsEntity, onSave: (PendingSmsEntity, TxType, Long?, Long) -> Unit, onSkip: (Long) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val accounts = snapshot.accounts.filter { !it.archived && (it.type == AccountType.BANK || it.type == AccountType.CASH || it.type == AccountType.CARD) }
    val defaultAccount = item.accountId ?: accounts.firstOrNull { it.type == if (item.isCard) AccountType.CARD else AccountType.BANK }?.id
    var accountId by rememberSaveable(item.id) { mutableStateOf(defaultAccount) }
    var categoryId by rememberSaveable(item.id) { mutableStateOf(item.suggestedCategoryId) }
    var type by rememberSaveable(item.id) { mutableStateOf(if (item.kind == SmsKind.DEBIT) TxType.EXPENSE else TxType.INCOME) }

    val needsCategory = type == TxType.EXPENSE || type == TxType.REFUND
    val categories = snapshot.categories.filter { !it.hidden && it.kind == if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
    val canSave = accountId != null && (!needsCategory || categoryId != null)

    Card(modifier = Modifier.animateContentSize(spring(stiffness = 500f)), padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.merchant.ifBlank { "Unknown merchant" }, style = Type.bodyExtra, color = c.ink, maxLines = 1)
                Text("${item.sender} · ${UiFormat.pastDay(item.occurredAt.toLocalDate(), snapshot.today)}${item.last4?.let { " · …$it" }.orEmpty()}", style = Type.small, color = c.muted)
            }
            Amount(item.amount, hidden, Type.h2, color = if (item.kind == SmsKind.CREDIT) c.positive else c.ink, showPaise = true)
        }
        Banner(item.reason.explain(), c.lavender, c.lavenderInk, modifier = Modifier.padding(top = 12.dp))

        if (item.kind == SmsKind.CREDIT) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("What is it?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TxType.INCOME to "Income", TxType.REFUND to "Refund", TxType.REPAYMENT to "Friend paid me back").forEach { (option, label) ->
                        Chip(label, type == option, { type = option; categoryId = null })
                    }
                }
            }
        }
        if (needsCategory || type == TxType.INCOME) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (type == TxType.INCOME) "Category (optional)" else "Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat -> Chip(cat.name, categoryId == cat.id, { categoryId = if (type == TxType.INCOME && categoryId == cat.id) null else cat.id }) }
                }
            }
        }
        if (item.accountId == null || accounts.size > 1) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Account")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account -> Chip(account.name, accountId == account.id, { accountId = account.id }) }
                }
            }
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LButton("Skip", { onSkip(item.id) }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
            LButton("Save", { accountId?.let { onSave(item, type, categoryId, it) } }, modifier = Modifier.weight(1f), enabled = canSave, kind = ButtonKind.Lime, height = 48.dp)
        }
    }
}
