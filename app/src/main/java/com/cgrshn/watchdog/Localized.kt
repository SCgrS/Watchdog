package com.cgrshn.watchdog

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the chosen language to texts. Whatever the phone's language, the app speaks the language chosen in it (English
 * by default). The screen uses this in [MainActivity.attachBaseContext], notifications in [Notifier]. English texts are
 * in `values/` (the default), Turkish ones in `values-tr/`.
 */
object Localized {
    /**
     * The localized application context, kept for the checks that run in the background (alarm, job, phone start), so a
     * new context is not built at every check. Only the application context is kept: It lives as long as the process, so
     * keeping it leaks nothing. Contexts of a screen are built fresh. All calls come from the main thread.
     */
    private var appContext: Pair<Locale, Context>? = null

    fun context(base: Context): Context {
        val locale = Store(base).language.locale
        val current = base.resources.configuration.locales
        if (current.size() > 0 && current[0] == locale) return base
        val isApp = base === base.applicationContext
        if (isApp) appContext?.let { (cachedLocale, cached) -> if (cachedLocale == locale) return cached }
        // The override carries only the language. Passing the whole configuration would freeze the screen metrics at
        // their current values (wrong sizes when a split-screen window is resized).
        val override = Configuration().apply { setLocales(LocaleList(locale)) }
        val localized = base.createConfigurationContext(override)
        if (isApp) appContext = locale to localized
        return localized
    }
}
