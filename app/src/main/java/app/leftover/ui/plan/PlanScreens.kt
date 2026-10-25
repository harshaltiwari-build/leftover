package app.leftover.ui.plan

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.domain.Money
import app.leftover.domain.PayCycle
import app.leftover.domain.rupees
import app.leftover.ui.Routes
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CategoryTile
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.Segmented
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.UiFormat
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.LocalDate

/** The four sections under the Plan tab. */
@Composable
fun PlanTabs(current: String, onSelect: (String) -> Unit) {
    val entries = listOf("Budgets" to Routes.PLAN, "Goals" to Routes.GOALS, "Bills" to Routes.BILLS, "Wishlist" to Routes.WISHLIST)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries) { (label, route) -> Chip(label, current == route, { if (route != current) onSelect(route) }) }
    }
}

@Composable
fun PlanScaffold(title: String = "Plan", current: String, onSelectTab: (String) -> Unit, content: @Composable () -> Unit) {
    val c = Leftover.colors
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = Type.h1, color = c.ink)
            PlanTabs(current, onSelectTab)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

private fun PayCycle.back(count: Int): PayCycle {
    var cycle = this
    repeat(count) { cycle = cycle.previous() }
    return cycle
}

@Composable
fun BudgetsScreen(snapshot: Snapshot, onSelectTab: (String) -> Unit, onEditLimits: () -> Unit, onCategories: () -> Unit, onOpenTags: () -> Unit, onOpenCheckIn: () -> Unit, onOpenBreakdown: () -> Unit, onToggleRollover: (CategoryEntity) -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val hidden = snapshot.settings.hideBalances
    val cycle = snapshot.cycle
    val budgeted = snapshot.categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden && (it.budgetLimit ?: 0L) > 0 }
    val totalLimit = budgeted.sumOf { it.budgetLimit ?: 0L }
    val totalSpent = budgeted.sumOf { ledger.spent(it.id).coerceAtLeast(0) }
    val daysLeft = cycle.daysUntilPayday(snapshot.today)
    val pace = cycle.dayOfCycle(snapshot.today).toFloat() / cycle.totalDays
    val usedFraction = if (totalLimit > 0) totalSpent.toFloat() / totalLimit else 0f
    var open by rememberSaveable { mutableStateOf<Long?>(null) }

    PlanScaffold(current = Routes.PLAN, onSelectTab = onSelectTab) {
        Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${UiRange.format(cycle.start)} – ${UiRange.format(cycle.end)}", style = Type.smallBold, color = c.ink,
                    modifier = Modifier.clip(Radius.pill).background(c.surfaceAlt).border(1.dp, c.border, Radius.pill).padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text("$daysLeft ${if (daysLeft == 1) "day" else "days"} left", style = Type.smallBold, color = c.muted)
            }
            Spacer(Modifier.height(14.dp))
            if (totalLimit <= 0) {
                Text("No budgets yet", style = Type.h2, color = c.ink)
                Text("Set a limit for the categories you want to keep an eye on.", style = Type.caption, color = c.muted)
                Spacer(Modifier.height(12.dp))
                LButton("Set budget limits", onEditLimits, height = 48.dp)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Amount((totalLimit - totalSpent), hidden, Type.display36, color = c.ink)
                    Text("budget left of ${Money.display(totalLimit, hidden)}", style = Type.body, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth()) {
                    ProgressBar(usedFraction, height = 12.dp, color = if (usedFraction > 1f) c.ember else c.ink)
                    Box(Modifier.padding(start = 0.dp).fillMaxWidth(pace.coerceIn(0f, 1f)).height(12.dp), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.width(2.dp).height(20.dp).background(c.muted))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("${(usedFraction * 100).toInt()}% used · marker = where you'd be at an even pace (${(pace * 100).toInt()}%)", style = Type.small, color = c.muted)
                Spacer(Modifier.height(12.dp))
                val projected = if (pace > 0.05f) (totalSpent / pace).toLong() else totalSpent
                val under = totalLimit - projected
                Text(
                    if (under >= 0) "You're on track to finish about ${Money.display(under, hidden)} under." else "At this pace you'll finish about ${Money.display(-under, hidden)} over — worth easing off.",
                    style = Type.captionBold, color = if (under >= 0) c.onLime else c.emberText,
                    modifier = Modifier.fillMaxWidth().clip(Radius.md).background(if (under >= 0) c.lime else c.emberBg).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Budget left tracks your category limits. Left to spend on Home is the money you actually have after bills, card dues and goals.",
                style = Type.small, color = c.muted,
            )
            Row(
                Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onOpenBreakdown),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("See how Left to spend is worked out", style = Type.captionBold, color = c.ink)
                Icon(LeftoverIcons.Arrow, null, tint = c.ink, modifier = Modifier.size(14.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Categories", style = Type.title, color = c.ink)
            Row {
                Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onCategories).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Manage", style = Type.captionBold, color = c.muted)
                }
                Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onEditLimits).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Edit limits", style = Type.captionBold, color = c.ink)
                }
            }
        }

        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            snapshot.categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden }.forEachIndexed { index, cat ->
                if (index > 0) Divider()
                val spent = ledger.spent(cat.id).coerceAtLeast(0)
                val limit = cat.budgetLimit
                val over = limit != null && spent > limit
                val expanded = open == cat.id
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (expanded) null else cat.id }.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryTile(cat.icon, cat.color, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, style = Type.bodyStrong, color = c.ink)
                            Text(if (limit != null) "${Money.display(spent, hidden)} of ${Money.display(limit, hidden)}" else "${Money.display(spent, hidden)} spent · no limit", style = Type.small, color = c.muted)
                        }
                        if (limit != null) {
                            Text(
                                if (over) "${Money.display(spent - limit, hidden)} over" else "${Money.display(limit - spent, hidden)} left",
                                style = Type.bodyExtra, color = if (over) c.emberText else c.ink,
                            )
                        }
                    }
                    if (limit != null && limit > 0) {
                        ProgressBar(spent.toFloat() / limit, color = if (over) c.ember else c.ink, track = if (over) c.emberBg else c.track)
                    }
                    if (expanded) {
                        val average = (1..3).map { ledger.spent(cat.id, cycle.back(it)).coerceAtLeast(0) }.average().toLong()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoPill("Avg last 3 cycles", Money.display(average, hidden), Modifier.weight(1f))
                            InfoPill("Per day now", if (limit != null && !over) Money.display((limit - spent) / daysLeft, hidden) else "₹0", Modifier.weight(1f))
                        }
                        ToggleRow("Roll unspent into next cycle", null, cat.rollover, { onToggleRollover(cat) })
                    }
                }
            }
        }

        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
            app.leftover.ui.components.SettingsRow("Tags and events", "See what a trip or a wedding cost across categories", onOpenTags, value = snapshot.tags.size.takeIf { it > 0 }?.toString())
            app.leftover.ui.components.Divider()
            app.leftover.ui.components.SettingsRow("Weekly check-in", "A look back at the week, and challenges you set", onOpenCheckIn, value = snapshot.challenges.size.takeIf { it > 0 }?.toString())
        }
    }
}

private object UiRange {
    fun format(date: LocalDate): String = "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
}

@Composable
private fun InfoPill(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(modifier.clip(Radius.sm).background(c.surfaceAlt).padding(10.dp)) {
        Text(label, style = Type.smallBold, color = c.muted)
        Text(value, style = Type.bodyExtra, color = c.ink)
    }
}

@Composable
fun EditLimitsScreen(snapshot: Snapshot, onClose: () -> Unit, onSave: (List<CategoryEntity>) -> Unit) {
    val c = Leftover.colors
    val ledger = snapshot.ledger
    val categories = snapshot.categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden }
    var limits by remember { mutableStateOf(categories.associate { it.id to (it.budgetLimit ?: 0L) }) }
    val total = limits.values.sum()
    val smoothed = if (snapshot.settings.irregularIncome) ledger.incomePlan else null
    val income = smoothed?.floor ?: if (snapshot.settings.usualIncome > 0) snapshot.settings.usualIncome else ledger.income()
    val fixed = snapshot.bills.sumOf { it.amount } + snapshot.goals.sumOf { it.perCycle }
    val room = income - fixed
    val over = total > room && income > 0

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Budget limits", style = Type.h2, color = c.ink)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Budgeted per cycle", style = Type.caption, color = c.onHeroMuted)
                    Text(Money.format(total), style = Type.h2, color = if (over) c.ember else c.heroNumber)
                }
                ProgressBar(if (room > 0) total.toFloat() / room else 0f, color = if (over) c.ember else c.heroNumber, track = c.onHeroMuted.copy(alpha = 0.3f))
                Text(
                    when {
                        income <= 0 -> "Add your usual income in onboarding or settings to see how much room you have."
                        over -> "${Money.format(total - room)} more than you have after bills and goals (${Money.format(room)}). Lower a limit or a goal."
                        else -> "${Money.format(room - total)} unbudgeted after ${Money.format(income)} ${if (smoothed != null) "in your leanest month" else "income"} − ${Money.format(fixed)} bills & goals."
                    },
                    style = Type.small, color = c.onHeroMuted,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Card(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                categories.forEachIndexed { index, cat ->
                    if (index > 0) Divider()
                    val average = (1..3).map { ledger.spent(cat.id, snapshot.cycle.back(it)).coerceAtLeast(0) }.average().toLong()
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, style = Type.bodyStrong, color = c.ink)
                            Text("Usually ${Money.format(average)}", style = Type.small, color = c.muted)
                        }
                        Stepper("−", "Lower ${cat.name} by ₹500") { limits = limits + (cat.id to (((limits[cat.id] ?: 0L) - rupees(500)).coerceAtLeast(0))) }
                        Text(Money.format(limits[cat.id] ?: 0L), style = Type.bodyExtra, color = c.ink, modifier = Modifier.width(78.dp), textAlign = TextAlign.Center)
                        Stepper("+", "Raise ${cat.name} by ₹500") { limits = limits + (cat.id to ((limits[cat.id] ?: 0L) + rupees(500))) }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp)) {
            LButton("Save limits · from next cycle", {
                onSave(categories.map { it.copy(budgetLimit = (limits[it.id] ?: 0L).takeIf { v -> v > 0 }) })
                onClose()
            })
        }
    }
}

@Composable
private fun Stepper(symbol: String, description: String, onClick: () -> Unit) {
    val c = Leftover.colors
    Box(
        Modifier.size(44.dp).clip(Radius.sm).background(c.ground).border(1.dp, c.border, Radius.sm)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = Type.title, color = c.ink) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(snapshot: Snapshot, onBack: () -> Unit, onUpdate: (CategoryEntity) -> Unit, onAdd: (CategoryEntity) -> Unit, onMerge: (Long, Long) -> Unit) {
    val c = Leftover.colors
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showIncome by rememberSaveable { mutableStateOf(false) }
    var merging by rememberSaveable { mutableStateOf(false) }
    val kind = if (showIncome) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = snapshot.categories.filter { it.kind == kind }
    val selected = categories.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Categories", style = Type.h2, color = c.ink, modifier = Modifier.weight(1f))
            LButton("+ New", { adding = true; renaming = true; draftName = ""; selectedId = null }, modifier = Modifier.width(96.dp), height = 44.dp)
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Segmented(listOf("Spending", "Income"), if (showIncome) "Income" else "Spending", { showIncome = it == "Income"; selectedId = null; merging = false; adding = false })
        }
        Text("Each category keeps one colour and icon everywhere: lists, budgets and charts.", style = Type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                categories.forEachIndexed { index, cat ->
                    if (index > 0) Divider()
                    val on = cat.id == selectedId
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { selectedId = cat.id; renaming = false; adding = false; merging = false; draftName = cat.name }.background(if (on) c.surfaceAlt else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.alpha(if (cat.hidden) 0.5f else 1f)) { CategoryTile(cat.icon, cat.color, size = 38.dp) }
                        Column(Modifier.weight(1f).alpha(if (cat.hidden) 0.5f else 1f)) {
                            Text(cat.name, style = Type.bodyStrong, color = c.ink)
                            Text(
                                cat.budgetLimit?.let { "Budget ${Money.format(it)}" } ?: "No budget · ${UiFormat.count(snapshot.transactions.count { it.categoryId == cat.id }, "entry", "entries")}",
                                style = Type.small, color = c.muted,
                            )
                        }
                        Text(if (cat.hidden) "Hidden" else if (on) "Selected" else "", style = Type.smallBold, color = c.muted)
                    }
                }
            }

            if (renaming || adding) {
                Card(padding = PaddingValues(16.dp)) {
                    SectionLabel(if (adding) "New category" else "Rename")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().height(52.dp).clip(Radius.md).background(c.surfaceAlt).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (draftName.isEmpty()) Text("Category name", style = Type.bodyStrong, color = c.subtle)
                            BasicTextField(draftName, { draftName = it.take(24) }, textStyle = Type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LButton(
                        if (adding) "Add category" else "Save name", height = 48.dp, enabled = draftName.isNotBlank(),
                        onClick = {
                            if (adding) onAdd(CategoryEntity(name = draftName.trim(), icon = "other", color = if (showIncome) "lime" else "grey", kind = kind, sortOrder = (if (showIncome) 30 else 10) + categories.size))
                            else selected?.let { onUpdate(it.copy(name = draftName.trim())) }
                            renaming = false; adding = false
                        },
                    )
                }
            }
        }
        if (selected != null && !adding) {
            Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${selected.name} — what would you like to do?", style = Type.captionBold, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryPalette.keys.filter { it != "lime" }.forEach { key ->
                        val tile = CategoryPalette.of(key, c.isDark).tile
                        Box(
                            Modifier.size(44.dp).clip(Radius.pill).background(tile)
                                .border(if (selected.color == key) 2.dp else 1.dp, if (selected.color == key) c.ink else c.border, Radius.pill)
                                .clickable(role = Role.Button) { onUpdate(selected.copy(color = key)) }.semantics { contentDescription = "$key colour" },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LButton("Rename", { renaming = true; draftName = selected.name }, modifier = Modifier.weight(1f), height = 48.dp, kind = ButtonKind.Outline)
                    LButton(if (selected.hidden) "Show" else "Hide", { onUpdate(selected.copy(hidden = !selected.hidden)) }, modifier = Modifier.weight(1f), height = 48.dp)
                }
                if (categories.size > 1) {
                    if (!merging) {
                        LButton("Merge into another…", { merging = true }, kind = ButtonKind.Quiet, height = 44.dp)
                    } else {
                        val entries = snapshot.transactions.count { it.categoryId == selected.id }
                        Text("Move ${UiFormat.count(entries, "entry", "entries")} from ${selected.name} into:", style = Type.captionBold, color = c.ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.filter { it.id != selected.id }.forEach { target ->
                                Chip(target.name, false, { onMerge(selected.id, target.id); merging = false; selectedId = null })
                            }
                        }
                        Text("${selected.name} is removed after this. It can't be undone.", style = Type.small, color = c.muted)
                    }
                }
            }
        }
    }
}
