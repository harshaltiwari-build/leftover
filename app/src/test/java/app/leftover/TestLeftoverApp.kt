package app.leftover

import android.util.Log
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

/**
 * The app as Robolectric runs it. Phones start WorkManager through androidx.startup, which Robolectric skips, so it's
 * started in test mode here before the app schedules its reminders. Set in robolectric.properties; never shipped.
 */
class TestLeftoverApp : LeftoverApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this, Configuration.Builder().setMinimumLoggingLevel(Log.WARN).setExecutor(SynchronousExecutor()).build())
        super.onCreate()
    }
}
