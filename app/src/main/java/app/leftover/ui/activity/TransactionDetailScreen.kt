package app.leftover.ui.activity

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import app.leftover.data.ReceiptStore
import app.leftover.data.Snapshot
import app.leftover.data.db.TxFeeling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.Money
import app.leftover.ui.UiFormat
import app.leftover.ui.UiFormat.label
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Card
import app.leftover.ui.components.CategoryTile
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Segmented
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

private val editableTypes = listOf(TxType.EXPENSE, TxType.TRANSFER, TxType.REFUND, TxType.REPAYMENT)

private fun explain(type: TxType) = when (type) {
    TxType.EXPENSE -> "Counts toward spending and your category budgets."
    TxType.TRANSFER -> "Money moved between your own accounts. Not spending."
    TxType.REFUND -> "Reduces spending in the original category."
    TxType.REPAYMENT -> "A friend paying you back. Not counted as income."
    TxType.SETTLE_OUT -> "You paying a friend back. Not counted as spending."
    TxType.INCOME -> "Money you earned. Counts as income, not spending."
    TxType.GOAL_SAVE -> "Money moved into a goal. It no longer counts toward Left to spend."
    TxType.GOAL_WITHDRAW -> "Money taken back out of a goal."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(snapshot: Snapshot, id: Long, onBack: () -> Unit, onUpdate: (TransactionEntity) -> Unit, onDelete: (Long) -> Unit, onSetTags: (Long, Set<Long>) -> Unit, onAttachReceipt: (TransactionEntity, String?) -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val tx = snapshot.transactions.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    val hidden = snapshot.settings.hideBalances

    if (tx == null) {
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().padding(20.dp)) {
            BackButton(onBack)
            Text("This transaction was deleted.", style = Type.body, color = c.muted, modifier = Modifier.padding(top = 24.dp))
        }
        return
    }

    val account = snapshot.accounts.firstOrNull { it.id == tx.accountId }
    val toAccount = snapshot.accounts.firstOrNull { it.id == tx.toAccountId }
    val categories = snapshot.categories.filter { !it.hidden && it.kind == if (tx.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
    var note by remember(tx.id) { mutableStateOf(tx.note) }
    val splitExpense = snapshot.shared.expenses.firstOrNull { it.transactionId == tx.id }
    val settlement = snapshot.shared.settlements.firstOrNull { it.transactionId == tx.id }
    val linkedPerson = settlement?.let { snapshot.shared.person(it.personId)?.name }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Transaction", style = Type.title, color = c.ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryTile(UiFormat.iconKey(tx, ledger), UiFormat.colorKey(tx, ledger), size = 56.dp)
                Text(UiFormat.title(tx, ledger), style = Type.bodyStrong, color = c.ink)
                Amount(UiFormat.signed(tx), hidden, Type.display48, color = c.ink, signed = tx.type != TxType.TRANSFER)
                Text("${UiFormat.dayHeader(UiFormat.date(tx), snapshot.today).substringAfter(" · ")} · ${UiFormat.clock(tx.occurredAt)} · ${account?.name ?: "—"}", style = Type.caption, color = c.muted, textAlign = TextAlign.Center)
            }

            if (tx.type in editableTypes && splitExpense == null && settlement == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Segmented(editableTypes.map { it.label() }, tx.type.label(), { picked -> editableTypes.firstOrNull { it.label() == picked }?.let { onUpdate(tx.copy(type = it)) } })
                    Text(explain(tx.type), style = Type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            } else {
                Text(explain(tx.type), style = Type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
            }

            if ((tx.type == TxType.EXPENSE || tx.type == TxType.REFUND || tx.type == TxType.INCOME) && splitExpense == null) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Category", style = Type.bodyExtra, color = c.ink)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { cat -> Chip(cat.name, tx.categoryId == cat.id, { onUpdate(tx.copy(categoryId = cat.id)) }) }
                    }
                }
            }

            if (tx.type == TxType.EXPENSE) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Was it worth it?", style = Type.bodyExtra, color = c.ink)
                    Text("Only you see this. Insights and reports add up your regrets.", style = Type.small, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TxFeeling.entries.forEach { f -> Chip(f.label, tx.feeling == f, { onUpdate(tx.copy(feeling = if (tx.feeling == f) null else f)) }, role = Role.RadioButton) }
                    }
                }
            }

            if (tx.type == TxType.EXPENSE || tx.type == TxType.REFUND || tx.type == TxType.INCOME) {
                ReceiptCard(tx, onAttach = { name -> onAttachReceipt(tx, name) })
            }

            if (tx.type == TxType.EXPENSE || tx.type == TxType.INCOME || tx.type == TxType.REFUND) {
                val mine = app.leftover.domain.TagTotals.tagsOf(tx.id, snapshot.txTags)
                Card(padding = PaddingValues(16.dp)) {
                    Text("Tags", style = Type.bodyExtra, color = c.ink)
                    Spacer(Modifier.height(10.dp))
                    if (snapshot.tags.isEmpty()) Text("No tags yet. Make one from Budgets, or when you add a spend.", style = Type.caption, color = c.muted)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        snapshot.tags.forEach { tag -> Chip("#${tag.name}", tag.id in mine, { onSetTags(tx.id, if (tag.id in mine) mine - tag.id else mine + tag.id) }, role = androidx.compose.ui.semantics.Role.Checkbox) }
                    }
                }
            }

            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                DetailRow("Account", if (toAccount != null) "${account?.name} → ${toAccount.name}" else account?.name ?: "—")
                Divider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Note", style = Type.caption, color = c.muted, modifier = Modifier.width(90.dp))
                    Box(Modifier.weight(1f)) {
                        if (note.isEmpty()) Text("Add a note", style = Type.bodyStrong, color = c.subtle)
                        BasicTextField(
                            note, { note = it }, textStyle = Type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (tx.type == TxType.EXPENSE) {
                    Divider()
                    ToggleRow("Leave out of budgets", null, tx.excludeFromBudgets, { onUpdate(tx.copy(excludeFromBudgets = it)) })
                }
            }

            if (note != tx.note) LButton("Save note", { onUpdate(tx.copy(note = note.trim())) }, kind = ButtonKind.Lime, height = 48.dp)

            if (settlement != null) {
                Card(color = c.lavender, bordered = false, padding = PaddingValues(14.dp)) {
                    Text("Recorded from Split", style = Type.bodyExtra, color = c.lavenderInk)
                    Text(
                        "This settles up with ${linkedPerson ?: "a friend"}. To change it, delete it here and record the payment again from their page.",
                        style = Type.caption, color = c.lavenderMuted,
                    )
                }
            }
            if (splitExpense != null) {
                Text("Type and category are locked because this bill is split. Delete it to start again.", style = Type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (tx.splitOthers > 0) {
                Card(color = c.lavender, bordered = false, padding = PaddingValues(14.dp)) {
                    Text("Shared expense", style = Type.bodyExtra, color = c.lavenderInk)
                    Text(
                        "Your share is ${Money.display(tx.amount - tx.splitOthers, hidden)}. ${Money.display(tx.splitOthers, hidden)} is coming back from friends and isn't counted in budgets.",
                        style = Type.caption, color = c.lavenderMuted,
                    )
                }
            }

            if (confirmDelete) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Delete this transaction?", style = Type.bodyExtra, color = c.ink)
                    Text(if (splitExpense != null || settlement != null) "The split or payment it belongs to is removed too, so balances stay in step. This can't be undone." else "Balances and budgets will update. This can't be undone.", style = Type.caption, color = c.muted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LButton("Keep", { confirmDelete = false }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                        LButton("Delete", { onDelete(tx.id); onBack() }, modifier = Modifier.weight(1f), height = 48.dp)
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(Radius.md).clickable(role = Role.Button) { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) { Text("Delete transaction", style = Type.bodyStrong, color = c.emberText) }
            }
        }
    }
}

/** Attach a photo of the receipt from the camera or the gallery. The photo stays in the app's private folder. */
@Composable
private fun ReceiptCard(tx: TransactionEntity, onAttach: (String?) -> Unit) {
    val c = Leftover.colors
    val context = LocalContext.current
    var preview by remember(tx.receiptFile) { mutableStateOf<ImageBitmap?>(null) }
    var full by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCamera by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tx.receiptFile) {
        preview = tx.receiptFile?.let { name -> withContext(Dispatchers.IO) { ReceiptStore.thumbnail(context, name)?.asImageBitmap() } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingCamera
        pendingCamera = null
        if (name != null) scope.launch {
            val saved = ok && withContext(Dispatchers.IO) { ReceiptStore.finishCamera(context, name) }
            if (saved) { error = null; onAttach(name) } else ReceiptStore.file(context, name).delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { ReceiptStore.importFrom(context, uri) }
            if (name != null) { error = null; onAttach(name) } else error = "Couldn't read that image. Try another one."
        }
    }
    Card(padding = PaddingValues(16.dp)) {
        Text("Receipt", style = Type.bodyExtra, color = c.ink)
        Spacer(Modifier.height(10.dp))
        val image = preview
        if (tx.receiptFile != null && image != null) {
            Image(
                image, "Receipt photo. Double tap to view it full size.",
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(Radius.md).clickable(role = Role.Image) { full = true },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Replace", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 44.dp)
                LButton("Remove", { onAttach(null) }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 44.dp)
            }
        } else {
            Text("Keep a photo of the bill for returns, warranties or splitting later. It stays on this phone and isn't part of backups.", style = Type.caption, color = c.muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Take photo", {
                    val (name, uri) = ReceiptStore.cameraTarget(context)
                    pendingCamera = name
                    runCatching { camera.launch(uri) }.onFailure { pendingCamera = null; error = "No camera app found." }
                }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 44.dp)
                LButton("From gallery", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 44.dp)
            }
        }
        error?.let { Text(it, style = Type.small, color = c.emberText, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (full && tx.receiptFile != null) {
        var big by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(tx.receiptFile) { big = withContext(Dispatchers.IO) { ReceiptStore.thumbnail(context, tx.receiptFile, side = 1600)?.asImageBitmap() } }
        Dialog(onDismissRequest = { full = false }) {
            Box(Modifier.fillMaxWidth().clip(Radius.lg).background(c.surface).clickable(role = Role.Button) { full = false }.padding(8.dp)) {
                big?.let { Image(it, "Receipt photo, full size. Tap to close.", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.caption, color = c.muted, modifier = Modifier.width(90.dp))
        Text(value, style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
    }
}
