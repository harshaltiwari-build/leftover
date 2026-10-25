package app.leftover.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import app.leftover.LeftoverApp
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import app.leftover.domain.Money

internal object WidgetKeys {
    val amount = ActionParameters.Key<Long>("amount")
    val category = ActionParameters.Key<Long>("category")
    val account = ActionParameters.Key<Long>("account")
    val label = ActionParameters.Key<String>("label")
    val merchant = ActionParameters.Key<String>("merchant")
}

/** Logs a repeat spend with one tap and remembers it so it can be undone. */
class LogPresetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as LeftoverApp
        val amount = parameters[WidgetKeys.amount] ?: return
        val account = parameters[WidgetKeys.account] ?: return
        val category = parameters[WidgetKeys.category]?.takeIf { it >= 0 }
        val label = parameters[WidgetKeys.label].orEmpty()
        val now = System.currentTimeMillis()
        val id = app.repository.addTransaction(
            TransactionEntity(
                type = TxType.EXPENSE, amount = amount, accountId = account, categoryId = category,
                merchant = parameters[WidgetKeys.merchant].orEmpty(), occurredAt = now, source = TxSource.WIDGET,
            ),
        )
        LastLogged.save(context, LastLogged.Entry(id, label, amount, now))
        Widgets.refreshAll(context)
        Toast.makeText(context, "Logged ${Money.format(amount)} · $label", Toast.LENGTH_SHORT).show()
    }
}

/** Deletes the entry that was just logged from the widget. */
class UndoLoggedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as LeftoverApp
        LastLogged.fresh(context)?.let { app.repository.deleteTransaction(it.id) }
        LastLogged.clear(context)
        Widgets.refreshAll(context)
    }
}
