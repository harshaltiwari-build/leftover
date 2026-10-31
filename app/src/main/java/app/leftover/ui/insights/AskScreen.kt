package app.leftover.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.domain.Ask
import app.leftover.domain.AskAnswer
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.home.TransactionRow
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

/**
 * Questions about your own money, answered on the phone. Every answer shows the entries it was built from, so it can
 * always be checked, and nothing is sent anywhere.
 */
@Composable
fun AskScreen(snapshot: Snapshot, onBack: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    var input by rememberSaveable { mutableStateOf("") }
    val asked = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) { mutableStateListOf<String>() }
    val list = rememberLazyListState()

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        asked.add(q)
        input = ""
    }
    LaunchedEffect(asked.size) { if (asked.isNotEmpty()) list.animateScrollToItem(asked.size) }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Column {
                Text("Ask about your money", style = Type.title, color = c.ink)
                Text("Answers use only your own entries, on this phone", style = Type.small, color = c.muted)
            }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(color = c.lavender, bordered = false, padding = PaddingValues(16.dp)) {
                    Text("What would you like to know?", style = Type.bodyExtra, color = c.lavenderInk)
                    Text(
                        "Try spending on a category or at a place (\"Swiggy last month\"), what changed, bills due, goals, biggest spends, what's left today, or regrets.",
                        style = Type.caption, color = c.lavenderMuted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            items(asked.size) { i ->
                val q = asked[i]
                val answer = remember(q, snapshot.ledger, hidden) { Ask.answer(q, snapshot.ledger, hidden) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(q, style = Type.bodyStrong, color = c.onChipSelected, modifier = Modifier.widthIn(max = 300.dp).clip(Radius.lg).background(c.chipSelected).padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                    AnswerCard(answer, snapshot, hidden, onOpenTransaction, onTry = ::ask)
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(Ask.suggestions) { s -> Chip(s, false, { ask(s) }, role = Role.Button) }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f).height(52.dp).clip(Radius.pill).background(c.surface).border(1.dp, c.hairline, Radius.pill).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                if (input.isEmpty()) Text("Ask a question", style = Type.body, color = c.muted)
                BasicTextField(
                    input, { input = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { ask(input) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LButton("Ask", { ask(input) }, kind = ButtonKind.Lime, enabled = input.isNotBlank(), height = 52.dp)
        }
        Text(
            "Explains your own data. It doesn't give investment advice.", style = Type.small, color = c.muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun AnswerCard(answer: AskAnswer, snapshot: Snapshot, hidden: Boolean, onOpenTransaction: (Long) -> Unit, onTry: (String) -> Unit) {
    val c = Leftover.colors
    var showAll by remember(answer) { mutableStateOf(false) }
    Card(shape = Radius.xl, padding = PaddingValues(18.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        answer.period?.let { Text(it, style = Type.smallBold, color = c.muted) }
        Text(answer.headline, style = if (answer.headline.length > 14) Type.h2 else Type.display36, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(answer.summary, style = Type.body, color = c.ink)
        if (!answer.understood) {
            Spacer(Modifier.height(10.dp))
            Ask.suggestions.take(4).forEach { s ->
                Text("“$s”", style = Type.captionBold, color = c.lavenderMuted, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onTry(s) }.padding(vertical = 8.dp))
            }
        }
        if (answer.lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            answer.lines.forEachIndexed { i, (label, value) ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = Type.caption, color = c.ink, modifier = Modifier.weight(1f))
                    Text(value, style = Type.captionBold, color = c.ink)
                }
            }
        }
        if (answer.entries.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (showAll) "Hide entries" else "Show the ${answer.entries.size} ${if (answer.entries.size == 1) "entry" else "entries"} behind this",
                style = Type.captionBold, color = c.ink,
                modifier = Modifier.clickable(role = Role.Button) { showAll = !showAll }.padding(vertical = 8.dp),
            )
            if (showAll) {
                answer.entries.take(50).forEachIndexed { i, tx ->
                    if (i > 0) Divider()
                    TransactionRow(tx, snapshot.ledger, hidden) { onOpenTransaction(tx.id) }
                }
                if (answer.entries.size > 50) Text("And ${answer.entries.size - 50} more. Reports shows them all.", style = Type.small, color = c.muted)
            }
        }
    }
}
