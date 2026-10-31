package app.leftover.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import app.leftover.R
import app.leftover.domain.Money
import app.leftover.ui.Routes

/**
 * One-tap logging for the spends you repeat. Buttons are built from your own history (see [QuickPresets]), so a new
 * user just sees "+ Other". After a tap the widget offers a real Undo for ten minutes.
 */
class QuickLogWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val logged = LastLogged.fresh(context)
        provideContent { QuickLogContent(model, logged) }
    }
}

class QuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogWidget()
}

@Composable
private fun QuickLogContent(model: WidgetModel, logged: LastLogged.Entry?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val root = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(12.dp)

    if (!model.ready) {
        Box(root.clickable(actionStartActivity(Widgets.open(context, Routes.HOME))), contentAlignment = Alignment.Center) {
            Text("Open Leftover to set up", style = TextStyle(color = WidgetColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        return
    }

    // Each button needs about 44 dp plus spacing; the title takes ~24 dp. Always keep room for "+ Other".
    val slots = (((size.height - 24.dp - 24.dp) / 50.dp).toInt()).coerceAtLeast(1)
    val presets = model.presets.take((slots - 1).coerceAtLeast(0))

    Column(root) {
        Text("Quick log", style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        if (logged != null) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("Logged ${Money.format(logged.amount)}", style = TextStyle(color = WidgetColors.positive, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text(logged.label, style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp))
                }
                Spacer(GlanceModifier.width(8.dp))
                Pill("Undo", R.drawable.widget_btn_quiet, WidgetColors.ink, actionRunCallback<UndoLoggedAction>(), GlanceModifier.width(72.dp))
            }
            Spacer(GlanceModifier.height(6.dp))
        }
        presets.forEach { preset ->
            val action = actionRunCallback<LogPresetAction>(
                actionParametersOf(
                    WidgetKeys.amount to preset.amount, WidgetKeys.account to preset.accountId, WidgetKeys.category to (preset.categoryId ?: -1L),
                    WidgetKeys.label to preset.label, WidgetKeys.merchant to preset.merchant,
                ),
            )
            Pill("${preset.label} ${Money.format(preset.amount)}", R.drawable.widget_btn_primary, WidgetColors.onPrimary, action)
            Spacer(GlanceModifier.height(6.dp))
        }
        Pill("+ Other", R.drawable.widget_btn_quiet, WidgetColors.ink, actionStartActivity(Widgets.add(context, "Expense", model.locked)))
    }
}
