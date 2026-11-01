package app.leftover.ui.activity

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import app.leftover.domain.FilterPeriod
import app.leftover.domain.MoreFilters
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.domain.ActivityFilter
import app.leftover.domain.ActivityGroup
import app.leftover.domain.Money
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.Card
import app.leftover.ui.components.Banner
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconTile
import app.leftover.ui.home.TransactionRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityScreen(
    snapshot: Snapshot,
    onOpenTransaction: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenReview: () -> Unit,
    onDelete: (app.leftover.data.db.TransactionEntity) -> Unit,
    onRecategorize: (app.leftover.data.db.TransactionEntity, Long) -> Unit = { _, _ -> },
) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val hidden = snapshot.settings.hideBalances
    var group by rememberSaveable { mutableStateOf(ActivityGroup.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var more by rememberSaveable(stateSaver = MoreFiltersSaver) { mutableStateOf(MoreFilters()) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(ledger) { more.prunedTo(ledger).let { if (it != more) more = it } }
    val shown = remember(snapshot.transactions, group, query, more) { ActivityFilter.apply(snapshot.transactions, group, query, ledger, more) }
    val byDay = remember(shown) { shown.groupBy { UiFormat.date(it) }.toList().sortedByDescending { it.first } }
    // Entries that belong to a split bill or a settle-up are only deleted from their detail screen, so the split stays whole.
    val linked = remember(snapshot.shared) { (snapshot.shared.expenses.mapNotNull { it.transactionId } + snapshot.shared.settlements.mapNotNull { it.transactionId }).toSet() }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Activity", style = Type.h1, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconTile(LeftoverIcons.Import, onOpenImport, "Import a bank statement")
                    IconTile(LeftoverIcons.Wallet, onOpenAccounts, "Accounts")
                }
            }
            if (snapshot.pendingSms.isNotEmpty()) {
                Banner(
                    "${UiFormat.count(snapshot.pendingSms.size, "auto-tracked payment")} ${if (snapshot.pendingSms.size == 1) "needs" else "need"} a look",
                    c.lavender, c.lavenderInk, trailing = "Review →", onClick = onOpenReview,
                )
            }
            SearchField(query) { query = it }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Chip(if (more.count > 0) "Filters · ${more.count}" else "More filters", more.count > 0, { showMore = !showMore }, icon = LeftoverIcons.Settings, role = Role.Button)
                }
                items(ActivityGroup.entries) { entry -> Chip(entry.label, group == entry, { group = entry }) }
            }
            if (showMore) MoreFiltersPanel(snapshot, more, onApply = { more = it; showMore = false }, onClose = { showMore = false })
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                Column(Modifier.padding(bottom = 18.dp).clickable(role = Role.Button, onClick = onOpenInsights), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryTile("Spent this cycle", ledger.totalSpent(), hidden, Modifier.weight(1f))
                        SummaryTile("Income", ledger.income(), hidden, Modifier.weight(1f))
                    }
                    Text("See insights and trends →", style = Type.captionBold, color = c.ink)
                }
            }
            if (byDay.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (snapshot.transactions.isEmpty()) "Nothing here yet" else "No matches", style = Type.title, color = c.ink)
                        Text(
                            if (snapshot.transactions.isEmpty()) "Add an expense with the + button and it will show up here." else "Try a different search or filter.",
                            style = Type.caption, color = c.muted,
                        )
                    }
                }
            }
            byDay.forEach { (date, list) ->
                stickyHeader(key = "day-${date.toEpochDay()}") {
                    Row(Modifier.fillMaxWidth().background(c.ground).padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(UiFormat.dayHeader(date, snapshot.today), style = Type.captionBold, color = c.muted)
                        val spend = ActivityFilter.daySpend(list)
                        Text(
                            when {
                                hidden -> ""
                                spend > 0 -> "Spent ${Money.format(spend)}"
                                list.any { it.type == app.leftover.data.db.TxType.REFUND } -> "${Money.format(-spend)} back"
                                else -> "No spending"
                            },
                            style = Type.captionBold, color = c.muted,
                        )
                    }
                }
                itemsIndexed(list, key = { _, tx -> tx.id }) { index, tx ->
                    val locked = tx.id in linked
                    val shape = when {
                        list.size == 1 -> Radius.lg
                        index == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        index == list.lastIndex -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Column(Modifier.padding(bottom = if (index == list.lastIndex) 14.dp else 0.dp)) {
                        val canMove = tx.type == app.leftover.data.db.TxType.EXPENSE || tx.type == app.leftover.data.db.TxType.INCOME
                        SwipeRow(canDelete = !locked, onDelete = { onDelete(tx) }, onSwipeRight = if (canMove) ({ moving = tx.id }) else null, shape = shape) {
                            Column(Modifier.fillMaxWidth().clip(shape).background(c.surface)) {
                                if (index > 0) Divider()
                                TransactionRow(tx, ledger, hidden) { onOpenTransaction(tx.id) }
                            }
                        }
                    }
                }
            }
            if (byDay.isNotEmpty()) {
                item {
                    Text("Swipe an entry right to change its category, or left to delete it. Split bills and settle-ups are deleted from their own page.", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
    snapshot.transactions.firstOrNull { it.id == moving }?.let { tx ->
        MoveCategoryDialog(snapshot, tx, onPick = { onRecategorize(tx, it); moving = null }, onDismiss = { moving = null })
    }
}

/**
 * Drag a row left past a third of its width to delete it, or right to change its category; anything shorter springs
 * back. Screen readers get "Delete" and "Change category" actions instead of the gestures.
 */
@Composable
private fun SwipeRow(canDelete: Boolean, onDelete: () -> Unit, onSwipeRight: (() -> Unit)?, shape: androidx.compose.ui.graphics.Shape, content: @Composable () -> Unit) {
    val c = Leftover.colors
    if (!canDelete && onSwipeRight == null) { content(); return }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val actions = buildList {
        if (onSwipeRight != null) add(CustomAccessibilityAction("Change category") { onSwipeRight(); true })
        if (canDelete) add(CustomAccessibilityAction("Delete") { onDelete(); true })
    }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }.semantics { customActions = actions }) {
        if (offset.value < 0f) {
            Box(Modifier.matchParentSize().clip(shape).background(c.emberBg).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text(if (-offset.value > width / 3) "Release to delete" else "Delete", style = Type.captionBold, color = c.emberText)
            }
        }
        if (offset.value > 0f) {
            Box(Modifier.matchParentSize().clip(shape).background(c.lavender).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (offset.value > width / 3) "Release to change category" else "Category", style = Type.captionBold, color = c.lavenderInk)
            }
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.pointerInput(canDelete, onSwipeRight != null) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                canDelete && -offset.value > width / 3 -> { offset.animateTo(-width, tween(160)); onDelete(); offset.snapTo(0f) }
                                onSwipeRight != null && offset.value > width / 3 -> { offset.animateTo(0f, spring()); onSwipeRight() }
                                else -> offset.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                ) { change, drag ->
                    change.consume()
                    val min = if (canDelete) -width else 0f
                    val max = if (onSwipeRight != null) width else 0f
                    scope.launch { offset.snapTo((offset.value + drag).coerceIn(min, max)) }
                }
            },
        ) { content() }
    }
}

/** Picks a new category for one entry, from the same kind (spending or income). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MoveCategoryDialog(snapshot: Snapshot, tx: app.leftover.data.db.TransactionEntity, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val c = Leftover.colors
    val kind = if (tx.type == app.leftover.data.db.TxType.INCOME) app.leftover.data.db.CategoryKind.INCOME else app.leftover.data.db.CategoryKind.EXPENSE
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(Radius.xl).background(c.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Move ${tx.merchant.ifBlank { tx.note }.ifBlank { "this entry" }} to", style = Type.h2, color = c.ink)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.categories.filter { it.kind == kind && !it.hidden }.forEach { cat ->
                    Chip(cat.name, cat.id == tx.categoryId, { if (cat.id != tx.categoryId) onPick(cat.id) else onDismiss() }, icon = LeftoverIcons.forKey(cat.icon))
                }
            }
            Text("Cancel", style = Type.bodyStrong, color = c.muted, modifier = Modifier.align(Alignment.End).clip(Radius.sm).clickable(role = Role.Button, onClick = onDismiss).padding(12.dp))
        }
    }
}

/** Category, account, cycle and amount. Changes apply when "Show results" is tapped; "Clear" removes them all. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MoreFiltersPanel(snapshot: Snapshot, current: MoreFilters, onApply: (MoreFilters) -> Unit, onClose: () -> Unit) {
    val c = Leftover.colors
    var draft by remember(current) { mutableStateOf(current) }
    var min by remember(current) { mutableStateOf(current.minRupees?.toString().orEmpty()) }
    var max by remember(current) { mutableStateOf(current.maxRupees?.toString().orEmpty()) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(Radius.lg).background(c.surface).border(1.dp, c.hairline, Radius.lg)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Category", style = Type.captionBold, color = c.muted)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.categories.filter { !it.hidden }.forEach { cat ->
                val on = cat.id in draft.categoryIds
                Chip(cat.name, on, { draft = draft.copy(categoryIds = if (on) draft.categoryIds - cat.id else draft.categoryIds + cat.id) }, icon = LeftoverIcons.forKey(cat.icon), role = Role.Checkbox)
            }
        }
        Text("Account", style = Type.captionBold, color = c.muted)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Any", draft.accountId == null, { draft = draft.copy(accountId = null) })
            snapshot.accounts.forEach { a -> Chip(a.name, draft.accountId == a.id, { draft = draft.copy(accountId = a.id) }) }
        }
        Text("When", style = Type.captionBold, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPeriod.entries.forEach { p -> Chip(p.label, draft.period == p, { draft = draft.copy(period = p) }) }
        }
        Text("Amount", style = Type.captionBold, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            app.leftover.ui.components.LabeledField("From", min, "0", { min = it.filter(Char::isDigit).take(7) }, androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.weight(1f), prefix = "₹")
            app.leftover.ui.components.LabeledField("To", max, "Any", { max = it.filter(Char::isDigit).take(7) }, androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.weight(1f), prefix = "₹")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            app.leftover.ui.components.LButton("Clear", { onApply(MoreFilters()) }, modifier = Modifier.weight(1f), kind = app.leftover.ui.components.ButtonKind.Outline, height = 46.dp)
            app.leftover.ui.components.LButton("Show results", { onApply(draft.copy(minRupees = min.toLongOrNull(), maxRupees = max.toLongOrNull())) }, modifier = Modifier.weight(1f), height = 46.dp)
        }
    }
}

/** Keeps the filters across rotation and process death. */
private val MoreFiltersSaver = androidx.compose.runtime.saveable.listSaver<MoreFilters, Any>(
    save = { listOf(it.categoryIds.toLongArray(), it.accountId ?: -1L, it.period.name, it.minRupees ?: -1L, it.maxRupees ?: -1L) },
    restore = {
        MoreFilters(
            (it[0] as LongArray).toSet(), (it[1] as Long).takeIf { id -> id >= 0 }, FilterPeriod.valueOf(it[2] as String),
            (it[3] as Long).takeIf { v -> v >= 0 }, (it[4] as Long).takeIf { v -> v >= 0 },
        )
    },
)

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Leftover.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Radius.md).background(c.surface).border(1.dp, c.hairline, Radius.md).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(LeftoverIcons.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search merchant, note or amount", style = Type.body, color = c.muted)
            BasicTextField(value, onChange, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryTile(label: String, amount: Long, hidden: Boolean, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(modifier.clip(Radius.md).background(c.surface).border(1.dp, c.hairline, Radius.md).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = Type.smallBold, color = c.muted)
        Amount(amount, hidden, Type.h2, color = c.ink)
    }
}
