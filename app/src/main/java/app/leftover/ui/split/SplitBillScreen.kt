package app.leftover.ui.split

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.NewSharedExpense
import app.leftover.data.Snapshot
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryKind
import app.leftover.domain.ME
import app.leftover.domain.Money
import app.leftover.domain.SplitCalculator
import app.leftover.domain.SplitMode
import app.leftover.domain.rupees
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Banner
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconTile
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.Segmented
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

private const val EQUALLY = "Equally"
private const val EXACT = "Exact"
private const val SHARES = "Shares"

/**
 * Splits one bill between you and friends. Nothing can be saved until the parts add up to the total,
 * so a split can never leave money unaccounted for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitBillScreen(
    snapshot: Snapshot,
    initialPersonId: Long?,
    initialGroupId: Long?,
    onClose: () -> Unit,
    onAddPerson: () -> Unit,
    onSave: (NewSharedExpense, Boolean) -> Unit,
) {
    val c = Leftover.colors
    val book = snapshot.shared
    val hidden = snapshot.settings.hideBalances
    val categories = snapshot.categories.filter { !it.hidden && it.kind == CategoryKind.EXPENSE }
    val accounts = snapshot.accounts.filter { !it.archived && (it.type == AccountType.BANK || it.type == AccountType.CASH || it.type == AccountType.CARD) }

    var digits by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    var paidBy by rememberSaveable { mutableStateOf(ME) }
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull { it.type == AccountType.BANK }?.id ?: accounts.firstOrNull()?.id ?: -1L) }
    var groupId by rememberSaveable { mutableStateOf(initialGroupId ?: -1L) }
    var friends by rememberSaveable {
        mutableStateOf(
            when {
                initialGroupId != null -> book.groupMembers(initialGroupId).map { it.id }.toSet()
                initialPersonId != null -> setOf(initialPersonId)
                else -> emptySet()
            },
        )
    }
    var meIn by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf(EQUALLY) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var exactDigits by rememberSaveable { mutableStateOf(emptyMap<Long, String>()) }
    var shareCounts by rememberSaveable { mutableStateOf(emptyMap<Long, Int>()) }

    val payer = if (paidBy == ME || paidBy in friends) paidBy else ME
    val youAreIn = meIn || payer != ME
    val participants = (if (youAreIn) listOf(ME) else emptyList()) + friends.sorted()
    val total = rupees(digits.toLongOrNull() ?: 0L)
    val splitMode = when (mode) {
        EXACT -> SplitMode.EXACT
        SHARES -> SplitMode.SHARES
        else -> SplitMode.EQUAL
    }
    val result = SplitCalculator.calculate(
        total, participants, splitMode,
        exact = exactDigits.mapValues { rupees(it.value.toLongOrNull() ?: 0L) },
        shares = shareCounts,
    )
    val yourShare = result.amounts[ME] ?: 0L
    val canSave = total > 0 && friends.isNotEmpty() && result.balanced && (payer != ME || accountId >= 0)

    fun nameOf(id: Long) = if (id == ME) "You" else book.person(id)?.name ?: "Friend"

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Split a bill", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // The bill: the amount is the biggest thing on the screen, with its name under it.
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Total bill", style = Type.caption, color = c.onHeroMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹", style = Type.display36, color = c.onHeroMuted, modifier = Modifier.padding(end = 6.dp))
                    Box(Modifier.weight(1f)) {
                        if (digits.isEmpty()) Text("0", style = Type.display48, color = c.onHeroMuted.copy(alpha = 0.6f))
                        BasicTextField(
                            digits, { digits = it.filter(Char::isDigit).take(7) }, textStyle = Type.display48.copy(color = c.heroNumber), cursorBrush = SolidColor(c.heroNumber),
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.onHeroMuted.copy(alpha = 0.25f)))
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                    if (title.isEmpty()) Text("What was it for? (optional)", style = Type.bodyStrong, color = c.onHeroMuted)
                    BasicTextField(
                        title, { title = it.take(40) }, textStyle = Type.bodyStrong.copy(color = c.onHero), cursorBrush = SolidColor(c.onHero), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            FormSection("Category") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(categories) { cat -> Chip(cat.name, categoryId == cat.id, { categoryId = cat.id }) }
                }
            }

            FormSection("Split with") {
                if (book.groups.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(book.groups) { group ->
                            Chip(group.name, groupId == group.id, {
                                if (groupId == group.id) groupId = -1L
                                else {
                                    groupId = group.id
                                    friends = book.groupMembers(group.id).map { it.id }.toSet()
                                }
                            }, icon = group.kind.icon())
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.people.forEach { person ->
                        Chip(person.name, person.id in friends, { friends = if (person.id in friends) friends - person.id else friends + person.id })
                    }
                    Chip("Add friend", false, onAddPerson, icon = LeftoverIcons.Plus, dashed = true)
                }
            }

            if (friends.isNotEmpty()) {
                Card(padding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Paid by")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("You", payer == ME, { paidBy = ME })
                                friends.sorted().forEach { id -> Chip(nameOf(id), payer == id, { paidBy = id }) }
                            }
                        }
                        if (payer == ME) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel("Paid from")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    accounts.forEach { account -> Chip(account.name, accountId == account.id, { accountId = account.id }) }
                                }
                            }
                            Divider()
                            ToggleRow("I'm part of this bill", "Turn off if you're covering it for your friends", meIn, { meIn = it })
                        }
                    }
                }

                FormSection("How to split") {
                    Segmented(listOf(EQUALLY, EXACT, SHARES), mode, { mode = it })
                    Text(
                        when (mode) {
                            EXACT -> "Type what each person owes. It has to add up to the bill."
                            SHARES -> "Split in proportion. Someone with 2 shares pays twice as much as someone with 1."
                            else -> "Everyone pays the same amount."
                        },
                        style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        participants.forEachIndexed { index, id ->
                            if (index > 0) Divider()
                            ParticipantRow(
                                name = nameOf(id),
                                person = book.person(id),
                                owed = result.amounts[id] ?: 0L,
                                mode = mode,
                                exact = exactDigits[id].orEmpty(),
                                onExact = { exactDigits = exactDigits + (id to it.filter(Char::isDigit).take(7)) },
                                shares = shareCounts[id] ?: 1,
                                onShares = { shareCounts = shareCounts + (id to it.coerceIn(0, 20)) },
                                hidden = hidden,
                            )
                        }
                    }
                    if (mode == EXACT && total > 0 && !result.balanced) {
                        val left = result.difference
                        Banner(
                            text = if (left > 0) "${Money.format(left)} left to assign" else "${Money.format(-left)} more than the bill",
                            background = c.emberBg, foreground = c.emberText,
                        )
                    }
                }

                if (total > 0 && result.balanced) Banner("Adds up to ${Money.format(total)}", c.lime, c.onLime)
                if (mode == EQUALLY && friends.isNotEmpty()) {
                    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                        ToggleRow("Repeat every month", "Like rent or Wi-Fi. Leftover adds it again on the ${UiFormat.ordinal(java.time.LocalDate.now().dayOfMonth)} of each month, split the same way.", repeat, { repeat = it })
                    }
                }

                if (total > 0 && result.balanced) {
                    Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.lavender).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your share is ${Money.display(yourShare, hidden)}", style = Type.bodyExtra, color = c.lavenderInk)
                        Text(
                            if (payer == ME) "You paid ${Money.display(total, hidden)}, so Left to spend drops by ${Money.display(total, hidden)} today. ${Money.display(total - yourShare, hidden)} shows as coming back to you, and budgets count only your ${Money.display(yourShare, hidden)}."
                            else "You'll owe ${nameOf(payer)} ${Money.display(yourShare, hidden)}. It counts toward your budgets now, and no money leaves your accounts until you settle.",
                            style = Type.small, color = c.lavenderMuted,
                        )
                    }
                }
            }
        }
        LButton(
            text = "Save split", enabled = canSave,
            onClick = {
                val category = categories.firstOrNull { it.id == categoryId }
                onSave(
                    NewSharedExpense(
                        title = title.trim().ifBlank { category?.name ?: "Shared bill" },
                        total = total,
                        paidBy = payer,
                        shares = result.amounts,
                        groupId = groupId.takeIf { it >= 0 },
                        categoryId = category?.id,
                        accountId = accountId.takeIf { payer == ME && it >= 0 },
                    ),
                    repeat && mode == EQUALLY,
                )
                onClose()
            },
        )
    }
}

/** A labelled block of the form: the label sits 8dp above its content, and blocks sit 20dp apart. */
@Composable
private fun FormSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun ParticipantRow(
    name: String,
    person: app.leftover.data.db.PersonEntity?,
    owed: Long,
    mode: String,
    exact: String,
    onExact: (String) -> Unit,
    shares: Int,
    onShares: (Int) -> Unit,
    hidden: Boolean,
) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PersonAvatar(person, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = Type.bodyStrong, color = c.ink, maxLines = 1)
            if (mode == SHARES) Text(Money.display(owed, hidden), style = Type.small, color = c.muted)
        }
        when (mode) {
            EXACT -> Row(
                Modifier.width(120.dp).heightIn(min = 44.dp).clip(Radius.sm).border(1.dp, c.border, Radius.sm).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("₹", style = Type.bodyStrong, color = c.muted)
                Box(Modifier.weight(1f).padding(start = 4.dp)) {
                    if (exact.isEmpty()) Text("0", style = Type.bodyStrong, color = c.subtle)
                    BasicTextField(
                        exact, onExact, textStyle = Type.bodyStrong.copy(color = c.ink, textAlign = TextAlign.End), cursorBrush = SolidColor(c.ink), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SHARES -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(LeftoverIcons.Minus, { onShares(shares - 1) }, "Fewer shares for $name")
                Text("$shares", style = Type.bodyExtra, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
                IconTile(LeftoverIcons.Plus, { onShares(shares + 1) }, "More shares for $name")
            }
            else -> Amount(owed, hidden, Type.bodyExtra)
        }
    }
}
