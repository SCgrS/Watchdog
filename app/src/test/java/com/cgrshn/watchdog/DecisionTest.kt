package com.cgrshn.watchdog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecisionTest {
    private fun snapshot(
        usb: State = State.OFF,
        wireless: State = State.OFF,
        autoBlocker: State = State.UNKNOWN,
    ) = Snapshot(usb, wireless, autoBlocker)

    private val usbOn = snapshot(usb = State.ON)
    private val allOff = snapshot()

    @Test
    fun intervalOverWithSettingOnAlerts() {
        val outcome = Decision.decide(usbOn, visible = null, trigger = Trigger.ALARM)
        assertEquals(ReminderType.DEBUGGING, outcome.type)
        assertEquals(Delivery.ALERT, outcome.delivery)
    }

    @Test
    fun intervalOverWithVisibleNotificationAlertsAgain() {
        val outcome = Decision.decide(usbOn, visible = ReminderType.DEBUGGING, trigger = Trigger.ALARM)
        assertEquals(Delivery.ALERT, outcome.delivery)
    }

    @Test
    fun wirelessDebuggingAloneAlerts() {
        val outcome = Decision.decide(snapshot(wireless = State.ON), visible = null, trigger = Trigger.ALARM)
        assertEquals(ReminderType.DEBUGGING, outcome.type)
    }

    @Test
    fun noNotificationWhenSettingsOffAtIntervalEnd() {
        val outcome = Decision.decide(allOff, visible = null, trigger = Trigger.ALARM)
        assertNull(outcome.type)
        assertEquals(Delivery.REMOVE, outcome.delivery)
    }

    @Test
    fun turningSettingOnRaisesNoNewAlert() {
        val outcome = Decision.decide(usbOn, visible = null, trigger = Trigger.SOFT)
        assertEquals(Delivery.UPDATE_SILENTLY, outcome.delivery)
    }

    @Test
    fun withUnreadableAutoBlockerNotificationTurnsIntoReminderWhenSettingsGoOff() {
        val outcome = Decision.decide(allOff, visible = ReminderType.DEBUGGING, trigger = Trigger.SOFT)
        assertEquals(ReminderType.AUTO_BLOCKER_REMINDER, outcome.type)
        assertEquals(Delivery.SHOW_SILENTLY, outcome.delivery)
    }

    @Test
    fun autoBlockerReminderIsOneTimeAndNeverSoundsAgain() {
        val outcome = Decision.decide(allOff, visible = ReminderType.AUTO_BLOCKER_REMINDER, trigger = Trigger.ALARM)
        assertEquals(ReminderType.AUTO_BLOCKER_REMINDER, outcome.type)
        assertEquals(Delivery.LEAVE, outcome.delivery)
    }

    @Test
    fun autoBlockerReminderDoesNotComeBackAfterSwipe() {
        val outcome = Decision.decide(allOff, visible = null, trigger = Trigger.ALARM)
        assertNull(outcome.type)
    }

    @Test
    fun settingTurnedOnAgainWhileReminderIsVisible() {
        val outcome = Decision.decide(usbOn, visible = ReminderType.AUTO_BLOCKER_REMINDER, trigger = Trigger.SOFT)
        assertEquals(ReminderType.DEBUGGING, outcome.type)
        assertEquals(Delivery.UPDATE_SILENTLY, outcome.delivery)
    }

    @Test
    fun notificationGoesWhenSettingsOffAndReadableAutoBlockerOn() {
        val outcome = Decision.decide(snapshot(autoBlocker = State.ON), visible = ReminderType.DEBUGGING, trigger = Trigger.SOFT)
        assertNull(outcome.type)
        assertEquals(Delivery.REMOVE, outcome.delivery)
    }

    @Test
    fun autoBlockerReadAsOffIsRemindedSeparately() {
        val outcome = Decision.decide(snapshot(autoBlocker = State.OFF), visible = null, trigger = Trigger.ALARM)
        assertEquals(ReminderType.AUTO_BLOCKER_OFF, outcome.type)
        assertEquals(Delivery.ALERT, outcome.delivery)
    }

    @Test
    fun notificationGoesWhenAutoBlockerTurnsOn() {
        val outcome = Decision.decide(snapshot(autoBlocker = State.ON), visible = ReminderType.AUTO_BLOCKER_OFF, trigger = Trigger.SOFT)
        assertEquals(Delivery.REMOVE, outcome.delivery)
    }

    @Test
    fun notificationGoesOnPhoneWithoutAutoBlocker() {
        val outcome = Decision.decide(snapshot(autoBlocker = State.ABSENT), visible = ReminderType.DEBUGGING, trigger = Trigger.SOFT)
        assertEquals(Delivery.REMOVE, outcome.delivery)
    }

    @Test
    fun autoBlockerIsRemindedSilentlyEvenIfAlertWasNeverSeen() {
        val outcome = Decision.decide(allOff, visible = null, trigger = Trigger.SOFT, autoBlockerPending = true)
        assertEquals(ReminderType.AUTO_BLOCKER_REMINDER, outcome.type)
        assertEquals(Delivery.SHOW_SILENTLY, outcome.delivery)
    }

    @Test
    fun pendingReminderWaitsWhileSettingsAreOn() {
        val outcome = Decision.decide(usbOn, visible = null, trigger = Trigger.SOFT, autoBlockerPending = true)
        assertEquals(ReminderType.DEBUGGING, outcome.type)
        assertEquals(Delivery.UPDATE_SILENTLY, outcome.delivery)
    }

    @Test
    fun noNotificationWhenNothingIsPending() {
        val outcome = Decision.decide(allOff, visible = null, trigger = Trigger.SOFT, autoBlockerPending = false)
        assertNull(outcome.type)
    }

    @Test
    fun afterRestartNotificationComesBackSilently() {
        val outcome = Decision.decide(usbOn, visible = null, trigger = Trigger.BOOT)
        assertEquals(ReminderType.DEBUGGING, outcome.type)
        assertEquals(Delivery.SHOW_SILENTLY, outcome.delivery)
    }

    @Test
    fun afterRestartNoNotificationWhenSettingsOff() {
        val outcome = Decision.decide(allOff, visible = null, trigger = Trigger.BOOT)
        assertEquals(Delivery.REMOVE, outcome.delivery)
    }

    @Test
    fun overdueCheckIsAFullCheckFromAnyTrigger() {
        assertEquals(Trigger.ALARM, Decision.effectiveTrigger(Trigger.SOFT, scheduledAt = 900, now = 1_000))
        assertEquals(Trigger.ALARM, Decision.effectiveTrigger(Trigger.BOOT, scheduledAt = 1_000, now = 1_000))
        assertEquals(Trigger.SOFT, Decision.effectiveTrigger(Trigger.SOFT, scheduledAt = 1_001, now = 1_000))
        assertEquals(Trigger.SOFT, Decision.effectiveTrigger(Trigger.SOFT, scheduledAt = 0, now = 1_000))
    }

    @Test
    fun checkTimeIsNeverPushedLater() {
        val week = 7 * 24 * 3_600_000L
        val now = 1_000_000_000_000L
        // First start: One interval later.
        assertEquals(now + week, Decision.rescheduleTime(0, now, week))
        // Fake change signal or app start: The stored time is kept.
        assertEquals(now + 5_000, Decision.rescheduleTime(now + 5_000, now, week))
        // Clock set back or interval shortened: Capped at now + interval.
        assertEquals(now + 3_600_000L, Decision.rescheduleTime(now + week, now, 3_600_000L))
    }

    @Test
    fun unreadableWirelessSettingIsNotCountedAsOn() {
        val outcome = Decision.decide(snapshot(wireless = State.UNKNOWN), visible = null, trigger = Trigger.ALARM)
        assertNull(outcome.type)
    }

    @Test
    fun manualCheckShowsOnSettingSilently() {
        val outcome = Decision.decide(usbOn, visible = null, trigger = Trigger.MANUAL)
        assertEquals(ReminderType.DEBUGGING, outcome.type)
        assertEquals(Delivery.SHOW_SILENTLY, outcome.delivery)
    }

    @Test
    fun manualCheckRemovesNotificationWhenSettingsOff() {
        val outcome = Decision.decide(snapshot(autoBlocker = State.ON), visible = ReminderType.DEBUGGING, trigger = Trigger.MANUAL)
        assertNull(outcome.type)
        assertEquals(Delivery.REMOVE, outcome.delivery)
    }

    @Test
    fun manualCheckDoesNotReplayAutoBlockerReminder() {
        val outcome = Decision.decide(allOff, visible = ReminderType.AUTO_BLOCKER_REMINDER, trigger = Trigger.MANUAL)
        assertEquals(Delivery.LEAVE, outcome.delivery)
    }

    @Test
    fun overdueCheckIsAFullCheckOnManualCheckToo() {
        assertEquals(Trigger.ALARM, Decision.effectiveTrigger(Trigger.MANUAL, scheduledAt = 900, now = 1_000))
        assertEquals(Trigger.MANUAL, Decision.effectiveTrigger(Trigger.MANUAL, scheduledAt = 1_001, now = 1_000))
    }

    @Test
    fun checkButtonMovesNextCheckOneIntervalLater() {
        val week = 7 * 24 * 3_600_000L
        val now = 1_000_000_000_000L
        assertEquals(now + week, Decision.nextCheck(Trigger.MANUAL, scheduledAt = now + 1_000, now = now, intervalMs = week))
        assertEquals(now + week, Decision.nextCheck(Trigger.ALARM, scheduledAt = now - 1_000, now = now, intervalMs = week))
    }

    @Test
    fun settingChangeAndBootNeverPostponeNextCheck() {
        val week = 7 * 24 * 3_600_000L
        val now = 1_000_000_000_000L
        assertEquals(now + 1_000, Decision.nextCheck(Trigger.SOFT, scheduledAt = now + 1_000, now = now, intervalMs = week))
        assertEquals(now + 1_000, Decision.nextCheck(Trigger.BOOT, scheduledAt = now + 1_000, now = now, intervalMs = week))
    }
}
