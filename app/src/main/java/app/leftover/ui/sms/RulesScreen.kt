package app.leftover.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.leftover.data.db.CategoryKind
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.CategoryTile
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconTile
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Type

/** Merchant-to-category rules for bank texts: "if the name contains Swiggy, it is Eating out". Rules beat what Leftover has learned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(snapshot: Snapshot, onBack: () -> Unit, onAdd: (String, Long) -> Unit, onDelete: (Long) -> Unit) {
    val c = Leftover.colors
    val categories = snapshot.categories.filter { !it.hidden && it.kind == CategoryKind.EXPENSE }
    var pattern by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val valid = pattern.trim().length >= 2 && categoryId != null

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Capture rules", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "When a bank text names a merchant that contains the words below, Leftover files it under the category you pick. Capital letters and punctuation don't matter.",
                style = Type.caption, color = c.muted,
            )
            Card(padding = PaddingValues(16.dp)) {
                Text("New rule", style = Type.bodyExtra, color = c.ink)
                Spacer(Modifier.height(12.dp))
                LabeledField("If the merchant contains", pattern, "swiggy", { pattern = it.take(30) }, KeyboardType.Text)
                Spacer(Modifier.height(12.dp))
                SectionLabel("File it under")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat -> Chip(cat.name, categoryId == cat.id, { categoryId = cat.id }) }
                }
                Spacer(Modifier.height(14.dp))
                LButton("Add rule", enabled = valid, kind = ButtonKind.Lime, height = 48.dp, onClick = {
                    onAdd(pattern.trim(), categoryId!!)
                    pattern = ""
                    categoryId = null
                })
            }

            SectionLabel("Your rules")
            if (snapshot.rules.isEmpty()) {
                Card(padding = PaddingValues(20.dp)) { Text("No rules yet. Leftover still learns from the categories you pick.", style = Type.caption, color = c.muted) }
            } else {
                Card(padding = PaddingValues(horizontal = 16.dp)) {
                    snapshot.rules.forEachIndexed { index, rule ->
                        if (index > 0) Divider()
                        val category = snapshot.ledger.category(rule.categoryId)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryTile(category?.icon ?: "other", category?.color ?: "grey", size = 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text("Contains “${rule.pattern}”", style = Type.bodyStrong, color = c.ink, maxLines = 1)
                                Text("→ ${category?.name ?: "A category that was removed"}", style = Type.small, color = c.muted)
                            }
                            IconTile(LeftoverIcons.Trash, { onDelete(rule.id) }, "Delete the rule for ${rule.pattern}")
                        }
                    }
                }
            }
        }
    }
}
