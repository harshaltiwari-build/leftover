package app.leftover.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountType
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import app.leftover.domain.CsvImport
import app.leftover.domain.CsvMapping
import app.leftover.domain.DateStyle
import app.leftover.domain.Money
import app.leftover.domain.SmsPolicy
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.Segmented
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

private const val MAX_BYTES = 5_000_000
private const val NONE = -1

/**
 * Brings in transactions from a bank statement or spreadsheet saved as CSV. You choose what each column means, see exactly
 * what will be added, and rows that look like something Leftover already has are left out unless you say otherwise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(snapshot: Snapshot, onBack: () -> Unit, onImport: (List<TransactionEntity>, () -> Unit) -> Unit) {
    val c = Leftover.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var table by remember { mutableStateOf<List<List<String>>?>(null) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    var done by rememberSaveable { mutableStateOf<Int?>(null) }
    var working by rememberSaveable { mutableStateOf(false) }

    var hasHeader by rememberSaveable { mutableStateOf(true) }
    var dateColumn by rememberSaveable { mutableStateOf(0) }
    var descriptionColumn by rememberSaveable { mutableStateOf(NONE) }
    var amountColumn by rememberSaveable { mutableStateOf(NONE) }
    var debitColumn by rememberSaveable { mutableStateOf(NONE) }
    var creditColumn by rememberSaveable { mutableStateOf(NONE) }
    var twoColumns by rememberSaveable { mutableStateOf(false) }
    var style by rememberSaveable { mutableStateOf(DateStyle.DAY_MONTH_YEAR.name) }
    var positiveIsSpending by rememberSaveable { mutableStateOf(false) }
    var includeDuplicates by rememberSaveable { mutableStateOf(false) }
    val accounts = snapshot.accounts.filter { !it.archived && (it.type == AccountType.BANK || it.type == AccountType.CASH || it.type == AccountType.CARD) }
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull { it.type == AccountType.BANK }?.id ?: accounts.firstOrNull()?.id ?: -1L) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            problem = null
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().takeIf { b -> b.size <= MAX_BYTES }?.toString(Charsets.UTF_8) } }.getOrNull()
            }
            val rows = text?.let(CsvImport::parse)
            val guess = rows?.let(CsvImport::guess)
            if (rows == null || guess == null) {
                problem = if (text == null) "Couldn't read that file. It may be too large (over 5 MB) or not text." else "That doesn't look like a statement: it needs a header and at least one row."
            } else {
                table = rows
                hasHeader = guess.hasHeader
                dateColumn = guess.date
                descriptionColumn = guess.description ?: NONE
                amountColumn = guess.amount ?: NONE
                debitColumn = guess.debit ?: NONE
                creditColumn = guess.credit ?: NONE
                twoColumns = guess.debit != null || guess.credit != null
                style = guess.dateStyle.name
                positiveIsSpending = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Import from a CSV", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val rows = table
            val doneCount = done
            if (doneCount != null) {
                Card(padding = PaddingValues(20.dp)) {
                    Text("Imported ${UiFormat.count(doneCount, "transaction")}", style = Type.title, color = c.ink)
                    Text("They're in Activity now. Spending counts toward your budgets from the date on each row.", style = Type.caption, color = c.muted)
                }
                LButton("Done", onBack, kind = ButtonKind.Lime)
            } else if (rows == null) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Bring in a statement", style = Type.bodyExtra, color = c.ink)
                    Spacer(Modifier.height(4.dp))
                    Text("Download your bank statement or an export from another app as a CSV file, then pick it here. You'll choose what each column means and see everything before anything is added. The file stays on your phone.", style = Type.caption, color = c.muted)
                }
                LButton("Choose a CSV file", kind = ButtonKind.Lime, onClick = { pick.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream")) })
                problem?.let { Text(it, style = Type.captionBold, color = c.emberText) }
            } else {
                val header = if (hasHeader) rows.first() else emptyList()
                val width = rows.maxOf { it.size }
                fun label(i: Int) = header.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "Column ${i + 1}"
                val dateStyle = DateStyle.valueOf(style)
                val mapping = CsvMapping(
                    hasHeader, dateColumn, descriptionColumn.takeIf { it != NONE },
                    amount = if (twoColumns) null else amountColumn.takeIf { it != NONE },
                    debit = if (twoColumns) debitColumn.takeIf { it != NONE } else null,
                    credit = if (twoColumns) creditColumn.takeIf { it != NONE } else null,
                    dateStyle = dateStyle, positiveIsSpending = positiveIsSpending,
                )
                val preview = remember(rows, mapping, snapshot.transactions.size) { CsvImport.preview(rows, mapping, snapshot.transactions) }
                val toAdd = preview.rows.filter { includeDuplicates || !it.duplicate }

                Card(padding = PaddingValues(16.dp)) {
                    Text("${UiFormat.count(rows.size - if (hasHeader) 1 else 0, "row")} found", style = Type.bodyExtra, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    rows.take(if (hasHeader) 3 else 2).forEach { r -> Text(r.joinToString("  ·  ").take(90), style = Type.small, color = c.muted, maxLines = 1) }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("First row is a header", null, hasHeader, { hasHeader = it })
                }

                Picker("Date", (0 until width).toList(), ::label, dateColumn) { dateColumn = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateStyle.entries.forEach { s -> Chip(s.label, style == s.name, { style = s.name }) }
                }
                Picker("Description", (0 until width).toList(), ::label, descriptionColumn, allowNone = true) { descriptionColumn = it }
                Segmented(listOf("One amount column", "Debit and credit"), if (twoColumns) "Debit and credit" else "One amount column", { twoColumns = it == "Debit and credit" })
                if (twoColumns) {
                    Picker("Money out (debit)", (0 until width).toList(), ::label, debitColumn, allowNone = true) { debitColumn = it }
                    Picker("Money in (credit)", (0 until width).toList(), ::label, creditColumn, allowNone = true) { creditColumn = it }
                } else {
                    Picker("Amount", (0 until width).toList(), ::label, amountColumn) { amountColumn = it }
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        ToggleRow("Positive amounts are spending", "Turn on if your bank lists what you spent as plain positive numbers.", positiveIsSpending, { positiveIsSpending = it })
                    }
                }
                if (accounts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Add them to")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { a -> Chip(a.name, accountId == a.id, { accountId = a.id }) }
                        }
                    }
                }

                Card(padding = PaddingValues(16.dp)) {
                    Text("Preview", style = Type.bodyExtra, color = c.ink)
                    Text(
                        "${UiFormat.count(preview.fresh, "new transaction")}" + (if (preview.duplicates > 0) ", ${preview.duplicates} look like ones you already have" else "") +
                            (if (preview.unreadable > 0) ", ${preview.unreadable} couldn't be read" else ""),
                        style = Type.caption, color = c.muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    toAdd.take(5).forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(r.description.ifBlank { if (r.isExpense) "Spending" else "Money in" }, style = Type.bodyStrong, color = c.ink, maxLines = 1)
                                Text(UiFormat.dayMonth(r.date) + " " + r.date.year, style = Type.small, color = c.muted)
                            }
                            Amount(if (r.isExpense) -r.paise else r.paise, snapshot.settings.hideBalances, Type.bodyExtra, color = if (r.isExpense) c.ink else c.positive, signed = true)
                        }
                    }
                    if (toAdd.isEmpty()) Text("Nothing to add yet. Check the columns above.", style = Type.caption, color = c.emberText)
                }
                if (preview.duplicates > 0) {
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        ToggleRow("Add the possible duplicates too", "Same day and amount as something already here.", includeDuplicates, { includeDuplicates = it })
                    }
                }
                LButton(if (working) "Importing…" else "Import ${toAdd.size}", enabled = toAdd.isNotEmpty() && accountId >= 0 && !working, kind = ButtonKind.Lime, onClick = {
                    working = true
                    val zone = ZoneId.systemDefault()
                    val rules = snapshot.rules.map { it.pattern to it.categoryId }
                    val txs = toAdd.map { r ->
                        val category = if (r.isExpense) SmsPolicy.ruleCategory(r.description, rules) ?: SmsPolicy.categoryFor(r.description, snapshot.transactions) else null
                        TransactionEntity(
                            type = if (r.isExpense) TxType.EXPENSE else TxType.INCOME, amount = r.paise, accountId = accountId, categoryId = category,
                            merchant = r.description, occurredAt = r.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), source = TxSource.IMPORT,
                        )
                    }
                    onImport(txs) { working = false; done = txs.size }
                })
                Text("Imported spending shows in Activity and your budgets. Add a category later from each transaction, or set a capture rule to sort them in future.", style = Type.small, color = c.muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Picker(title: String, columns: List<Int>, label: (Int) -> String, selected: Int, allowNone: Boolean = false, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowNone) Chip("None", selected == NONE, { onSelect(NONE) })
            columns.forEach { i -> Chip(label(i), selected == i, { onSelect(i) }) }
        }
    }
}
