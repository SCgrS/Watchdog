package com.cgrshn.watchdog

import android.content.Context

/**
 * Reads the settings, makes the decision and arranges the notification and the schedule accordingly. All calls come from
 * the app's main thread (receivers, job, screen), so they run one after another.
 */
object Checker {
    fun check(context: Context, trigger: Trigger): Snapshot {
        val app = context.applicationContext
        val store = Store(app)
        val snapshot = SettingsReader.read(app)
        val now = System.currentTimeMillis()
        val effective = Decision.effectiveTrigger(trigger, store.nextCheck, now)
        when (snapshot.autoBlocker) {
            State.UNKNOWN -> if (snapshot.debuggingOn) store.autoBlockerPending = true
            else -> store.autoBlockerPending = false
        }
        val outcome = Decision.decide(snapshot, Notifier.visibleType(app), effective, store.autoBlockerPending)
        val shown = Notifier.apply(app, snapshot, outcome)
        if (shown && outcome.type == ReminderType.AUTO_BLOCKER_REMINDER) store.autoBlockerPending = false
        store.lastCheck = now
        Scheduler.schedule(app, Decision.nextCheck(effective, store.nextCheck, now, store.checkMs))
        Scheduler.watchSettings(app)
        return snapshot
    }

    /** The check interval changed: The next check only moves earlier (at most now + new interval), never later. */
    fun changeInterval(context: Context, minutes: Int) {
        val app = context.applicationContext
        Store(app).checkMinutes = minutes
        Scheduler.reschedule(app)
    }
}
