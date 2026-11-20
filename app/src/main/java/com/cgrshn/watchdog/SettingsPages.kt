package com.cgrshn.watchdog

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * The settings pages the app can open. The app only opens them and never touches the settings inside. USB and WIRELESS
 * open Developer options scrolled to that setting. UNKNOWN_APPS opens Install unknown apps. New pages go at the end: The
 * position numbers the notifications' pending intents.
 */
enum class Page { USB, WIRELESS, AUTO_BLOCKER, NOTIFICATIONS, BATTERY, PAUSE, UNKNOWN_APPS }

object SettingsPages {
    /** Keys of the settings in Developer options (the same in AOSP and Samsung's SecSettings, checked 2026-11-14). */
    private const val USB_KEY = "enable_adb"
    private const val WIRELESS_KEY = "toggle_adb_wireless"
    private const val HIGHLIGHT_KEY = ":settings:fragment_args_key"
    private const val FRAGMENT_ARGS = ":settings:show_fragment_args"

    /**
     * The system Settings app (AOSP, Pixel and Samsung). Settings pages are first sent to it by package, so no other app
     * that declares the same action can step in. A sideloaded app cannot take this name because the system app has it.
     */
    private const val SETTINGS_PACKAGE = "com.android.settings"

    /** Samsung's Auto Blocker screen (exported, needs no permission). */
    private const val AUTO_BLOCKER_SCREEN = "com.samsung.android.rampart.ui.MainSettingActivity"
    private const val AUTO_BLOCKER_ACTION = "com.samsung.android.rampart.action.MAIN_SETTING_ACTIVITY"

    /**
     * Opens the page. If the first candidate is missing, tries the next. Returns false if none opens. [app]: For
     * UNKNOWN_APPS, the app whose switch opens directly. Without it, the list of apps opens.
     */
    fun open(context: Context, page: Page, app: String? = null): Boolean {
        for (intent in candidates(context, page, app)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    /** Pending intent that opens the page through [SettingsOpenerActivity], for notifications. */
    fun pendingIntent(context: Context, page: Page): PendingIntent = PendingIntent.getActivity(
        context,
        100 + page.ordinal,
        Intent(context, SettingsOpenerActivity::class.java).putExtra(SettingsOpenerActivity.PAGE, page.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun candidates(context: Context, page: Page, app: String?): List<Intent> {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val appInfo = settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        return when (page) {
            Page.USB, Page.WIRELESS -> {
                val key = if (page == Page.USB) USB_KEY else WIRELESS_KEY
                settings(highlighted(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, key)) +
                    settings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) +
                    settings(Intent(Settings.ACTION_SETTINGS))
            }
            Page.AUTO_BLOCKER -> {
                // The Auto Blocker screen is opened only when it belongs to the phone's own system app.
                val autoBlocker = if (SettingsReader.autoBlockerIsSystemApp(context)) {
                    listOf(
                        Intent(AUTO_BLOCKER_ACTION).setClassName(SettingsReader.AUTO_BLOCKER_PACKAGE, AUTO_BLOCKER_SCREEN),
                    )
                } else {
                    emptyList()
                }
                autoBlocker + settings(Intent(Settings.ACTION_SECURITY_SETTINGS)) + settings(Intent(Settings.ACTION_SETTINGS))
            }
            Page.NOTIFICATIONS -> settings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            ) + appInfo
            Page.BATTERY -> appInfo
            Page.PAUSE -> settings(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, packageUri)) + appInfo
            Page.UNKNOWN_APPS -> {
                val list = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                val single = app?.let { listOf(Intent(list).setData(Uri.fromParts("package", it, null))) }.orEmpty()
                single.flatMap(::settings) + settings(list) + settings(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    /**
     * The intent first addressed to the system Settings app, then as it is (for phones whose Settings app has another
     * package name). The intents carry only a package name (the app's own, or the app shown on the Install unknown apps
     * row) and a settings key.
     */
    private fun settings(intent: Intent): List<Intent> = listOf(Intent(intent).setPackage(SETTINGS_PACKAGE), intent)

    /**
     * The path the Settings app's own search uses: The page opens scrolled to the setting with the given key and
     * highlights it. The key is passed both directly and as a fragment argument, since Settings versions read one or the
     * other. On phones that do not support it, the page simply opens.
     */
    private fun highlighted(action: String, key: String): Intent = Intent(action)
        .putExtra(HIGHLIGHT_KEY, key)
        .putExtra(FRAGMENT_ARGS, Bundle().apply { putString(HIGHLIGHT_KEY, key) })
}

/** Invisible in-between screen: Turns a request from a notification into a settings page and closes. Not exported. */
class SettingsOpenerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent?.getStringExtra(PAGE)
        Page.entries.firstOrNull { it.name == name }?.let { SettingsPages.open(this, it) }
        finish()
    }

    companion object {
        const val PAGE = "page"
    }
}
