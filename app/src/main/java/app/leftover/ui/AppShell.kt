package app.leftover.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

data class Tab(val label: String, val icon: ImageVector, val route: String, val sections: Set<String>)

val tabs = listOf(
    Tab("Home", LeftoverIcons.Home, Routes.HOME, setOf(Routes.HOME)),
    Tab("Activity", LeftoverIcons.List, Routes.ACTIVITY, setOf(Routes.ACTIVITY)),
    Tab("Plan", LeftoverIcons.Target, Routes.PLAN, setOf(Routes.PLAN, Routes.GOALS, Routes.BILLS, Routes.WISHLIST)),
    Tab("Split", LeftoverIcons.People, Routes.SPLIT, setOf(Routes.SPLIT)),
)

/** Four labelled tabs with a raised Add button in the middle, inside the thumb zone. */
@Composable
fun BottomBar(currentRoute: String?, onTab: (String) -> Unit, onAdd: () -> Unit) {
    val c = Leftover.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 72.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.take(2).forEach { TabItem(it, currentRoute in it.sections, onTab) }
            Box(
                Modifier.offset(y = (-6).dp).size(56.dp).clip(Radius.lg).background(c.hero)
                    .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "Add expense" },
                contentAlignment = Alignment.Center,
            ) { Icon(LeftoverIcons.Plus, null, tint = c.heroNumber, modifier = Modifier.size(26.dp)) }
            tabs.drop(2).forEach { TabItem(it, currentRoute in it.sections, onTab) }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onTab: (String) -> Unit) {
    val c = Leftover.colors
    Column(
        Modifier.widthIn(min = 64.dp).heightIn(min = 60.dp).clip(Radius.md).clickable(role = Role.Tab) { onTab(tab.route) }.semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(width = 48.dp, height = 30.dp).clip(Radius.pill).background(if (selected) c.lime else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(tab.icon, null, tint = if (selected) c.onLime else c.muted, modifier = Modifier.size(22.dp))
        }
        Text(tab.label, style = if (selected) Type.smallBold else Type.small, color = if (selected) c.ink else c.muted, maxLines = 1)
    }
}

/** The "Saved · Undo" message that floats above the bottom bar after a quick save. */
@Composable
fun ToastHost(toast: Toast?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    AnimatedVisibility(visible = toast != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = modifier) {
        val shown = toast
        if (shown != null) {
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 56.dp).clip(Radius.md).background(c.hero)
                    .border(1.dp, c.hairline, Radius.md).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .semantics { contentDescription = "${shown.title}. ${shown.subtitle}" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(LeftoverIcons.Check, null, tint = c.heroNumber, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(shown.title, style = Type.bodyExtra, color = c.onHero)
                    Text(shown.subtitle, style = Type.small, color = c.onHeroMuted)
                }
                if (shown.undoTransactionId != null) {
                    Box(
                        Modifier.height(44.dp).clip(Radius.sm).background(c.lime).clickable(role = Role.Button, onClick = onUndo).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Undo", style = Type.captionBold, color = c.onLime) }
                }
            }
        }
    }
}
