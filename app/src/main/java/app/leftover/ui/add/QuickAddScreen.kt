package app.leftover.ui.add

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import app.leftover.data.db.TemplateEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.CategoryKind
import app.leftover.ui.UiFormat
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.KeypadInput
import app.leftover.domain.Money
import app.leftover.ui.Toast
import app.leftover.ui.components.AmountDisplay
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Keypad
import app.leftover.ui.components.LButton
import app.leftover.ui.components.Segmented
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate
import java.time.ZoneId

private val kinds = listOf("Expense", "Income", "Transfer")

/**
 * The fast-entry screen: amount first on a custom keypad, then a category, then Save.
 * It also serves as the "Income" entry from the home-screen widget by passing [initialKind].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddScreen(
    snapshot: Snapshot,
    onClose: () -> Unit,
    onSave: (TransactionEntity, Toast, Set<Long>) -> Unit,
    onAddTag: (String) -> Unit,
    onAddCategory: (String, CategoryKind) -> Unit,
    onSaveTemplate: (TemplateEntity) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    initialKind: String = "Expense",
) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var digits by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var daysAgo by rememberSaveable { mutableStateOf(0) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var tagIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var newTag by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingForTag by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var savedAsTemplate by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingForCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(snapshot.categories) {
        waitingForCategory?.let { name -> snapshot.categories.firstOrNull { it.name.equals(name, true) }?.let { categoryId = it.id; waitingForCategory = null } }
    }
    // A tag made here appears in the list a moment later; pick it as soon as it does.
    LaunchedEffect(snapshot.tags) {
        waitingForTag?.let { name -> snapshot.tags.firstOrNull { it.name.equals(name, true) }?.let { tagIds = tagIds + it.id; waitingForTag = null } }
    }

    val spendAccounts = snapshot.accounts.filter { it.type != app.leftover.data.db.AccountType.SAVINGS }
    val fromAccount = spendAccounts.firstOrNull { it.id == accountId } ?: spendAccounts.firstOrNull()
    val toAccount = spendAccounts.firstOrNull { it.id == toAccountId && it.id != fromAccount?.id } ?: spendAccounts.firstOrNull { it.id != fromAccount?.id }
    val categories = snapshot.categories.filter { !it.hidden && it.kind == if (kind == "Income") CategoryKind.INCOME else CategoryKind.EXPENSE }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }

    val amount = KeypadInput.toPaise(digits)
    val before = ledger.leftToSpend.left
    val after = when (kind) {
        "Expense" -> before - amount
        "Income" -> before + amount
        else -> before
    }
    val needsCategory = kind != "Transfer"
    val ready = amount > 0 && fromAccount != null && (!needsCategory || selectedCategory != null) && (kind != "Transfer" || toAccount != null)

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BackButton(onClose, "Close", close = true)
            Segmented(kinds, kind, { kind = it; categoryId = null }, Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(22.dp))
            AmountDisplay(digits, sign = if (kind == "Income") "+" else if (kind == "Expense" && digits.isNotEmpty()) "−" else "", signColor = if (kind == "Income") c.positive else c.muted)
            Spacer(Modifier.height(8.dp))
            val over = after < 0 && kind == "Expense"
            Text(
                text = if (kind == "Transfer") "Transfers don't change what you can spend" else "Left to spend: ${Money.format(before)} → ${Money.format(after)}",
                style = Type.captionBold, color = if (over) c.emberText else c.ink,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(Radius.pill).background(if (over) c.emberBg else c.track).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(18.dp))
            if (kind == "Expense" && snapshot.templates.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snapshot.templates, key = { it.id }) { t ->
                        Chip("${t.name} · ${Money.format(t.amount)}", false, {
                            digits = (t.amount / 100).toString(); categoryId = t.categoryId; merchant = t.merchant; t.accountId?.let { accountId = it }; if (note.isBlank()) note = ""
                        }, icon = LeftoverIcons.Plus, onLongClick = { deleting = t.id })
                    }
                }
                deleting?.let { id ->
                    val t = snapshot.templates.firstOrNull { it.id == id }
                    if (t != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(Radius.md).background(c.emberBg).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Delete “${t.name}”?", style = Type.captionBold, color = c.emberText, modifier = Modifier.weight(1f))
                            Chip("Keep", false, { deleting = null })
                            Chip("Delete", true, { onDeleteTemplate(id); deleting = null })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (needsCategory) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat -> Chip(cat.name, categoryId == cat.id, { categoryId = cat.id }) }
                    Chip("New", false, { newCategory = if (newCategory == null) "" else null }, icon = LeftoverIcons.Plus, dashed = true)
                }
                newCategory?.let { name ->
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { NoteField(name, hint = "Name this category") { newCategory = it.take(20) } }
                        Chip("Add", true, {
                            if (name.isNotBlank() && categories.none { it.name.equals(name.trim(), true) }) {
                                onAddCategory(name.trim(), if (kind == "Income") CategoryKind.INCOME else CategoryKind.EXPENSE)
                                waitingForCategory = name.trim(); newCategory = null
                            }
                        })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            AccountRow(if (kind == "Transfer") "From" else if (kind == "Income") "Received in" else "Paid from", spendAccounts.map { it.id to it.name }, fromAccount?.id) { accountId = it }
            if (kind == "Transfer") {
                Spacer(Modifier.height(10.dp))
                AccountRow("To", spendAccounts.filter { it.id != fromAccount?.id }.map { it.id to it.name }, toAccount?.id) { toAccountId = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Today" to 0, "Yesterday" to 1, "2 days ago" to 2).forEach { (label, days) -> Chip(label, daysAgo == days, { daysAgo = days }, icon = if (daysAgo == days) LeftoverIcons.Calendar else null) }
                val custom = daysAgo > 2
                Chip(if (custom) UiFormat.dayMonth(LocalDate.now().minusDays(daysAgo.toLong())) else "Other date", custom, { pickingDate = true }, icon = LeftoverIcons.Calendar)
            }
            if (pickingDate) {
                val todayUtc = LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                val state = androidx.compose.material3.rememberDatePickerState(
                    initialSelectedDateMillis = LocalDate.now().minusDays(daysAgo.toLong()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                    selectableDates = object : androidx.compose.material3.SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                    },
                )
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { pickingDate = false },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            state.selectedDateMillis?.let { ms ->
                                val picked = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                                daysAgo = java.time.temporal.ChronoUnit.DAYS.between(picked, LocalDate.now()).toInt().coerceAtLeast(0)
                            }
                            pickingDate = false
                        }) { Text("Done") }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
                ) { androidx.compose.material3.DatePicker(state = state) }
            }
            Spacer(Modifier.height(10.dp))
            NoteField(note) { note = it }
            if (kind != "Transfer") {
                Spacer(Modifier.height(12.dp))
                Text("Tags", style = Type.captionBold, color = c.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.tags.forEach { tag -> Chip("#${tag.name}", tag.id in tagIds, { tagIds = if (tag.id in tagIds) tagIds - tag.id else tagIds + tag.id }, role = androidx.compose.ui.semantics.Role.Checkbox) }
                    Chip(if (newTag == null) "New tag" else "Cancel", false, { newTag = if (newTag == null) "" else null }, icon = if (newTag == null) LeftoverIcons.Plus else null)
                }
                newTag?.let { name ->
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(48.dp).clip(Radius.md).background(c.surface).border(1.dp, c.hairline, Radius.md).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (name.isEmpty()) Text("goa, wedding, birthday", style = Type.body, color = c.muted)
                            BasicTextField(name, { newTag = it.filter { ch -> ch.isLetterOrDigit() || ch == ' ' }.take(20) }, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        Chip("Add", true, { if (name.isNotBlank()) { onAddTag(name.trim()); waitingForTag = name.trim(); newTag = null } })
                    }
                }
            }
            if (kind == "Expense" && amount > 0 && selectedCategory != null && !savedAsTemplate) {
                Spacer(Modifier.height(12.dp))
                Chip("Save as a template", false, {
                    onSaveTemplate(TemplateEntity(name = note.trim().ifBlank { selectedCategory.name }.take(20), amount = amount, categoryId = selectedCategory.id, merchant = merchant.ifBlank { note.trim() }, accountId = fromAccount?.id))
                    savedAsTemplate = true
                }, icon = LeftoverIcons.Plus)
            } else if (savedAsTemplate) {
                Spacer(Modifier.height(12.dp))
                Text("Saved. Tap it any time from the row at the top.", style = Type.small, color = c.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Keypad({ digits = KeypadInput.press(digits, it) }, keyHeight = 50.dp)
        Spacer(Modifier.height(12.dp))
        LButton(
            text = when {
                amount <= 0 -> "Enter an amount"
                needsCategory && selectedCategory == null -> "Pick a category"
                kind == "Expense" -> "Save ${Money.format(amount)} · ${selectedCategory?.name}"
                kind == "Income" -> "Save income ${Money.format(amount)}"
                else -> "Save transfer ${Money.format(amount)}"
            },
            enabled = ready,
            onClick = {
                val zone = ZoneId.systemDefault()
                val moment = LocalDate.now().minusDays(daysAgo.toLong()).atTime(java.time.LocalTime.now()).atZone(zone).toInstant().toEpochMilli()
                val type = when (kind) { "Income" -> TxType.INCOME; "Transfer" -> TxType.TRANSFER; else -> TxType.EXPENSE }
                val tx = TransactionEntity(
                    type = type, amount = amount, accountId = fromAccount!!.id,
                    toAccountId = if (type == TxType.TRANSFER) toAccount?.id else null,
                    categoryId = selectedCategory?.id, merchant = merchant, note = note.trim(), occurredAt = moment,
                )
                val title = when (type) {
                    TxType.EXPENSE -> "Saved ${Money.format(amount)} · ${selectedCategory?.name}"
                    TxType.INCOME -> "Saved income ${Money.format(amount)}"
                    else -> "Saved transfer ${Money.format(amount)}"
                }
                val sub = if (type == TxType.TRANSFER) "Left to spend unchanged" else "${Money.format(after)} left to spend"
                onSave(tx, Toast(title, sub), tagIds)
            },
        )
    }
}

@Composable
private fun AccountRow(label: String, options: List<Pair<Long, String>>, selectedId: Long?, onSelect: (Long) -> Unit) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Type.captionBold, color = c.muted, modifier = Modifier.width(78.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, name) -> Chip(name, id == selectedId, { onSelect(id) }, icon = LeftoverIcons.forKey(if (name.contains("cash", true)) "cash" else if (name.contains("card", true)) "card" else "bank")) }
        }
    }
}

@Composable
private fun NoteField(value: String, hint: String = "Add a note (optional)", onChange: (String) -> Unit) {
    val c = Leftover.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Radius.md).background(c.surface).border(1.dp, c.hairline, Radius.md).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(LeftoverIcons.List, null, tint = c.muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = Type.body, color = c.muted)
            BasicTextField(value, onChange, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
