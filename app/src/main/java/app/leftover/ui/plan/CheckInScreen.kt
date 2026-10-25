package app.leftover.ui.plan

import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.leftover.data.Snapshot
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.ChallengeEntity
import app.leftover.domain.ChallengeProgress
import app.leftover.domain.WeeklyReview
import app.leftover.ui.UiFormat
import app.leftover.ui.components.Amount
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.components.ProgressBar
import app.leftover.ui.components.SectionLabel
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import androidx.compose.ui.text.input.KeyboardType

/** A two-minute look back at the week, and challenges you set yourself. No streaks, nothing resets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(snapshot: Snapshot, onBack: () -> Unit, onAddChallenge: (ChallengeEntity) -> Unit, onDeleteChallenge: (Long) -> Unit) {
    val c = Leftover.colors
    val hidden = snapshot.settings.hideBalances
    val review = WeeklyReview.of(snapshot.ledger)
    val today = snapshot.today
    var name by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf<Long?>(null) }
    var days by rememberSaveable { mutableIntStateOf(7) }
    val categories = snapshot.categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Weekly check-in", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.hero).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Spent in the last 7 days", style = Type.caption, color = c.onHeroMuted)
                Amount(review.spent, hidden, Type.display36, color = c.heroNumber)
                Row {
                    if (review.previousSpent == 0L && review.spent == 0L) {
                        Text("Nothing spent this week or the week before.", style = Type.caption, color = c.onHeroMuted)
                    } else {
                        Amount(kotlin.math.abs(review.change), hidden, Type.captionBold, color = c.onHero)
                        Text(if (review.change > 0) " more than the week before" else if (review.change < 0) " less than the week before" else " the same as the week before", style = Type.caption, color = c.onHeroMuted)
                    }
                }
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                val top = snapshot.ledger.category(review.topCategoryId)
                Fact("Biggest category", top?.name ?: "None yet") { if (top != null) Amount(review.topCategorySpent, hidden, Type.bodyExtra) }
                Divider()
                Fact("Days with no spending", "${review.noSpendDays} of 7") {}
                Divider()
                Fact("Left to spend", "${UiFormat.count(review.daysToPayday, "day")} to payday") { Amount(review.leftToSpend, hidden, Type.bodyExtra) }
            }
            Text("This is a look back, not a score. A week is just a week.", style = Type.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))

            SectionLabel("Your challenges")
            if (snapshot.challenges.isEmpty()) {
                Card(padding = PaddingValues(16.dp)) {
                    Text("Set yourself a short challenge, like no eating out for a week. It only counts the days with no spending, and nothing is lost if you slip.", style = Type.caption, color = c.muted)
                }
            }
            snapshot.challenges.forEach { challenge ->
                val progress = ChallengeProgress.of(challenge, snapshot.transactions, today)
                Card(padding = PaddingValues(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(challenge.name, style = Type.bodyStrong, color = c.ink)
                            Text(
                                (snapshot.ledger.category(challenge.categoryId)?.name ?: "Any spending") + " · " + UiFormat.count(progress.totalDays, "day"),
                                style = Type.small, color = c.muted,
                            )
                        }
                        Text(
                            "Remove", style = Type.captionBold, color = c.muted,
                            modifier = Modifier.heightIn(min = 44.dp).clip(Radius.sm).clickable(role = Role.Button) { onDeleteChallenge(challenge.id) }.padding(horizontal = 10.dp, vertical = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(progress.daysSoFar.toFloat() / progress.totalDays, color = c.ink, track = c.track)
                    Text(
                        when {
                            progress.notStarted -> "Starts ${UiFormat.dayMonth(java.time.LocalDate.ofEpochDay(challenge.startEpochDay))}"
                            progress.kept -> "Kept. No spending for all ${progress.totalDays} days."
                            progress.finished -> "Done. ${progress.cleanDays} of ${progress.totalDays} days with no spending."
                            progress.spendDays.isEmpty() -> "Day ${progress.daysSoFar} of ${progress.totalDays}, no spending so far."
                            else -> "Day ${progress.daysSoFar} of ${progress.totalDays}. ${progress.cleanDays} days with no spending. Spent on ${UiFormat.dayMonth(progress.spendDays.last())}."
                        },
                        style = Type.caption, color = c.ink, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            SectionLabel("New challenge")
            Card(padding = PaddingValues(16.dp)) {
                LabeledField("Name", name, "No eating out", { name = it.take(30) }, KeyboardType.Text, capitalize = true)
                Spacer(Modifier.height(12.dp))
                Text("What counts", style = Type.captionBold, color = c.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Any spending", scope == null, { scope = null })
                    categories.forEach { Chip(it.name, scope == it.id, { scope = it.id }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("For how long", style = Type.captionBold, color = c.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 7, 14, 30).forEach { Chip("$it days", days == it, { days = it }) }
                }
                Spacer(Modifier.height(14.dp))
                LButton("Start today", enabled = name.isNotBlank(), kind = ButtonKind.Lime, height = 48.dp, onClick = {
                    onAddChallenge(ChallengeEntity(name = name.trim(), categoryId = scope, startEpochDay = today.toEpochDay(), endEpochDay = today.toEpochDay() + days - 1))
                    name = ""
                })
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, trailing: @Composable () -> Unit) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.bodyStrong, color = c.ink)
            Text(value, style = Type.small, color = c.muted)
        }
        trailing()
    }
}
