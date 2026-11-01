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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.FeatureIcons
import app.leftover.ui.components.FeatureIcon
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

data class Tab(val label: String, val icon: ImageVector, val route: String, val sections: Set<String>)

val tabs = listOf(
    Tab("Home", FeatureIcons.tabs.getValue("Home"), Routes.HOME, setOf(Routes.HOME)),
    Tab("Activity", FeatureIcons.tabs.getValue("Activity"), Routes.ACTIVITY, setOf(Routes.ACTIVITY)),
    Tab("Plan", FeatureIcons.tabs.getValue("Plan"), Routes.PLAN, setOf(Routes.PLAN, Routes.GOALS, Routes.BILLS, Routes.WISHLIST, Routes.SPLIT)),
    Tab("Insights", FeatureIcons.tabs.getValue("Insights"), Routes.INSIGHTS, setOf(Routes.INSIGHTS)),
)

/**
 * Four tabs with icons and labels and a raised Add button, on a floating card inside the thumb zone. Five equal slots
 * keep the tabs evenly spaced around Add. The card is drawn behind the row rather than clipping it, so the raised
 * button stays whole. The card is solid; there's no strip behind it, so the page shows around it, and screens leave
 * room at the bottom so their last item can scroll clear of it.
 */
@Composable
fun BottomBar(currentRoute: String?, onTab: (String) -> Unit, onAdd: () -> Unit) {
    val c = Leftover.colors
    Column(Modifier.fillMaxWidth()) {
        // No backdrop: the bar floats and the page stays visible around and behind it.
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Box(Modifier.fillMaxWidth().height(68.dp)) {
                // A solid card with a soft shadow (a hairline in dark mode, where shadows vanish).
                Box(
                    Modifier.matchParentSize()
                        .let { if (c.isDark) it else it.shadow(12.dp, Radius.xl, ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f)) }
                        .clip(Radius.xl).background(c.surface)
                        .let { if (c.isDark) it.border(1.dp, c.hairline, Radius.xl) else it },
                )
                Row(Modifier.matchParentSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.take(2).forEach { TabItem(it, currentRoute in it.sections, onTab, Modifier.weight(1f)) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.offset(y = (-18).dp).size(58.dp)
                                .let { if (c.isDark) it else it.shadow(10.dp, Radius.lg, ambientColor = c.ink.copy(alpha = 0.3f), spotColor = c.ink.copy(alpha = 0.3f)) }
                                .clip(Radius.lg).background(c.hero)
                                .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "Add an expense" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(LeftoverIcons.Plus, null, tint = c.heroNumber, modifier = Modifier.size(26.dp)) }
                    }
                    tabs.drop(2).forEach { TabItem(it, currentRoute in it.sections, onTab, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onTab: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Column(
        modifier.heightIn(min = 56.dp).clip(Radius.md).clickable(role = Role.Tab) { onTab(tab.route) }.semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 44.dp, height = 26.dp).clip(Radius.pill).background(if (selected) c.lime else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { FeatureIcon(tab.icon, if (selected) c.onLime else c.muted, 20.dp) }
        Spacer(Modifier.height(3.dp))
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
                if (shown.undoTransactionId != null || shown.restore != null || shown.revert != null) {
                    Box(
                        Modifier.height(44.dp).clip(Radius.sm).background(c.lime).clickable(role = Role.Button, onClick = onUndo).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Undo", style = Type.captionBold, color = c.onLime) }
                }
            }
        }
    }
}
