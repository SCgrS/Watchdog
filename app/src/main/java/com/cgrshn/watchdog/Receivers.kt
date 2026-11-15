package com.cgrshn.watchdog

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Scheduled check. Not exported, only the app's own alarm reaches it. */
class CheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CHECK) Checker.check(context, Trigger.ALARM)
    }

    companion object {
        const val CHECK = "com.cgrshn.watchdog.CHECK"
    }
}

/**
 * Sets the schedule again when the phone starts, the app is updated or the clock is changed (alarms are cleared) and
 * silently puts back the notification a restart removed. A check whose time has passed runs right away. Only the system
 * can send these broadcasts. A request with any other action is ignored.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED ->
                Checker.check(context, Trigger.BOOT)
        }
    }
}

/** Short job the system runs when a watched setting changes: When a setting goes off, the notification goes right away. */
class SettingChangeJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Checker.check(this, Trigger.SOFT)
        // The content-triggered job runs once. A new one is set up after this one ends.
        val app = applicationContext
        Handler(Looper.getMainLooper()).post { Scheduler.watchSettings(app) }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
