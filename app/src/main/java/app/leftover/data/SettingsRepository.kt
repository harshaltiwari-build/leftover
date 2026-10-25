package app.leftover.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    val paydayDay: Int = 1,
    val incomeType: String = "Salary",
    val usualIncome: Long = 0,
    val hideBalances: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLock: Boolean = false,
    /** Keeps the app out of the recent-apps preview. On Android 12 and older this also blocks screenshots. */
    val hideInSwitcher: Boolean = false,
    /** Reminders are all off until switched on, and each one asks for the notification permission when it is. */
    val billReminders: Boolean = false,
    val paydayReminder: Boolean = false,
    val budgetAlerts: Boolean = false,
    val weeklyCheckIn: Boolean = false,
    /** Master switch for reading bank texts. Off until the user turns it on. */
    val smsTracking: Boolean = false,
    /** Backup protection: the salt and the key (wrapped by Android Keystore). Null when backups are not passphrase-protected. */
    /** Plan on the leanest recent month and treat the rest as a buffer. */
    val irregularIncome: Boolean = false,
    /** Language the voice recogniser listens for: "en-IN" or "hi-IN". */
    val voiceLanguage: String = "en-IN",
    /** Lets other apps hand entries to Leftover through a link. They only ever land in the review queue. */
    val acceptLinks: Boolean = false,
    /** Reads payment-app notifications from the apps in [notificationApps]. Needs Notification access in Android settings. */
    val captureNotifications: Boolean = false,
    /** Save a payment without asking when its account and merchant are known. Off means every payment waits for review. */
    val smsAutoSave: Boolean = true,
    val smsNotify: Boolean = true,
    /** Counts only, never text: how many one-time-password and promotional texts were set aside. */
    val smsSkippedOtp: Int = 0,
    val smsSkippedPromo: Int = 0,
    /** Subscriptions the user said they still use, so Leftover stops asking about them. */
    val keptSubscriptions: Set<String> = emptySet(),
    val notificationApps: Set<String> = emptySet(),
    val backupSalt: String? = null,
    val backupWrappedKey: String? = null,
    val autoBackup: Boolean = false,
    val backupFolder: String? = null,
    val backupEveryDays: Int = 7,
    val lastBackupAt: Long = 0,
    val lastBackupError: String? = null,
) {
    val backupProtected: Boolean get() = backupSalt != null && backupWrappedKey != null
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val paydayDay = intPreferencesKey("payday_day")
        val incomeType = stringPreferencesKey("income_type")
        val usualIncome = longPreferencesKey("usual_income")
        val hideBalances = booleanPreferencesKey("hide_balances")
        val themeMode = stringPreferencesKey("theme_mode")
        val appLock = booleanPreferencesKey("app_lock")
        val hideInSwitcher = booleanPreferencesKey("hide_in_switcher")
        val billReminders = booleanPreferencesKey("bill_reminders")
        val paydayReminder = booleanPreferencesKey("payday_reminder")
        val budgetAlerts = booleanPreferencesKey("budget_alerts")
        val weeklyCheckIn = booleanPreferencesKey("weekly_check_in")
        val reminderSent = stringSetPreferencesKey("reminder_sent")
        val smsTracking = booleanPreferencesKey("sms_tracking")
        val acceptLinks = booleanPreferencesKey("accept_links")
        val captureNotifications = booleanPreferencesKey("capture_notifications")
        val smsAutoSave = booleanPreferencesKey("sms_auto_save")
        val smsNotify = booleanPreferencesKey("sms_notify")
        val smsSkippedOtp = intPreferencesKey("sms_skipped_otp")
        val smsSkippedPromo = intPreferencesKey("sms_skipped_promo")
        val keptSubscriptions = stringSetPreferencesKey("kept_subscriptions")
        val notificationApps = stringSetPreferencesKey("notification_apps")
        val irregularIncome = booleanPreferencesKey("irregular_income")
        val voiceLanguage = stringPreferencesKey("voice_language")
        val backupSalt = stringPreferencesKey("backup_salt")
        val backupWrappedKey = stringPreferencesKey("backup_wrapped_key")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val backupFolder = stringPreferencesKey("backup_folder")
        val backupEveryDays = intPreferencesKey("backup_every_days")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val lastBackupError = stringPreferencesKey("last_backup_error")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        onboarded = this[Keys.onboarded] ?: false,
        name = this[Keys.name] ?: "",
        paydayDay = this[Keys.paydayDay] ?: 1,
        incomeType = this[Keys.incomeType] ?: "Salary",
        usualIncome = this[Keys.usualIncome] ?: 0,
        hideBalances = this[Keys.hideBalances] ?: false,
        themeMode = this[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        appLock = this[Keys.appLock] ?: false,
        hideInSwitcher = this[Keys.hideInSwitcher] ?: false,
        billReminders = this[Keys.billReminders] ?: false,
        paydayReminder = this[Keys.paydayReminder] ?: false,
        budgetAlerts = this[Keys.budgetAlerts] ?: false,
        weeklyCheckIn = this[Keys.weeklyCheckIn] ?: false,
        smsTracking = this[Keys.smsTracking] ?: false,
        acceptLinks = this[Keys.acceptLinks] ?: false,
        captureNotifications = this[Keys.captureNotifications] ?: false,
        smsAutoSave = this[Keys.smsAutoSave] ?: true,
        smsNotify = this[Keys.smsNotify] ?: true,
        smsSkippedOtp = this[Keys.smsSkippedOtp] ?: 0,
        smsSkippedPromo = this[Keys.smsSkippedPromo] ?: 0,
        keptSubscriptions = this[Keys.keptSubscriptions] ?: emptySet(),
        notificationApps = this[Keys.notificationApps] ?: emptySet(),
        irregularIncome = this[Keys.irregularIncome] ?: false,
        voiceLanguage = this[Keys.voiceLanguage] ?: "en-IN",
        backupSalt = this[Keys.backupSalt],
        backupWrappedKey = this[Keys.backupWrappedKey],
        autoBackup = this[Keys.autoBackup] ?: false,
        backupFolder = this[Keys.backupFolder],
        backupEveryDays = this[Keys.backupEveryDays] ?: 7,
        lastBackupAt = this[Keys.lastBackupAt] ?: 0L,
        lastBackupError = this[Keys.lastBackupError],
    )

    suspend fun completeOnboarding(paydayDay: Int, incomeType: String, usualIncome: Long) {
        context.dataStore.edit {
            it[Keys.paydayDay] = paydayDay
            it[Keys.incomeType] = incomeType
            it[Keys.usualIncome] = usualIncome
            it[Keys.onboarded] = true
        }
    }

    /** Applies the settings from a backup and marks setup as done, so the app opens straight to Home. */
    suspend fun restore(backup: BackupSettings) {
        context.dataStore.edit {
            it[Keys.name] = backup.name
            it[Keys.paydayDay] = backup.paydayDay
            it[Keys.incomeType] = backup.incomeType
            it[Keys.usualIncome] = backup.usualIncome
            it[Keys.hideBalances] = backup.hideBalances
            it[Keys.themeMode] = backup.themeMode.name
            it[Keys.billReminders] = backup.billReminders
            it[Keys.irregularIncome] = backup.irregularIncome
            it[Keys.voiceLanguage] = backup.voiceLanguage
            it[Keys.onboarded] = true
        }
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }

    suspend fun setHideBalances(value: Boolean) = context.dataStore.edit { it[Keys.hideBalances] = value }
    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = value.name }
    suspend fun setAppLock(value: Boolean) = context.dataStore.edit { it[Keys.appLock] = value }
    suspend fun setHideInSwitcher(value: Boolean) = context.dataStore.edit { it[Keys.hideInSwitcher] = value }
    suspend fun setBillReminders(value: Boolean) = context.dataStore.edit { it[Keys.billReminders] = value }
    suspend fun setPaydayReminder(value: Boolean) = context.dataStore.edit { it[Keys.paydayReminder] = value }
    suspend fun setBudgetAlerts(value: Boolean) = context.dataStore.edit { it[Keys.budgetAlerts] = value }
    suspend fun setWeeklyCheckIn(value: Boolean) = context.dataStore.edit { it[Keys.weeklyCheckIn] = value }
    suspend fun reminderSent(): Set<String> = context.dataStore.data.first()[Keys.reminderSent] ?: emptySet()
    suspend fun setReminderSent(keys: Set<String>) = context.dataStore.edit { it[Keys.reminderSent] = keys }
    suspend fun setBackupKey(salt: String?, wrappedKey: String?) = context.dataStore.edit {
        if (salt == null || wrappedKey == null) { it.remove(Keys.backupSalt); it.remove(Keys.backupWrappedKey) } else { it[Keys.backupSalt] = salt; it[Keys.backupWrappedKey] = wrappedKey }
    }
    suspend fun setAutoBackup(on: Boolean, folder: String?, everyDays: Int) = context.dataStore.edit {
        it[Keys.autoBackup] = on
        if (folder == null) it.remove(Keys.backupFolder) else it[Keys.backupFolder] = folder
        it[Keys.backupEveryDays] = everyDays
    }
    suspend fun recordBackup(at: Long?, error: String?) = context.dataStore.edit {
        if (at != null) it[Keys.lastBackupAt] = at
        if (error == null) it.remove(Keys.lastBackupError) else it[Keys.lastBackupError] = error
    }
    suspend fun setIrregularIncome(value: Boolean) = context.dataStore.edit { it[Keys.irregularIncome] = value }
    suspend fun setVoiceLanguage(value: String) = context.dataStore.edit { it[Keys.voiceLanguage] = value }
    suspend fun setIncome(incomeType: String, usualIncome: Long) = context.dataStore.edit { it[Keys.incomeType] = incomeType; it[Keys.usualIncome] = usualIncome }
    suspend fun setAcceptLinks(value: Boolean) = context.dataStore.edit { it[Keys.acceptLinks] = value }
    suspend fun keepSubscription(id: Long) = context.dataStore.edit { it[Keys.keptSubscriptions] = (it[Keys.keptSubscriptions] ?: emptySet()) + id.toString() }
    suspend fun setSmsAutoSave(value: Boolean) = context.dataStore.edit { it[Keys.smsAutoSave] = value }
    suspend fun setSmsNotify(value: Boolean) = context.dataStore.edit { it[Keys.smsNotify] = value }
    suspend fun addSmsSkipped(otp: Int, promo: Int) = context.dataStore.edit {
        it[Keys.smsSkippedOtp] = (it[Keys.smsSkippedOtp] ?: 0) + otp
        it[Keys.smsSkippedPromo] = (it[Keys.smsSkippedPromo] ?: 0) + promo
    }
    suspend fun setCaptureNotifications(value: Boolean) = context.dataStore.edit { it[Keys.captureNotifications] = value }
    suspend fun setNotificationApp(packageName: String, allowed: Boolean) = context.dataStore.edit {
        val current = it[Keys.notificationApps] ?: emptySet()
        it[Keys.notificationApps] = if (allowed) current + packageName else current - packageName
    }
    suspend fun setSmsTracking(value: Boolean) = context.dataStore.edit { it[Keys.smsTracking] = value }
    suspend fun setPaydayDay(value: Int) = context.dataStore.edit { it[Keys.paydayDay] = value }
    suspend fun setName(value: String) = context.dataStore.edit { it[Keys.name] = value }
}
