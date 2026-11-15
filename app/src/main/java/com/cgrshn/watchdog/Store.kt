package com.cgrshn.watchdog

import android.annotation.SuppressLint
import android.content.Context

/**
 * The app's small persistent store. Not backed up, other apps cannot read it. Values that the schedule depends on go to
 * disk right away on purpose (commit): Receivers and the job finish quickly and the process can end at any moment. A value
 * is written only when it changes, so frequent checks do not wear the storage. The file holds only a few values.
 */
@SuppressLint("ApplySharedPref")
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("store", Context.MODE_PRIVATE)

    var checkMinutes: Int
        get() = prefs.getInt("check_minutes", Intervals.DEFAULT_MINUTES)
            .let { if (it in Intervals.CHECK_MINUTES) it else Intervals.DEFAULT_MINUTES }
        set(value) {
            if (prefs.getInt("check_minutes", Intervals.DEFAULT_MINUTES) != value) {
                prefs.edit().putInt("check_minutes", value).commit()
            }
        }

    val checkMs: Long get() = checkMinutes * 60_000L

    /**
     * Debugging settings were seen on while Auto Blocker could not be read: When they go off, Auto Blocker is reminded
     * once. Cleared when the reminder is shown or Auto Blocker becomes readable.
     */
    var autoBlockerPending: Boolean
        get() = prefs.getBoolean("auto_blocker_pending", false)
        set(value) {
            if (prefs.getBoolean("auto_blocker_pending", false) != value) {
                prefs.edit().putBoolean("auto_blocker_pending", value).commit()
            }
        }

    /** Shown on the screen only. Written in the background (apply), losing it at a crash does no harm. */
    var lastCheck: Long
        get() = prefs.getLong("last_check", 0L)
        set(value) { prefs.edit().putLong("last_check", value).apply() }

    var nextCheck: Long
        get() = prefs.getLong("next_check", 0L)
        set(value) {
            if (prefs.getLong("next_check", 0L) != value) prefs.edit().putLong("next_check", value).commit()
        }

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean("notification_permission_asked", false)
        set(value) { prefs.edit().putBoolean("notification_permission_asked", value).commit() }

    /** The app's language. The default is English, independent of the phone's language. */
    var language: Language
        get() = Language.fromCode(prefs.getString("language", null))
        set(value) { prefs.edit().putString("language", value.code).commit() }
}
