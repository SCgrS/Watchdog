package com.cgrshn.watchdog

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Nothing runs in the background all the time. Two system mechanisms are used:
 *
 * - Alarm: When the chosen interval is over, [CheckReceiver] wakes up. No exact alarm permission is requested. A windowed
 *   alarm is late by at most 10 minutes while the phone is in use. If the phone is asleep, it runs at the first wake-up.
 * - Content-triggered job: When a watched setting changes, the system runs [SettingChangeJob]. This way the notification
 *   goes away as soon as the setting is turned off. The job runs once and is set up again after every run.
 */
object Scheduler {
    private const val JOB_ID = 1
    private const val WINDOW_MS = 10 * 60_000L

    fun schedule(context: Context, time: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, time, WINDOW_MS, checkIntent(context))
        Store(context).nextCheck = time
    }

    /**
     * Sets the stored check time again (restart, update and force stop clear alarms).
     *
     * The time is never pushed later: Another app cannot postpone the check by sending a fake change signal for a setting
     * address. When the clock is set back, the stored time does not exceed now + interval. Without a stored time (first
     * start) the first check is one interval later. The rule is in [Decision.rescheduleTime] and covered by unit tests.
     */
    fun reschedule(context: Context) {
        val store = Store(context)
        schedule(context, Decision.rescheduleTime(store.nextCheck, System.currentTimeMillis(), store.checkMs))
    }

    /**
     * If the job is already waiting and watches the same addresses, it is left alone. If the addresses differ (the
     * watched settings changed in an update and the old job is still there), it is set up again with the same ID and
     * replaces the old one.
     */
    fun watchSettings(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java)
        val pending = jobs.getPendingJob(JOB_ID)
        val watched = pending?.triggerContentUris?.map { it.uri }?.toSet()
        if (pending != null && watched == SettingsReader.uris.toSet()) return
        val builder = JobInfo.Builder(JOB_ID, ComponentName(context, SettingChangeJob::class.java))
        for (uri in SettingsReader.uris) {
            builder.addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
        }
        builder.setTriggerContentUpdateDelay(500L)
        builder.setTriggerContentMaxDelay(3_000L)
        try {
            jobs.schedule(builder.build())
        } catch (_: RuntimeException) {
            // If the job cannot be set up, the scheduled check still runs. The notification only goes away a little later.
        }
    }

    private fun checkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CheckReceiver::class.java).setAction(CheckReceiver.CHECK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
