package app.leftover.ui.split

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import app.leftover.ui.components.Card
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.GroupEntity
import app.leftover.data.db.GroupKind
import app.leftover.domain.rupees
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.CenteredHint
import app.leftover.ui.components.Chip
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Type

/** Creates a group of friends. Trips and events can carry a budget, which the group screen tracks against what's spent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddGroupScreen(snapshot: Snapshot, onClose: () -> Unit, onAddPerson: () -> Unit, onSave: (GroupEntity, Set<Long>) -> Unit) {
    val c = Leftover.colors
    val people = snapshot.shared.people
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(GroupKind.FRIENDS) }
    var members by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var budget by rememberSaveable { mutableStateOf("") }
    val hasBudget = kind == GroupKind.TRIP || kind == GroupKind.EVENT

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("New group", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Kind")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupKind.entries.forEach { option -> Chip(option.label(), kind == option, { kind = option }, icon = option.icon()) }
                }
            }
            LabeledField("Name", name, if (kind == GroupKind.TRIP) "Goa trip" else "Flat 4B", { name = it.take(30) }, KeyboardType.Text, capitalize = true)
            if (hasBudget) LabeledField("Budget (optional)", budget, "0", { budget = it.filter(Char::isDigit).take(8) }, KeyboardType.Number, prefix = "₹")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Who's in it")
                if (people.isEmpty()) {
                    Card(padding = PaddingValues(16.dp)) {
                        Text("No friends yet", style = Type.bodyExtra, color = c.ink)
                        Text("Add the people in this group first. You're always part of it.", style = Type.caption, color = c.muted)
                        Spacer(Modifier.height(12.dp))
                        LButton("Add a friend", onAddPerson, kind = ButtonKind.Outline, icon = LeftoverIcons.Plus, height = 48.dp)
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        people.forEach { person ->
                            Chip(person.name, person.id in members, { members = if (person.id in members) members - person.id else members + person.id })
                        }
                        Chip("Add friend", false, onAddPerson, icon = LeftoverIcons.Plus, dashed = true)
                    }
                    Text("You're always in the group.", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        LButton("Create group", enabled = name.isNotBlank() && members.isNotEmpty(), onClick = {
            val limit = budget.toLongOrNull()?.takeIf { hasBudget && it > 0 }?.let(::rupees)
            onSave(GroupEntity(name = name.trim(), kind = kind, budget = limit), members)
            onClose()
        })
    }
}
