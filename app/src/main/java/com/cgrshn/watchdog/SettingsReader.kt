package com.cgrshn.watchdog

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Only reads settings. The app has no permission to write settings and this file contains no write call.
 *
 * Auto Blocker is Samsung's. Its state lives in the secure setting `rampart_main_switch_enabled` (seen on a Samsung
 * phone with One UI 8 and Android 16: 0 off, 1 on). Since Android 12 some hidden settings are closed to apps. If it
 * cannot be read, the state is UNKNOWN and the app falls back to a general reminder.
 */
object SettingsReader {
    const val AUTO_BLOCKER_PACKAGE = "com.samsung.android.rampart"
    const val AUTO_BLOCKER_KEY = "rampart_main_switch_enabled"
    private const val WIRELESS_KEY = "adb_wifi_enabled"

    /** Addresses of the watched settings, for change notifications. */
    val uris: List<Uri>
        get() = listOf(
            Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
            Settings.Global.getUriFor(WIRELESS_KEY),
            Settings.Secure.getUriFor(AUTO_BLOCKER_KEY),
        )

    fun read(context: Context): Snapshot {
        val resolver = context.contentResolver
        return Snapshot(
            usb = global(resolver, Settings.Global.ADB_ENABLED),
            wireless = global(resolver, WIRELESS_KEY),
            autoBlocker = autoBlocker(context),
        )
    }

    private fun global(resolver: ContentResolver, key: String): State =
        try {
            parse(Settings.Global.getString(resolver, key))
        } catch (_: SecurityException) {
            State.UNKNOWN
        }

    private fun autoBlocker(context: Context): State {
        if (!autoBlockerIsSystemApp(context)) return State.ABSENT
        return try {
            when (val value = parse(Settings.Secure.getString(context.contentResolver, AUTO_BLOCKER_KEY))) {
                State.ABSENT -> State.UNKNOWN
                else -> value
            }
        } catch (_: SecurityException) {
            State.UNKNOWN
        }
    }

    /**
     * Auto Blocker counts only as the phone's own system app. On a phone without it, any sideloaded app could take the
     * package name, and Watchdog would then remind about it and open that app's screen. Samsung's Auto Blocker lives in
     * /system/app (seen on One UI 8).
     */
    fun autoBlockerIsSystemApp(context: Context): Boolean =
        try {
            val packages = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packages.getApplicationInfo(AUTO_BLOCKER_PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packages.getApplicationInfo(AUTO_BLOCKER_PACKAGE, 0)
            }
            info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun parse(value: String?): State {
        val number = value?.trim()?.toIntOrNull() ?: return State.ABSENT
        return if (number != 0) State.ON else State.OFF
    }
}
