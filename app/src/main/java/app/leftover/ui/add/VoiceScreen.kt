package app.leftover.ui.add

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import app.leftover.domain.EntryKind
import app.leftover.domain.Money
import app.leftover.domain.ParsedEntry
import app.leftover.domain.VoiceParser
import app.leftover.domain.rupees
import app.leftover.ui.Toast
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Say or type an entry, check what Leftover understood, then save. Nothing is saved without the review step,
 * and fields it couldn't find are left for you to fill in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceScreen(snapshot: Snapshot, onClose: () -> Unit, onSave: (TransactionEntity, Toast) -> Unit) {
    val c = Leftover.colors
    val context = LocalContext.current
    var heard by rememberSaveable { mutableStateOf<String?>(null) }
    var typed by rememberSaveable { mutableStateOf("") }

    // Editable result of the parse.
    var kind by rememberSaveable { mutableStateOf(EntryKind.EXPENSE) }
    var amountDigits by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var daysAgo by rememberSaveable { mutableStateOf(0) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun apply(text: String) {
        val parsed: ParsedEntry = VoiceParser.parse(text, snapshot.categories)
        heard = text
        kind = parsed.kind
        amountDigits = parsed.amount?.let { (it / 100).toString() }.orEmpty()
        categoryId = parsed.category?.id
        merchant = parsed.merchant.orEmpty()
        daysAgo = parsed.daysAgo
    }

    val language = snapshot.settings.voiceLanguage
    val speech = remember(language) { SpeechController(context, language) { apply(it) } }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) speech.start() else speech.permissionDenied() }
    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val accounts = snapshot.accounts.filter { it.type != AccountType.SAVINGS }
    val account = accounts.firstOrNull { it.id == accountId } ?: accounts.firstOrNull()
    val categories = snapshot.categories.filter { !it.hidden && it.kind == if (kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
    val category = categories.firstOrNull { it.id == categoryId }
    val amount = rupees(amountDigits.toLongOrNull() ?: 0L)
    val toCheck = listOf(amount <= 0, category == null).count { it }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Say it", style = Type.h2, color = c.ink)
        }

        if (heard == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(28.dp))
                Text(
                    if (speech.listening) "Listening…" else "Tap the mic and say an expense",
                    style = Type.bodyStrong, color = c.muted,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    speech.partial.ifBlank { if (speech.listening) "" else "“Spent 180 on coffee today”" },
                    style = Type.h2, color = if (speech.partial.isBlank()) c.subtle else c.ink, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { contentDescription = "Heard: ${speech.partial}" },
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(if (speech.listening) c.lime else c.hero)
                        .clickable(role = Role.Button) { if (speech.listening) speech.stop() else startListening() }
                        .semantics { contentDescription = if (speech.listening) "Stop listening" else "Start listening" },
                    contentAlignment = Alignment.Center,
                ) { Icon(LeftoverIcons.Mic, null, tint = if (speech.listening) c.onLime else c.heroNumber, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.height(10.dp))
                Text(if (speech.listening) "Tap to stop" else "Say the amount, what for, and when", style = Type.captionBold, color = c.muted)
                speech.message?.let { note ->
                    Spacer(Modifier.height(14.dp))
                    Text(note, style = Type.caption, color = c.emberText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(Radius.md).background(c.emberBg).padding(12.dp))
                }
                Spacer(Modifier.height(26.dp))
                SectionLabel("Or type it")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(Radius.md).background(c.surface).border(1.dp, c.border, Radius.md).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (typed.isEmpty()) Text("e.g. uber 236 yesterday", style = Type.body, color = c.subtle)
                        BasicTextField(
                            typed, { typed = it.take(120) }, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LButton("Understand it", { apply(typed) }, enabled = typed.isNotBlank(), kind = ButtonKind.Outline, height = 48.dp)
                Spacer(Modifier.height(18.dp))
                SectionLabel("Try saying")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Spent 180 on coffee today", "Uber 236 yesterday", "Got 6,500 from client", "Dinner 850 two days ago").forEach { example ->
                        Chip(example, false, { typed = example; apply(example) })
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "“${heard}”", style = Type.bodyStrong, color = c.onChipSelected,
                    modifier = Modifier.align(Alignment.End).clip(Radius.lg).background(c.chipSelected).padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Card(shape = Radius.xl, padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Check before saving")
                        Text(if (toCheck == 0) "All set" else "$toCheck to check", style = Type.smallBold, color = if (toCheck == 0) c.positive else c.emberText)
                    }
                    Divider()
                    EditRow("Amount", if (amountDigits.isEmpty()) "—" else Money.format(amount)) {
                        BasicTextField(
                            amountDigits, { amountDigits = it.filter(Char::isDigit).take(7) }, textStyle = Type.h2.copy(color = c.ink, textAlign = TextAlign.End), cursorBrush = SolidColor(c.ink), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp),
                            decorationBox = { inner -> Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { Text("₹", style = Type.h2, color = c.muted); Spacer(Modifier.width(2.dp)); inner() } },
                        )
                    }
                    Divider()
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Expense", kind == EntryKind.EXPENSE, { kind = EntryKind.EXPENSE; categoryId = null })
                            Chip("Income", kind == EntryKind.INCOME, { kind = EntryKind.INCOME; categoryId = null })
                        }
                        Text(if (category == null) "Which category?" else "Category", style = Type.captionBold, color = if (category == null) c.emberText else c.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { cat -> Chip(cat.name, categoryId == cat.id, { categoryId = cat.id }) }
                        }
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (kind == EntryKind.INCOME) "From" else "Merchant", style = Type.caption, color = c.muted, modifier = Modifier.width(96.dp))
                        Box(Modifier.weight(1f)) {
                            if (merchant.isEmpty()) Text("Optional", style = Type.bodyStrong, color = c.subtle)
                            BasicTextField(merchant, { merchant = it.take(30) }, textStyle = Type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Divider()
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Date", style = Type.captionBold, color = c.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Today" to 0, "Yesterday" to 1, "2 days ago" to 2).forEach { (label, days) -> Chip(label, daysAgo == days, { daysAgo = days }) }
                        }
                        if (daysAgo > 2) Text("Heard $daysAgo days ago", style = Type.small, color = c.muted)
                    }
                    Divider()
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (kind == EntryKind.INCOME) "Received in" else "Paid with", style = Type.captionBold, color = c.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { a -> Chip(a.name, a.id == account?.id, { accountId = a.id }) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Again", { heard = null; typed = "" }, modifier = Modifier.width(112.dp), kind = ButtonKind.Outline, icon = LeftoverIcons.Mic)
                LButton(
                    text = when {
                        amount <= 0 -> "Add an amount"
                        category == null -> "Pick a category"
                        kind == EntryKind.INCOME -> "Save income ${Money.format(amount)}"
                        else -> "Save ${Money.format(amount)} · ${category.name}"
                    },
                    enabled = amount > 0 && category != null && account != null,
                    onClick = {
                        val moment = LocalDate.now().minusDays(daysAgo.toLong()).atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val isIncome = kind == EntryKind.INCOME
                        val tx = TransactionEntity(
                            type = if (isIncome) TxType.INCOME else TxType.EXPENSE, amount = amount, accountId = account!!.id,
                            categoryId = category!!.id, merchant = merchant.trim(), occurredAt = moment, source = TxSource.VOICE,
                        )
                        val after = snapshot.ledger.leftToSpend.left + if (isIncome) amount else -amount
                        onSave(tx, Toast(if (isIncome) "Saved income ${Money.format(amount)}" else "Saved ${Money.format(amount)} · ${category.name}", "${Money.format(after)} left to spend"))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EditRow(label: String, summary: String, field: @Composable () -> Unit) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.caption, color = c.muted, modifier = Modifier.width(96.dp).semantics { contentDescription = "$label, $summary" })
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { field() }
    }
}
