package app.leftover

import android.app.Application
import app.leftover.data.LeftoverRepository
import app.leftover.data.SettingsRepository
import app.leftover.data.db.LeftoverDatabase
import app.leftover.domain.ReminderPrefs
import app.leftover.notify.ReminderScheduler
import app.leftover.sms.SmsIngestor
import app.leftover.sms.SmsNotifier
import app.leftover.widget.WidgetModel
import app.leftover.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual dependency container: small enough that a DI framework would only add ceremony. */
class LeftoverApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val database by lazy { LeftoverDatabase.create(this) }
    val repository by lazy { LeftoverRepository(database, settings) }
    val ingestor by lazy { SmsIngestor(repository, SmsNotifier(this)) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Reminders run on a daily schedule while any of them is switched on.
        appScope.launch {
            settings.settings.map { ReminderPrefs(it.billReminders, it.paydayReminder, it.budgetAlerts, it.weeklyCheckIn) }.distinctUntilChanged()
                .collect { ReminderScheduler.sync(this@LeftoverApp, it) }
        }
        // Keep the home-screen widgets in step with the data: redraw whenever what they show changes.
        appScope.launch {
            repository.snapshot.map { WidgetModel.from(it) }.distinctUntilChanged().debounce(400).collect { Widgets.refreshAll(this@LeftoverApp) }
        }
    }
}
