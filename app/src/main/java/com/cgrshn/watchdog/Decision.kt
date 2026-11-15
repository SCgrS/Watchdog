package com.cgrshn.watchdog

/** The state read for one setting. */
enum class State {
    ON,
    OFF,

    /** The setting exists on this phone, but Android does not let apps read it. */
    UNKNOWN,

    /** The setting or feature does not exist on this phone. */
    ABSENT,
}

/**
 * A snapshot of the watched settings. Developer options itself is not watched: Leaving it on is not a risk by itself, the
 * reminder is about the debugging settings.
 */
data class Snapshot(
    val usb: State,
    val wireless: State,
    val autoBlocker: State,
) {
    /** USB or wireless debugging is on. */
    val debuggingOn: Boolean
        get() = usb == State.ON || wireless == State.ON
}

/** What started a check. */
enum class Trigger {
    /** The chosen interval is over: If a setting is on, the user is alerted (sound). */
    ALARM,

    /**
     * The phone started, the app was updated or the clock changed: A restart clears notifications. A notification that
     * should be visible is put back silently, without sound.
     */
    BOOT,

    /** A watched setting changed or the app was opened: No new alert, the visible notification is updated. */
    SOFT,

    /**
     * The user tapped Check on the screen: A notification that should be visible is shown silently (the user is already
     * looking at the screen). The next check is set one interval later.
     */
    MANUAL,
}

/** The content of the app's single notification. */
enum class ReminderType {
    /** USB or wireless debugging is on. */
    DEBUGGING,

    /** Auto Blocker was read as off (on phones that let apps read it). */
    AUTO_BLOCKER_OFF,

    /**
     * Debugging settings that were seen on are now off, and Auto Blocker cannot be read: A one-time "Turn on Auto Blocker
     * now" reminder. It appears silently as soon as the settings go off, closes on tap or swipe and never sounds again.
     */
    AUTO_BLOCKER_REMINDER,
}

/** How the notification is delivered. */
enum class Delivery {
    /** Show and alert (sound, vibration). If the notification is already visible, alert again. */
    ALERT,

    /** If the notification is visible, refresh its content silently. If not, do not show it. */
    UPDATE_SILENTLY,

    /** Show silently whether or not it is visible (no sound, no vibration). */
    SHOW_SILENTLY,

    /** Leave the visible notification as it is. */
    LEAVE,

    REMOVE,
}

data class Outcome(val type: ReminderType?, val delivery: Delivery)

/**
 * All of the app's decision logic. It does not depend on Android and is covered by unit tests.
 *
 * The app checks at the chosen interval. If debugging settings are on, it shows a notification, which goes away when they
 * are turned off. No new alert appears while the user changes settings or opens the app.
 */
object Decision {
    /**
     * [autoBlockerPending]: Debugging settings were seen on while Auto Blocker could not be read. When the settings go
     * off, Auto Blocker is reminded once.
     */
    fun decide(snapshot: Snapshot, visible: ReminderType?, trigger: Trigger, autoBlockerPending: Boolean = false): Outcome {
        val type = when {
            snapshot.debuggingOn -> ReminderType.DEBUGGING
            snapshot.autoBlocker == State.OFF -> ReminderType.AUTO_BLOCKER_OFF
            snapshot.autoBlocker == State.UNKNOWN && (
                autoBlockerPending ||
                    visible == ReminderType.DEBUGGING ||
                    visible == ReminderType.AUTO_BLOCKER_REMINDER
                ) -> ReminderType.AUTO_BLOCKER_REMINDER
            else -> null
        }
        val delivery = when {
            type == null -> Delivery.REMOVE
            type == ReminderType.AUTO_BLOCKER_REMINDER && visible == type -> Delivery.LEAVE
            trigger == Trigger.ALARM -> Delivery.ALERT
            trigger == Trigger.BOOT || trigger == Trigger.MANUAL -> Delivery.SHOW_SILENTLY
            type == ReminderType.AUTO_BLOCKER_REMINDER -> Delivery.SHOW_SILENTLY
            else -> Delivery.UPDATE_SILENTLY
        }
        return Outcome(type, delivery)
    }

    /**
     * A check whose time has come (or was missed while the phone was off) is done as a full check, whatever started it.
     * This way another app cannot postpone the check with a fake "setting changed" signal.
     */
    fun effectiveTrigger(trigger: Trigger, scheduledAt: Long, now: Long): Trigger =
        if (scheduledAt in 1..now) Trigger.ALARM else trigger

    /**
     * The time to set again. The stored time is never pushed later, it is only capped at now + interval (when the interval
     * gets shorter or the clock is set back). Without a stored time (first start) it is one interval later.
     */
    fun rescheduleTime(scheduledAt: Long, now: Long, intervalMs: Long): Long =
        if (scheduledAt <= 0L) now + intervalMs else minOf(scheduledAt, now + intervalMs)

    /**
     * The time to set after a check. When the interval is over or the user taps Check, the countdown starts again. The
     * Check button works only on the app's own screen while it is not covered, so no other app can press it. Other
     * triggers never push the time later ([rescheduleTime]).
     */
    fun nextCheck(trigger: Trigger, scheduledAt: Long, now: Long, intervalMs: Long): Long =
        if (trigger == Trigger.ALARM || trigger == Trigger.MANUAL) now + intervalMs else rescheduleTime(scheduledAt, now, intervalMs)
}
