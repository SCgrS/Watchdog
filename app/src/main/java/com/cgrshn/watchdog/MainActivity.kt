package com.cgrshn.watchdog

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** The single screen: Status, manual check, check interval and language. Tapping a status row opens that setting. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Change signals for the settings' addresses can be sent by any app. Signals that arrive close together are merged
     * into one check half a second after the last one, so a flood of fake signals cannot keep the screen busy.
     */
    private val refreshTask = Runnable { refresh() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(refreshTask)
            handler.postDelayed(refreshTask, CHANGE_DELAY_MS)
        }
    }

    private var lastSnapshot: Snapshot? = null
    private val rows = mutableMapOf<String, View>()
    private val locations = mutableMapOf<String, Int>()

    /** The screen opens in the app's chosen language, independent of the phone's language. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Localized.context(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)
        Notifier.createChannel(this)
        applyInsets()
        buildStatusRows()

        find<Button>(R.id.check_button).setOnClickListener { checkNow() }
        find<View>(R.id.interval_row).setOnClickListener { chooseInterval() }
        find<View>(R.id.language_row).setOnClickListener { chooseLanguage() }
        find<Button>(R.id.notification_button).setOnClickListener { requestNotificationPermission(userAsked = true) }
    }

    override fun onResume() {
        super.onResume()
        for (uri in SettingsReader.uris) contentResolver.registerContentObserver(uri, false, observer)
        refresh()
        requestNotificationPermission(userAsked = false)
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(observer)
        handler.removeCallbacks(refreshTask)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        lastSnapshot?.let { show(it) }
    }

    /** A check while the screen is open raises no new alert, it only updates the visible notification. */
    private fun refresh() {
        val snapshot = Checker.check(this, Trigger.SOFT)
        lastSnapshot = snapshot
        show(snapshot)
    }

    /**
     * Manual check: The settings are read right away, and if one is on, the notification is shown silently. The result is
     * told in a short message. The next check is set one interval later.
     */
    private fun checkNow() {
        val snapshot = Checker.check(this, Trigger.MANUAL)
        lastSnapshot = snapshot
        show(snapshot)
        Toast.makeText(this, getString(R.string.check_result, summary(snapshot)), Toast.LENGTH_SHORT).show()
    }

    private fun show(snapshot: Snapshot) {
        showWarnings()
        showStatusRow(ROW_USB, snapshot.usb, onIsRisky = true)
        showStatusRow(ROW_WIRELESS, snapshot.wireless, onIsRisky = true)
        showStatusRow(ROW_UNKNOWN_APPS, snapshot.unknownApps, onIsRisky = true, apps = snapshot.installers.map { it.label })
        showStatusRow(ROW_AUTO_BLOCKER, snapshot.autoBlocker, onIsRisky = false)
        find<View>(R.id.auto_blocker_note).visibility =
            if (snapshot.autoBlocker == State.UNKNOWN) View.VISIBLE else View.GONE
        val lastCheck = store.lastCheck
        find<TextView>(R.id.last_check).text =
            if (lastCheck > 0) getString(R.string.last_check, Format.time(lastCheck, store.language)) else ""
        find<TextView>(R.id.interval_value).text = Format.interval(store.checkMinutes, store.language)
        find<TextView>(R.id.next_check).text = Format.date(store.nextCheck, store.language)
        find<TextView>(R.id.language_value).text = store.language.displayName
    }

    /**
     * The result message of Check. While Auto Blocker cannot be read, it does not say "All clear", it says only that
     * nothing is left on (a big "all clear" was misleading while the other settings were unknown).
     */
    private fun summary(snapshot: Snapshot): String {
        val onSettings = buildList {
            if (snapshot.usb == State.ON) add(getString(R.string.usb_debugging))
            if (snapshot.wireless == State.ON) add(getString(R.string.wireless_debugging))
            if (snapshot.unknownApps == State.ON) add(getString(R.string.unknown_apps))
        }
        return when {
            onSettings.size == 1 -> getString(R.string.summary_one_on, onSettings[0])
            onSettings.size > 1 -> getString(R.string.summary_many_on, onSettings.size)
            snapshot.autoBlocker == State.OFF -> getString(R.string.summary_auto_blocker_off)
            snapshot.autoBlocker == State.UNKNOWN || snapshot.unknownApps == State.UNKNOWN ->
                getString(R.string.summary_nothing_on)
            else -> getString(R.string.summary_all_clear)
        }
    }

    private fun buildStatusRows() {
        val container = find<LinearLayout>(R.id.status_rows)
        // Tapping a row opens that setting. For USB and wireless debugging, Developer options scrolls to the setting. The
        // line under the name tells where the setting lives on the phone. Install unknown apps opens the switch of the
        // app it is allowed for, or the list when there are several.
        val rowInfos = listOf(
            RowInfo(ROW_USB, R.string.usb_debugging, R.string.location_developer_options, Page.USB),
            RowInfo(ROW_WIRELESS, R.string.wireless_debugging, R.string.location_developer_options, Page.WIRELESS),
            RowInfo(ROW_UNKNOWN_APPS, R.string.unknown_apps, R.string.location_special_access, Page.UNKNOWN_APPS),
            RowInfo(ROW_AUTO_BLOCKER, R.string.auto_blocker, R.string.location_security, Page.AUTO_BLOCKER),
        )
        val inflater = LayoutInflater.from(this)
        for ((index, info) in rowInfos.withIndex()) {
            if (index > 0) inflater.inflate(R.layout.divider, container, true)
            val row = inflater.inflate(R.layout.row_status, container, false)
            row.findViewById<TextView>(R.id.name).setText(info.name)
            row.findViewById<TextView>(R.id.location).setText(info.location)
            row.setOnClickListener {
                val single = lastSnapshot?.installers?.singleOrNull()?.packageName
                SettingsPages.open(this, info.page, app = if (info.page == Page.UNKNOWN_APPS) single else null)
            }
            container.addView(row)
            rows[info.key] = row
            locations[info.key] = info.location
        }
    }

    /**
     * [onIsRisky]: For debugging settings and Install unknown apps being on is the risk, for Auto Blocker being off.
     * [apps]: The apps Install unknown apps is allowed for. While there are any, they take the place of the location line.
     */
    private fun showStatusRow(key: String, state: State, onIsRisky: Boolean, apps: List<String> = emptyList()) {
        val row = rows[key] ?: return
        val risky = (state == State.ON && onIsRisky) || (state == State.OFF && !onIsRisky)
        val text = when (state) {
            State.ON -> R.string.state_on
            State.OFF -> R.string.state_off
            State.UNKNOWN -> R.string.state_unknown
            State.ABSENT -> R.string.state_absent
        }
        val lamp = when {
            risky -> R.color.lamp_risk
            state == State.ON || state == State.OFF -> R.color.lamp_safe
            else -> R.color.lamp_neutral
        }
        val value = row.findViewById<TextView>(R.id.value)
        value.setText(text)
        value.setTextColor(getColor(if (risky) R.color.text_risk else R.color.text_secondary))
        row.findViewById<View>(R.id.lamp).backgroundTintList = ColorStateList.valueOf(getColor(lamp))
        val locationView = row.findViewById<TextView>(R.id.location)
        if (apps.isEmpty()) {
            locationView.setText(locations.getValue(key))
            locationView.setTextColor(getColor(R.color.text_muted))
        } else {
            locationView.text = Format.list(apps, store.language)
            locationView.setTextColor(getColor(R.color.text_risk))
        }
        // Screen readers read the name, the value and where the setting lives on the phone (or the apps it is on for).
        val location = locationView.text
        row.contentDescription = getString(rowName(key)) + ", " + getString(text) + ", " + location
    }

    private fun rowName(key: String) = when (key) {
        ROW_USB -> R.string.usb_debugging
        ROW_WIRELESS -> R.string.wireless_debugging
        ROW_UNKNOWN_APPS -> R.string.unknown_apps
        else -> R.string.auto_blocker
    }

    private fun showWarnings() {
        find<View>(R.id.notification_warning).visibility = if (Notifier.canReach(this)) View.GONE else View.VISIBLE

        val box = find<View>(R.id.background_warning)
        val text = find<TextView>(R.id.background_warning_text)
        val button = find<Button>(R.id.background_button)
        val restricted = getSystemService(ActivityManager::class.java).isBackgroundRestricted
        // Samsung puts apps to sleep whose battery setting is not "Unrestricted" and that were not opened for a while.
        // Their alarms do not ring.
        val canSleep = !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val canBePaused = !packageManager.isAutoRevokeWhitelisted
        when {
            restricted || canSleep -> {
                box.visibility = View.VISIBLE
                text.setText(if (restricted) R.string.background_restricted else R.string.can_sleep)
                button.setText(R.string.open_battery_settings)
                button.setOnClickListener { SettingsPages.open(this, Page.BATTERY) }
            }
            canBePaused -> {
                box.visibility = View.VISIBLE
                text.setText(R.string.can_be_paused)
                button.setText(R.string.open_pause_settings)
                button.setOnClickListener { SettingsPages.open(this, Page.PAUSE) }
            }
            else -> box.visibility = View.GONE
        }
    }

    /**
     * Notification permission (Android 13 and later). Asked once on first start. After that only when the user taps the
     * button: If Android can ask again it asks, otherwise the app's notification settings open.
     */
    private fun requestNotificationPermission(userAsked: Boolean) {
        val needed = Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (userAsked) {
            if (needed && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                SettingsPages.open(this, Page.NOTIFICATIONS)
            }
        } else if (needed && !store.notificationPermissionAsked) {
            store.notificationPermissionAsked = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun chooseInterval() {
        val intervals = Intervals.CHECK_MINUTES
        ChoiceDialog.show(
            this,
            title = R.string.check_interval,
            subtitle = R.string.check_interval_description,
            options = intervals.map { Format.interval(it, store.language) },
            selected = intervals.indexOf(store.checkMinutes),
        ) { index ->
            Checker.changeInterval(this, intervals[index])
            refresh()
        }
    }

    /**
     * Language choice. Each language name is written in its own language. After a choice, the visible notification is
     * rewritten silently in the new language and the screen is rebuilt in it.
     */
    private fun chooseLanguage() {
        val languages = Language.entries
        ChoiceDialog.show(
            this,
            title = R.string.language,
            subtitle = R.string.language_description,
            options = languages.map { it.displayName },
            selected = languages.indexOf(store.language),
        ) { index ->
            store.language = languages[index]
            Notifier.rewrite(this, Checker.check(this, Trigger.SOFT))
            recreate()
        }
    }

    /**
     * Since Android 15 the screen is drawn edge to edge. Scrolled content is cut below the status bar (so it does not clash
     * with the clock) and may pass behind the navigation bar, but the last row ends above it.
     */
    private fun applyInsets() {
        val content = find<View>(R.id.content)
        val left = content.paddingLeft
        val top = content.paddingTop
        val right = content.paddingRight
        val bottom = content.paddingBottom
        find<View>(R.id.scroll).setOnApplyWindowInsetsListener { scroll, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            scroll.setPadding(bars.left, bars.top, bars.right, 0)
            content.setPadding(left, top, right, bottom + bars.bottom)
            insets
        }
    }

    private fun <T : View> find(id: Int): T = findViewById(id)

    private data class RowInfo(val key: String, val name: Int, val location: Int, val page: Page)

    private companion object {
        const val CHANGE_DELAY_MS = 500L
        const val ROW_USB = "usb"
        const val ROW_WIRELESS = "wireless"
        const val ROW_UNKNOWN_APPS = "unknown_apps"
        const val ROW_AUTO_BLOCKER = "auto_blocker"
    }
}
