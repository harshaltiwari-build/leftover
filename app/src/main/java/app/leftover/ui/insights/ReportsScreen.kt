package app.leftover.ui.insights

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.leftover.data.Snapshot
import app.leftover.data.db.TxFeeling
import app.leftover.domain.DateSpan
import app.leftover.domain.Money
import app.leftover.domain.RangePreset
import app.leftover.domain.RangeReport
import app.leftover.domain.ReportCsv
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.Chip
import app.leftover.ui.components.DateRangeDialog
import app.leftover.ui.components.Divider
import app.leftover.ui.components.LButton
import app.leftover.ui.components.Segmented
import app.leftover.ui.components.ToggleRow
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reports over any period: presets from "Today" to "This year", or any dates you pick. Every chart can be read as a
 * table instead, and the whole report exports as a PDF or CSV through the share sheet.
 */
@Composable
fun ReportsScreen(snapshot: Snapshot, onBack: () -> Unit) {
    val c = Leftover.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden = snapshot.settings.hideBalances
    var preset by rememberSaveable { mutableStateOf(RangePreset.THIS_CYCLE) }
    var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    var asTable by rememberSaveable { mutableStateOf(false) }
    var includeNotes by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val span = if (preset == RangePreset.CUSTOM && customStart != null && customEnd != null) DateSpan(LocalDate.ofEpochDay(customStart!!), LocalDate.ofEpochDay(customEnd!!))
    else preset.span(snapshot.today, snapshot.cycle)
    val report = remember(snapshot.ledger, span) { RangeReport.build(snapshot.ledger, span) }

    fun share(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, title))
    }

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text("Reports", style = Type.h1, color = c.ink)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RangePreset.entries) { p ->
                Chip(if (p == RangePreset.CUSTOM && preset == p) "Custom ✎" else p.label, preset == p, {
                    if (p == RangePreset.CUSTOM) picking = true else preset = p
                })
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(span.label(), style = Type.title, color = c.ink)
                Text("${span.days} ${if (span.days == 1) "day" else "days"} · ${report.entries.size} ${if (report.entries.size == 1) "spend" else "spends"}" + comparison(report, hidden), style = Type.caption, color = c.muted)
            }

            SummaryGrid(report, hidden)

            Segmented(listOf("Charts", "Tables"), if (asTable) "Tables" else "Charts", { asTable = it == "Tables" })

            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                Text("Money in and out", style = Type.title, color = c.ink)
                Spacer(Modifier.height(12.dp))
                if (asTable) InOutTable(report, hidden) else InOutChart(report, hidden)
            }

            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                Text("Where it went", style = Type.title, color = c.ink)
                Spacer(Modifier.height(12.dp))
                when {
                    report.categories.isEmpty() -> Text("No spending in this period.", style = Type.caption, color = c.muted)
                    asTable -> CategoryTable(report, hidden)
                    else -> CategoryDonut(report, hidden)
                }
            }

            FeelingCard(report, hidden)

            if (report.merchants.isNotEmpty()) {
                Card(shape = Radius.xl, padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                    Text("Top places", style = Type.title, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    report.merchants.forEachIndexed { i, m ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = Type.captionBold, color = c.muted, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = Type.bodyStrong, color = c.ink, maxLines = 1)
                                Text("${m.count} ${if (m.count == 1) "time" else "times"}", style = Type.small, color = c.muted)
                            }
                            Text(Money.display(m.spent, hidden), style = Type.bodyExtra, color = c.ink)
                        }
                    }
                }
            }

            Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
                Text("Export this report", style = Type.title, color = c.ink)
                Text(
                    "A4 PDF with totals, charts as tables and every spend, or a CSV for a spreadsheet. Files are made on this phone and only go where you send them." +
                        if (hidden) " Exports show amounts even while Hide balances is on." else "",
                    style = Type.caption, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                ToggleRow("Include notes in the PDF", "Off by default: notes are often the most personal part.", includeNotes, { includeNotes = it })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LButton(if (busy) "Making…" else "PDF", {
                        if (!busy) scope.launch {
                            busy = true
                            val file = withContext(Dispatchers.IO) { ReportPdf.write(context, snapshot.ledger, report, includeNotes) }
                            busy = false
                            share(file, "application/pdf", "Share report")
                        }
                    }, modifier = Modifier.weight(1f), kind = ButtonKind.Lime, height = 48.dp)
                    LButton("CSV", {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { File(context.cacheDir, "leftover-export.csv").apply { writeText(ReportCsv.build(snapshot.ledger, span)) } }
                            share(file, "text/csv", "Export transactions")
                        }
                    }, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp)
                }
            }
        }
    }

    if (picking) {
        DateRangeDialog(
            "Pick the days", "Show report",
            customStart?.let(LocalDate::ofEpochDay) ?: span.start, customEnd?.let(LocalDate::ofEpochDay) ?: span.end,
            onPicked = { start, end -> customStart = start.toEpochDay(); customEnd = end.toEpochDay(); preset = RangePreset.CUSTOM },
            onDismiss = { picking = false },
        )
    }
}


private fun comparison(r: RangeReport, hidden: Boolean): String {
    if (r.previousSpent <= 0 || hidden) return ""
    val diff = r.spent - r.previousSpent
    val pct = (kotlin.math.abs(diff) * 100 / r.previousSpent)
    return " · $pct% ${if (diff <= 0) "less" else "more"} than the ${r.span.days} days before"
}

@Composable
private fun SummaryGrid(r: RangeReport, hidden: Boolean) {
    val tiles = listOf(
        "Spent" to Money.display(r.spent, hidden),
        "Money in" to Money.display(r.income, hidden),
        "Net" to Money.display(r.net, hidden),
        "Kept" to (r.savingsRate?.let { "$it%" } ?: "—"),
        "Per day" to Money.display(r.perDay, hidden),
        "Of income spent" to (r.spendRate?.let { "$it%" } ?: "—"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) -> Tile(label, value, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    val c = Leftover.colors
    Column(modifier.clip(Radius.md).background(c.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = Type.smallBold, color = c.muted)
        Text(value, style = Type.h2, color = c.ink, maxLines = 1)
    }
}

@Composable
private fun InOutChart(r: RangeReport, hidden: Boolean) {
    val c = Leftover.colors
    val max = r.buckets.maxOfOrNull { maxOf(it.income, it.spent) }?.coerceAtLeast(1L)?.toFloat() ?: 1f
    val description = r.buckets.joinToString("; ") { "${it.label}: in ${Money.display(it.income, hidden)}, out ${Money.display(it.spent, hidden)}" }
    Column(Modifier.semantics { contentDescription = "Money in and out. $description" }) {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            r.buckets.forEach { b ->
                Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f).fillMaxHeight((b.income / max).coerceAtLeast(0.01f)).clip(Radius.xs).background(c.lime))
                    Box(Modifier.weight(1f).fillMaxHeight((b.spent / max).coerceAtLeast(0.01f)).clip(Radius.xs).background(c.ink))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Label every bar when there are few, otherwise the first, the middle and the last.
        val labelled = if (r.buckets.size <= 8) r.buckets.indices.toSet() else setOf(0, r.buckets.size / 2, r.buckets.lastIndex)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            r.buckets.forEachIndexed { i, b -> Text(if (i in labelled) b.label else "", style = Type.small, color = c.muted, maxLines = 1, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(c.lime, "In ${Money.display(r.income, hidden)}")
            Legend(c.ink, "Out ${Money.display(r.spent, hidden)}")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    val c = Leftover.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(Radius.pill).background(color))
        Text(label, style = Type.small, color = c.ink)
    }
}

@Composable
private fun InOutTable(r: RangeReport, hidden: Boolean) {
    val c = Leftover.colors
    Column {
        TableRow(listOf("Period", "In", "Out", "Net"), header = true)
        r.buckets.forEach { b ->
            Divider()
            TableRow(listOf(b.label, Money.display(b.income, hidden), Money.display(b.spent, hidden), Money.display(b.income - b.spent, hidden)))
        }
        Divider()
        TableRow(listOf("Total", Money.display(r.income, hidden), Money.display(r.spent, hidden), Money.display(r.net, hidden)), header = true)
        if (r.buckets.isEmpty()) Text("Nothing in this period.", style = Type.caption, color = c.muted)
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    val c = Leftover.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        cells.forEachIndexed { i, s ->
            Text(
                s, style = if (header) Type.smallBold else Type.caption, color = if (header) c.muted else c.ink, maxLines = 1,
                modifier = Modifier.weight(if (i == 0) 1.2f else 1f),
                textAlign = if (i == 0) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun CategoryDonut(r: RangeReport, hidden: Boolean) {
    val c = Leftover.colors
    val total = r.categories.sumOf { it.spent }.coerceAtLeast(1L).toFloat()
    val colors = r.categories.map { CategoryPalette.of(it.category.color, c.isDark).bar }
    val description = r.categories.joinToString("; ") { "${it.category.name} ${(it.spent * 100 / total.toLong())}%" }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(132.dp).semantics { contentDescription = "Spending by category: $description" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                var start = -90f
                r.categories.forEachIndexed { i, cat ->
                    val sweep = cat.spent / total * 360f
                    drawArc(colors[i], start, (sweep - 1.5f).coerceAtLeast(0.5f), useCenter = false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Money.display(r.spent, hidden), style = Type.bodyExtra, color = c.ink, maxLines = 1)
                Text("spent", style = Type.small, color = c.muted)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.categories.take(6).forEachIndexed { i, cat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(Radius.pill).background(colors[i]))
                    Text(cat.category.name, style = Type.small, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${(cat.spent * 100 / total.toLong())}%", style = Type.smallBold, color = c.ink)
                }
            }
            if (r.categories.size > 6) Text("+${r.categories.size - 6} more · see Tables", style = Type.small, color = c.muted)
        }
    }
}

@Composable
private fun CategoryTable(r: RangeReport, hidden: Boolean) {
    val total = r.categories.sumOf { it.spent }.coerceAtLeast(1L)
    Column {
        TableRow(listOf("Category", "Share", "Spent"), header = true)
        r.categories.forEach { cat ->
            Divider()
            TableRow(listOf(cat.category.name, "${cat.spent * 100 / total}%", Money.display(cat.spent, hidden)))
        }
    }
}

/** Needed, worth it or regret: what you said about each spend, added up. */
@Composable
private fun FeelingCard(r: RangeReport, hidden: Boolean) {
    val c = Leftover.colors
    val colors = mapOf(TxFeeling.NEEDED to c.ink, TxFeeling.WORTH_IT to c.lime, TxFeeling.REGRET to c.ember, null to c.track)
    val total = r.feelings.values.sum().coerceAtLeast(1L).toFloat()
    Card(shape = Radius.xl, padding = PaddingValues(18.dp)) {
        Text(
            when {
                r.marked == 0L -> "Was it worth it?"
                r.regret == 0L -> "No regrets in this period."
                hidden -> "Some of this period's spending was a regret."
                else -> "${Money.format(r.regret)} you'd rather not have spent."
            },
            style = Type.title, color = c.ink,
        )
        Spacer(Modifier.height(6.dp))
        if (r.marked == 0L) {
            Text("Open any spend and mark it Needed, Worth it or Regret. Your regrets add up here, so you can see where the money leaks.", style = Type.caption, color = c.muted)
            return@Card
        }
        Text("Based on the spends you've marked. Unmarked spends are grey.", style = Type.small, color = c.muted)
        Spacer(Modifier.height(12.dp))
        val order = listOf(TxFeeling.NEEDED, TxFeeling.WORTH_IT, TxFeeling.REGRET, null)
        Row(Modifier.fillMaxWidth().height(14.dp).clip(Radius.pill).semantics {
            contentDescription = order.mapNotNull { f -> r.feelings[f]?.let { "${f?.label ?: "Not marked"} ${(it * 100 / total.toLong())}%" } }.joinToString("; ")
        }) {
            order.forEach { f -> r.feelings[f]?.takeIf { it > 0 }?.let { Box(Modifier.weight(it / total).fillMaxHeight().background(colors.getValue(f))) } }
        }
        Spacer(Modifier.height(12.dp))
        order.forEach { f ->
            val amount = r.feelings[f] ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(Radius.pill).background(colors.getValue(f)))
                Text(f?.label ?: "Not marked", style = Type.caption, color = c.ink, modifier = Modifier.weight(1f))
                Text(Money.display(amount, hidden), style = Type.captionBold, color = c.ink)
            }
        }
    }
}
