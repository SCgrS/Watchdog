package com.cgrshn.watchdog

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The check intervals to choose from, and the default. */
object Intervals {
    /** Check interval in minutes: 1 hour, 6 hours, 1 day, 2 days, 3 days, 1 week. */
    val CHECK_MINUTES = intArrayOf(60, 360, 1_440, 2_880, 4_320, 10_080)

    const val DEFAULT_MINUTES = 10_080
}

/**
 * The app's languages. Independent of the phone's language, the default is English. Each name is written in its own
 * language, which is how it appears in the language picker.
 */
enum class Language(val code: String, val displayName: String, val locale: Locale) {
    ENGLISH("en", "English", Locale.forLanguageTag("en-GB")),
    TURKISH("tr", "Türkçe", Locale.forLanguageTag("tr-TR")),
    ;

    companion object {
        val DEFAULT = ENGLISH

        fun fromCode(code: String?): Language = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/** Interval, date and list formats used on the screen and in notifications. */
object Format {
    private val LONG_DATE = mapOf(
        Language.ENGLISH to DateTimeFormatter.ofPattern("EEEE, d MMMM 'at' HH:mm", Language.ENGLISH.locale),
        // Turkish spelling rules (TDK) separate hours and minutes with a dot.
        Language.TURKISH to DateTimeFormatter.ofPattern("d MMMM EEEE HH.mm", Language.TURKISH.locale),
    )
    private val TIME = mapOf(
        Language.ENGLISH to DateTimeFormatter.ofPattern("HH:mm", Language.ENGLISH.locale),
        Language.TURKISH to DateTimeFormatter.ofPattern("HH.mm", Language.TURKISH.locale),
    )

    /**
     * English: "Every hour", "Every 6 hours", "Every day", "Every 3 days", "Every week".
     * Turkish: "Saatte bir", "6 saatte bir", "Günde bir", "3 günde bir", "Haftada bir".
     */
    fun interval(minutes: Int, language: Language = Language.DEFAULT): String {
        val (count, unit) = when {
            minutes >= 10_080 && minutes % 10_080 == 0 -> minutes / 10_080 to IntervalUnit.WEEK
            minutes >= 1_440 && minutes % 1_440 == 0 -> minutes / 1_440 to IntervalUnit.DAY
            minutes >= 60 && minutes % 60 == 0 -> minutes / 60 to IntervalUnit.HOUR
            else -> minutes to IntervalUnit.MINUTE
        }
        return when (language) {
            Language.ENGLISH -> if (count == 1) "Every ${unit.english}" else "Every $count ${unit.english}s"
            Language.TURKISH -> if (count == 1 && unit != IntervalUnit.MINUTE) unit.turkishSingle else "$count ${unit.turkish} bir"
        }
    }

    private enum class IntervalUnit(val english: String, val turkishSingle: String, val turkish: String) {
        MINUTE("minute", "Dakikada bir", "dakikada"),
        HOUR("hour", "Saatte bir", "saatte"),
        DAY("day", "Günde bir", "günde"),
        WEEK("week", "Haftada bir", "haftada"),
    }

    /** "Sunday, 15 November at 14:30", "15 Kasım Pazar 14.30". */
    fun date(ms: Long, language: Language = Language.DEFAULT, zone: ZoneId = ZoneId.systemDefault()): String =
        LONG_DATE.getValue(language).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "14:32", "14.32". */
    fun time(ms: Long, language: Language = Language.DEFAULT, zone: ZoneId = ZoneId.systemDefault()): String =
        TIME.getValue(language).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "A", "A and B", "A, B and C" or "A ve B", "A, B ve C". */
    fun list(items: List<String>, language: Language = Language.DEFAULT): String {
        val and = if (language == Language.TURKISH) " ve " else " and "
        return when (items.size) {
            0 -> ""
            1 -> items[0]
            else -> items.dropLast(1).joinToString(", ") + and + items.last()
        }
    }
}
