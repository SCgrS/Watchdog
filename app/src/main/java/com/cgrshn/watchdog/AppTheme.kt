package com.cgrshn.watchdog

import android.app.UiModeManager
import android.content.Context

/**
 * Light or dark screen. The default follows the phone (the owner's decision, 2026-11-20). The colors are in
 * values/colors.xml and values-night/colors.xml, the theme is Android's own day and night theme.
 *
 * The choice is applied with UiModeManager.setApplicationNightMode (Android 12 and later, no permission): Android keeps it
 * for the app itself and recreates the app's screens in the new mode. The store keeps the choice too, for the screen's
 * value and the choice window.
 */
enum class AppTheme(val code: String, val label: Int, private val nightMode: Int) {
    SYSTEM("system", R.string.theme_system, UiModeManager.MODE_NIGHT_AUTO),
    LIGHT("light", R.string.theme_light, UiModeManager.MODE_NIGHT_NO),
    DARK("dark", R.string.theme_dark, UiModeManager.MODE_NIGHT_YES),
    ;

    fun apply(context: Context) {
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(nightMode)
    }

    companion object {
        val DEFAULT = SYSTEM

        fun fromCode(code: String?): AppTheme = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
