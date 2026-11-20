package com.cgrshn.watchdog

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
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
        val (unknownApps, installers) = installers(context)
        return Snapshot(
            usb = global(resolver, Settings.Global.ADB_ENABLED),
            wireless = global(resolver, WIRELESS_KEY),
            autoBlocker = autoBlocker(context),
            unknownApps = unknownApps,
            installers = installers,
        )
    }

    /**
     * Install unknown apps (Settings › Apps › Special access). Since Android 8 this is not one switch but a permission per
     * app: An app that asks for REQUEST_INSTALL_PACKAGES may open APK files for installing only while the user allows it.
     * The permission is an app operation, so it is read from AppOpsManager. Allowed means MODE_ALLOWED, the mode the
     * Settings page sets when the switch is turned on. Turning it off sets another mode.
     *
     * Only the apps Android lets Watchdog see are checked: Apps with an icon in the app list (the manifest's <queries>),
     * which are the browsers, file managers and messaging apps people allow. They are only looked at here, nothing about
     * them is stored or leaves the phone.
     *
     * Not counted: Apps that hold INSTALL_PACKAGES (the phone's own app stores and installers). They install without
     * asking, so their switch changes nothing and a reminder about it could never be cleared.
     *
     * Lint's QueryPermissionsNeeded is suppressed on purpose: The list is meant to hold only the visible apps, and the
     * manifest's <queries> makes the ones that matter visible. QUERY_ALL_PACKAGES is not used.
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun installers(context: Context): Pair<State, List<Installer>> {
        val op = AppOpsManager.permissionToOp(Manifest.permission.REQUEST_INSTALL_PACKAGES) ?: return State.UNKNOWN to emptyList()
        val packages = context.packageManager
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val installed = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packages.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packages.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
        } catch (_: RuntimeException) {
            // A list too large for one transfer or a dying package service: The state is unknown, never "off".
            return State.UNKNOWN to emptyList()
        }
        val allowed = installed.mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val asks = info.requestedPermissions?.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES) == true
            if (!asks || info.packageName == context.packageName) return@mapNotNull null
            if (packages.checkPermission(Manifest.permission.INSTALL_PACKAGES, info.packageName) == PackageManager.PERMISSION_GRANTED) {
                return@mapNotNull null
            }
            val mode = appOps.checkOpNoThrow(op, app.uid, info.packageName)
            if (mode != AppOpsManager.MODE_ALLOWED) return@mapNotNull null
            Installer(info.packageName, app.loadLabel(packages).toString().trim().ifEmpty { info.packageName })
        }
        val sorted = allowed.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        return (if (sorted.isEmpty()) State.OFF else State.ON) to sorted
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
