package com.cgrshn.watchdog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle

/**
 * The app's single notification. Its type is stored in the notification itself, no separate record is kept. Texts are
 * written in the app's chosen language ([Localized]), and the channel name is renewed when the language changes.
 */
object Notifier {
    private const val CHANNEL = "reminders"
    private const val ID = 1
    private const val TYPE = "com.cgrshn.watchdog.TYPE"
    private const val SILENT_GROUP = "com.cgrshn.watchdog.SILENT"

    /** Request code of the pending intent that opens the app (the settings pages start at 100). */
    private const val OPEN_APP_REQUEST = 1

    fun createChannel(base: Context) {
        val context = Localized.context(base)
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = context.getString(R.string.channel_description)
        manager(context).createNotificationChannel(channel)
    }

    /** Whether notifications can reach the user (permission granted and the channel not turned off). */
    fun canReach(context: Context): Boolean {
        val manager = manager(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Type of the notification visible right now, or null if none. */
    fun visibleType(context: Context): ReminderType? {
        val visible = manager(context).activeNotifications.firstOrNull { it.id == ID } ?: return null
        val name = visible.notification.extras.getString(TYPE)
        return ReminderType.entries.firstOrNull { it.name == name }
    }

    /** Applies the decision. Returns true if a notification was posted. */
    fun apply(base: Context, snapshot: Snapshot, outcome: Outcome): Boolean {
        val context = Localized.context(base)
        createChannel(context)
        val manager = manager(context)
        val type = outcome.type ?: run {
            manager.cancel(ID)
            return false
        }
        val post = when (outcome.delivery) {
            Delivery.REMOVE -> {
                manager.cancel(ID)
                false
            }
            Delivery.LEAVE -> false
            Delivery.ALERT, Delivery.SHOW_SILENTLY -> canReach(context)
            Delivery.UPDATE_SILENTLY -> manager.activeNotifications.any { it.id == ID }
        }
        if (post) manager.notify(ID, build(context, snapshot, type, silent = outcome.delivery != Delivery.ALERT))
        return post
    }

    /**
     * When the language changes, rewrites the visible notification in the new language, silently and without changing
     * its type. The decision does not repost every type (the one-time Auto Blocker reminder is left alone), hence a
     * separate path.
     */
    fun rewrite(base: Context, snapshot: Snapshot) {
        val context = Localized.context(base)
        createChannel(context)
        val type = visibleType(context) ?: return
        manager(context).notify(ID, build(context, snapshot, type, silent = true))
    }

    private fun build(context: Context, snapshot: Snapshot, type: ReminderType, silent: Boolean): Notification {
        val builder = base(context, type, silent)
        when (type) {
            ReminderType.DEBUGGING -> {
                val onSettings = buildList {
                    if (snapshot.usb == State.ON) add(context.getString(R.string.usb_debugging))
                    if (snapshot.wireless == State.ON) add(context.getString(R.string.wireless_debugging))
                }
                // The title names the setting that is on. A tap opens the app, not the settings page (user request,
                // 2026-11-15: going straight to USB debugging makes one forget Auto Blocker). The screen shows debugging
                // and Auto Blocker together.
                val title = when {
                    snapshot.usb == State.ON && snapshot.wireless == State.ON -> R.string.notification_both_title
                    snapshot.usb == State.ON -> R.string.notification_usb_title
                    else -> R.string.notification_wireless_title
                }
                val autoBlockerToo = snapshot.autoBlocker != State.ABSENT && snapshot.autoBlocker != State.ON
                var text = context.getString(
                    R.string.notification_debugging_text,
                    Format.list(onSettings, Store(context).language),
                )
                if (autoBlockerToo) text += " " + context.getString(R.string.notification_auto_blocker_too)
                builder.setContentTitle(context.getString(title))
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(openAppIntent(context))
                    .setAutoCancel(false)
                if (autoBlockerToo) {
                    builder.addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_notification),
                            context.getString(R.string.auto_blocker),
                            SettingsPages.pendingIntent(context, Page.AUTO_BLOCKER),
                        ).setAuthenticationRequired(true).build(),
                    )
                }
            }
            ReminderType.AUTO_BLOCKER_OFF -> {
                val text = context.getString(R.string.notification_auto_blocker_off_text)
                builder.setContentTitle(context.getString(R.string.notification_auto_blocker_off_title))
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(SettingsPages.pendingIntent(context, Page.AUTO_BLOCKER))
                    .setAutoCancel(false)
            }
            ReminderType.AUTO_BLOCKER_REMINDER -> {
                val text = context.getString(R.string.notification_auto_blocker_reminder_text)
                builder.setContentTitle(context.getString(R.string.notification_auto_blocker_reminder_title))
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(SettingsPages.pendingIntent(context, Page.AUTO_BLOCKER))
                    .setAutoCancel(true)
            }
        }
        return builder.build()
    }

    /**
     * A tap opens the app's screen. If the app is already open, it comes to the front instead of opening again. Immutable
     * intent with an explicit component.
     */
    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_APP_REQUEST,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The notification is marked private (VISIBILITY_PRIVATE) with a public version without content. When the phone is set
     * to hide sensitive content on the lock screen, someone who picks up the locked phone sees only "You have a reminder"
     * and cannot read which setting is on. When the phone is set to show all content, Android shows the full text: An app
     * cannot override this (a channel's lock screen visibility is reset by the system on Android 16). The action button
     * does not work before unlocking.
     */
    private fun base(context: Context, type: ReminderType, silent: Boolean): Notification.Builder {
        val lockScreen = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.brand))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_lock_screen_text))
            .build()
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.brand))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .setShowWhen(true)
            .setOnlyAlertOnce(silent)
            .addExtras(Bundle().apply { putString(TYPE, type.name) })
            .also { builder ->
                // Silent posting: Like AndroidX's setSilent, the notification is put in a group and only the group summary
                // alerts. Since there is no summary, the notification appears without sound or vibration.
                if (silent) builder.setGroup(SILENT_GROUP).setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            }
    }

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)
}
