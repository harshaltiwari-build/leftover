package app.leftover.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.leftover.R
import app.leftover.domain.Money
import app.leftover.ui.Routes

/**
 * "Left to spend" on the home screen. One widget, three layouts chosen by size:
 * small (the number and an add button), medium (adds Expense / Income buttons), large (adds budgets and the last entry).
 * Amounts follow the in-app "Hide balances" setting.
 */
class LeftoverWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Small, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        provideContent { LeftContent(model) }
    }

    companion object {
        val Small = DpSize(110.dp, 110.dp)
        val Medium = DpSize(250.dp, 110.dp)
        val Large = DpSize(250.dp, 250.dp)
    }
}

class LeftoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LeftoverWidget()
}

@Composable
private fun LeftContent(model: WidgetModel) {
    val context = LocalContext.current
    val size = LocalSize.current
    val openHome = actionStartActivity(Widgets.open(context, Routes.HOME))
    val root = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(14.dp)

    if (!model.ready) {
        Box(root.clickable(openHome), contentAlignment = Alignment.Center) {
            Text("Open Leftover to set up", style = TextStyle(color = WidgetColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        return
    }

    val addExpense = actionStartActivity(Widgets.add(context, "Expense", model.locked))
    val addIncome = actionStartActivity(Widgets.add(context, "Income", model.locked))
    val wide = size.width >= 220.dp
    val tall = size.height >= 220.dp

    Column(root) {
        if (wide) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(openHome)) { Summary(model) }
                Spacer(GlanceModifier.width(10.dp))
                if (!tall) {
                    Column(GlanceModifier.width(112.dp)) {
                        Pill("− Expense", R.drawable.widget_btn_primary, WidgetColors.onPrimary, addExpense)
                        Spacer(GlanceModifier.height(8.dp))
                        Pill("+ Income", R.drawable.widget_btn_lime, WidgetColors.onLime, addIncome)
                    }
                }
            }
        } else {
            Column(GlanceModifier.defaultWeight().clickable(openHome)) { Summary(model) }
            Pill("+ Add", R.drawable.widget_btn_primary, WidgetColors.onPrimary, addExpense)
        }

        if (tall) {
            Spacer(GlanceModifier.height(10.dp))
            model.budgets.forEach { line ->
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(line.name, style = TextStyle(color = WidgetColors.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                    val over = line.spent > line.limit
                    Text(
                        if (model.hidden) Money.HIDDEN else if (over) "${Money.format(line.spent - line.limit)} over" else "${Money.format(line.limit - line.spent)} left",
                        style = TextStyle(color = if (over) WidgetColors.ember else WidgetColors.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.height(3.dp))
                LinearProgressIndicator(
                    progress = (line.spent.toFloat() / line.limit).coerceIn(0f, 1f), modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = if (line.spent > line.limit) WidgetColors.ember else WidgetColors.ink, backgroundColor = WidgetColors.track,
                )
                Spacer(GlanceModifier.height(8.dp))
            }
            model.last?.let { last ->
                Row(GlanceModifier.fillMaxWidth()) {
                    Text("Last: ${last.title}", style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
                    Text(Money.display(last.amount, model.hidden, signed = true), style = TextStyle(color = WidgetColors.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                Pill("− Expense", R.drawable.widget_btn_primary, WidgetColors.onPrimary, addExpense, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                Pill("+ Income", R.drawable.widget_btn_lime, WidgetColors.onLime, addIncome, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun Summary(model: WidgetModel) {
    Text("Left to spend", style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    Text(Money.display(model.left, model.hidden), style = TextStyle(color = WidgetColors.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold))
    Text(
        if (model.hidden) "Hidden · tap to open" else "${Money.format(model.perDay)}/day · ${model.daysToPayday} ${if (model.daysToPayday == 1) "day" else "days"}",
        style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp),
    )
    if (!model.hidden && model.todaySpent > 0) {
        Text("Today ${Money.format(model.todaySpent)} spent", style = TextStyle(color = WidgetColors.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun Pill(label: String, background: Int, textColor: ColorProvider, action: Action, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    Box(
        modifier.height(44.dp).background(ImageProvider(background)).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}
