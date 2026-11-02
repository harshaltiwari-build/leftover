package app.leftover.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.WishStatus
import app.leftover.domain.Money
import app.leftover.domain.PayCycle
import app.leftover.domain.RangePreset
import app.leftover.domain.RangeReport
import app.leftover.domain.toLocalDate
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.Card
import app.leftover.ui.components.Divider
import app.leftover.ui.components.IconTile
import app.leftover.ui.components.ListRow
import app.leftover.ui.components.Avatar
import app.leftover.ui.components.Chip
import app.leftover.ui.components.FeatureIcon
import app.leftover.ui.components.IconBadge
import app.leftover.ui.theme.FeatureIcons
import app.leftover.data.ProfilePhotoStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.time.Duration
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

/** What Profile's rows open. */
data class ProfileActions(
    val onBack: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenRhythm: () -> Unit,
    val onOpenBills: () -> Unit,
    val onOpenLimits: () -> Unit,
    val onOpenGoals: () -> Unit,
    val onOpenBackup: () -> Unit,
    /** A picture picked from the gallery. */
    val onPickPhoto: (android.net.Uri) -> Unit = {},
    /** The camera finished writing into this file name; false when it was cancelled. */
    val onCameraPhoto: (String, Boolean) -> Unit = { _, _ -> },
    val onRemovePhoto: () -> Unit = {},
)

/** Numbers about you and your money on this phone. There is no account: this is all worked out from your own entries. */
private data class ProfileStats(
    val entries: Int,
    /** Entries per day since the first one, rounded. */
    val perDay: Int,
    val cycles: Int,
    val since: String?,
    val movedToGoals: Long,
    val keptByWaiting: Long,
    val skipped: Int,
    val calmest: Pair<PayCycle, Long>?,
    val biggest: Pair<String, Long>?,
    val regrets: Long,
)

private fun statsOf(snapshot: Snapshot): ProfileStats {
    val ledger = snapshot.ledger
    val first = snapshot.transactions.minOfOrNull { it.occurredAt }?.toLocalDate()
    val cycles = if (first == null) 0 else generateSequence(snapshot.cycle) { it.previous() }.takeWhile { !it.end.isBefore(first) }.take(120).count()
    val past = generateSequence(snapshot.cycle.previous()) { it.previous() }.take((cycles - 1).coerceAtLeast(0)).filter { it.start.year == snapshot.today.year }
        .map { it to ledger.totalSpent(it) }.filter { it.second > 0 }.toList()
    val year = RangeReport.build(ledger, RangePreset.THIS_YEAR.span(snapshot.today, snapshot.cycle))
    val top = year.categories.firstOrNull()
    val skipped = snapshot.wishes.filter { it.status == WishStatus.SKIPPED }
    return ProfileStats(
        entries = snapshot.transactions.size,
        perDay = if (first == null) 0 else Math.round(snapshot.transactions.size.toDouble() / (java.time.temporal.ChronoUnit.DAYS.between(first, snapshot.today) + 1)).toInt(),
        cycles = cycles,
        since = first?.let { it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (it.year != snapshot.today.year) " ${it.year}" else "" },
        movedToGoals = snapshot.goals.sumOf { ledger.goalSaved(it.id) }.coerceAtLeast(0),
        keptByWaiting = skipped.sumOf { it.price },
        skipped = skipped.size,
        calmest = past.minByOrNull { it.second },
        biggest = top?.let { it.category.name to (it.spent * 100 / year.spent.coerceAtLeast(1)) },
        regrets = year.regret,
    )
}

@Composable
fun ProfileScreen(snapshot: Snapshot, actions: ProfileActions) {
    val c = Leftover.colors
    val s = snapshot.settings
    val hidden = s.hideBalances
    val stats = remember(snapshot.ledger, snapshot.wishes) { statsOf(snapshot) }
    val name = s.name.ifBlank { "You" }
    val backedUp = if (s.lastBackupAt == 0L) null else Duration.between(Instant.ofEpochMilli(s.lastBackupAt), Instant.now()).toDays()
    val bills = snapshot.bills.filter { !it.archived }.sumOf { it.amount } + snapshot.emis.filter { !it.archived && it.remainingMonths > 0 }.sumOf { it.monthlyAmount }
    val budget = s.spendingBudget.takeIf { it > 0 } ?: snapshot.categories.sumOf { it.budgetLimit ?: 0L }
    val saving = snapshot.goals.filter { !it.archived }.sumOf { it.perCycle }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            BackButton(actions.onBack)
            Text("Profile", style = Type.title, color = c.ink)
            IconTile(LeftoverIcons.Settings, actions.onOpenSettings, "Settings")
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoEditor(name, s.profilePhoto, actions)
                Text(name, style = Type.h1, color = c.ink)
                Text("${s.incomeType} · paid on the ${ordinal(s.paydayDay)} · ₹ INR", style = Type.caption, color = c.muted, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    stats.since?.let { Pill("Tracking since $it", c.surface, c.ink) }
                    Pill(
                        when (backedUp) { null -> "Not backed up yet"; 0L -> "Backed up today"; 1L -> "Backed up yesterday"; else -> "Backed up $backedUp days ago" },
                        if (backedUp != null && backedUp <= 30) Color(0xFFE3F0C9) else c.emberBg, if (backedUp != null && backedUp <= 30) Color(0xFF3F5A0B) else c.emberText,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Entries logged", stats.entries.toString(), if (stats.entries == 0) "Start with +" else if (stats.perDay < 1) "A few a week" else "About ${stats.perDay} a day", c.surface, c.ink, c.muted, Modifier.weight(1f))
                    Stat("Cycles tracked", stats.cycles.toString(), stats.since?.let { "Since $it" } ?: "None yet", c.surface, c.ink, c.muted, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Moved to goals", Money.display(stats.movedToGoals, hidden), "${snapshot.goals.count { !it.archived }} goals", c.lime, c.onLime, c.onLime.copy(alpha = 0.75f), Modifier.weight(1f), bordered = false)
                    Stat("Kept by waiting", Money.display(stats.keptByWaiting, hidden), "${stats.skipped} wishlist ${if (stats.skipped == 1) "item" else "items"} skipped", c.lavender, c.lavenderInk, c.lavenderMuted, Modifier.weight(1f), bordered = false)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your money rhythm", style = Type.title, color = c.ink, modifier = Modifier.padding(horizontal = 4.dp))
                Card(shape = Radius.lg, padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    RhythmRow("Payday", ordinal(s.paydayDay), "lime", actions.onOpenRhythm)
                    Divider()
                    RhythmRow("Comes in", if (s.usualIncome > 0) Money.display(s.usualIncome, hidden) + " / mo" else "Add", "pistachio", actions.onOpenRhythm)
                    Divider()
                    RhythmRow("Bills and EMIs", Money.display(bills, hidden) + " / mo", "butter", actions.onOpenBills)
                    Divider()
                    RhythmRow("Spending budget", if (budget > 0) Money.display(budget, hidden) + " / mo" else "Set a budget", "peach", actions.onOpenLimits)
                    Divider()
                    RhythmRow("Saving", if (saving > 0) Money.display(saving, hidden) + " / payday" else "Add a goal", "lavender", actions.onOpenGoals)
                }
            }

            if (stats.calmest != null || stats.biggest != null || stats.regrets > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your year so far", style = Type.title, color = c.ink, modifier = Modifier.padding(horizontal = 4.dp))
                    Card(shape = Radius.lg, padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        var first = true
                        stats.calmest?.let { (cycle, spent) ->
                            YearRow("Calmest cycle", "The cycle you spent least", "${cycle.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · ${Money.display(spent, hidden)}", c.limeDeep)
                            first = false
                        }
                        stats.biggest?.let { (cat, share) ->
                            if (!first) Divider()
                            YearRow("Biggest category", "Since January", "$cat · $share%", CategoryPalette.of("peach", c.isDark).bar)
                            first = false
                        }
                        if (stats.regrets > 0) {
                            if (!first) Divider()
                            YearRow("Regrets marked", "Spends you said weren't worth it", Money.display(stats.regrets, hidden), c.ember)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).clip(Radius.sm).background(c.heroNumber.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(LeftoverIcons.Lock, null, tint = c.heroNumber, modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This profile lives on your phone", style = Type.bodyExtra, color = c.onHero)
                    Text(
                        "No account, no bank login, no ads. Leftover only goes online when you check for an update, and sends nothing about you, so your name and numbers go nowhere unless you back them up or export.",
                        style = Type.caption, color = c.onHeroMuted,
                    )
                    if (backedUp == null || backedUp > 30) {
                        Spacer(Modifier.height(4.dp))
                        Text("Back up now →", style = Type.captionBold, color = c.heroNumber, modifier = Modifier.clip(Radius.sm).clickable(role = Role.Button, onClick = actions.onOpenBackup).padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
}

private fun ordinal(day: Int): String = day.toString() + when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, style = Type.smallBold, color = fg, modifier = Modifier.clip(Radius.pill).background(bg).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun Stat(label: String, value: String, note: String, bg: Color, ink: Color, sub: Color, modifier: Modifier = Modifier, bordered: Boolean = true) {
    Card(shape = Radius.lg, color = bg, bordered = bordered, padding = PaddingValues(14.dp), modifier = modifier) {
        Text(label, style = Type.smallBold, color = sub)
        Text(value, style = Type.h2, color = ink, maxLines = 1)
        Text(note, style = Type.small, color = sub, maxLines = 2)
    }
}

/**
 * The photo at the top of Profile. Tapping it, or "Add photo" / "Change photo", shows the choices: the gallery (no
 * permission needed), the camera, or removing it.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhotoEditor(name: String, photo: String?, actions: ProfileActions) {
    val c = Leftover.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var pendingCamera by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { actions.onPickPhoto(uri); open = false }
    }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        pendingCamera?.let { actions.onCameraPhoto(it, ok) }
        pendingCamera = null
        if (ok) open = false
    }
    Box(
        Modifier.size(96.dp).border(3.dp, c.lime, Radius.pill).padding(6.dp).clip(Radius.pill)
            .clickable(role = Role.Button, onClickLabel = if (photo == null) "Add photo" else "Change photo") { open = !open },
        contentAlignment = Alignment.Center,
    ) { Avatar(name.takeIf { it != "You" }.orEmpty(), photo, 84.dp, Type.display36) }
    Row(
        Modifier.clip(Radius.pill).clickable(role = Role.Button) { open = !open }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FeatureIcon(LeftoverIcons.Camera, c.ink, 16.dp)
        Text(if (photo == null) "Add photo" else "Change photo", style = Type.captionBold, color = c.ink)
    }
    if (open) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("Choose from gallery", false, {
                gallery.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, icon = LeftoverIcons.Image, role = Role.Button)
            Chip("Take a photo", false, {
                val (file, uri) = ProfilePhotoStore.cameraTarget(context)
                pendingCamera = file
                runCatching { camera.launch(uri) }.onFailure { pendingCamera = null; actions.onCameraPhoto(file, false) }
            }, icon = LeftoverIcons.Camera, role = Role.Button)
            if (photo != null) Chip("Remove photo", false, { actions.onRemovePhoto(); open = false }, icon = LeftoverIcons.Trash, role = Role.Button)
        }
        Text("Your photo stays on this phone. It isn't in backups.", style = Type.small, color = c.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RhythmRow(name: String, value: String, color: String, onClick: () -> Unit) {
    val c = Leftover.colors
    ListRow(
        onClick = onClick,
        leading = {
            val palette = CategoryPalette.of(color, c.isDark)
            FeatureIcons.rhythm[name]?.let { IconBadge(it, palette.tile, c.ink) } ?: Box(Modifier.size(38.dp).clip(Radius.sm).background(palette.tile))
        },
        title = name,
        trailing = {
            Text(value, style = Type.captionBold, color = c.ink)
            Icon(LeftoverIcons.Chevron, null, tint = c.subtle, modifier = Modifier.size(16.dp))
        },
    )
}

@Composable
private fun YearRow(name: String, note: String, value: String, dot: Color) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).clip(Radius.pill).background(dot))
        Column(Modifier.weight(1f)) {
            Text(name, style = Type.bodyStrong, color = c.ink)
            Text(note, style = Type.small, color = c.muted)
        }
        Text(value, style = Type.captionBold, color = c.ink)
    }
}
