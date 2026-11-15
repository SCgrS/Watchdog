package com.cgrshn.watchdog

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun englishIntervals() {
        assertEquals(
            listOf("Every hour", "Every 6 hours", "Every day", "Every 2 days", "Every 3 days", "Every week"),
            Intervals.CHECK_MINUTES.map { Format.interval(it, Language.ENGLISH) },
        )
        assertEquals("Every 2 weeks", Format.interval(20_160, Language.ENGLISH))
        assertEquals("Every 30 minutes", Format.interval(30, Language.ENGLISH))
    }

    @Test
    fun turkishIntervals() {
        assertEquals(
            listOf("Saatte bir", "6 saatte bir", "Günde bir", "2 günde bir", "3 günde bir", "Haftada bir"),
            Intervals.CHECK_MINUTES.map { Format.interval(it, Language.TURKISH) },
        )
        assertEquals("2 haftada bir", Format.interval(20_160, Language.TURKISH))
        assertEquals("30 dakikada bir", Format.interval(30, Language.TURKISH))
    }

    @Test
    fun defaultIntervalIsWeekly() {
        assertEquals(10_080, Intervals.DEFAULT_MINUTES)
    }

    @Test
    fun englishDates() {
        assertEquals("Sunday, 15 November at 14:30", Format.date(at(15, 14, 30), Language.ENGLISH, zone))
        assertEquals("09:05", Format.time(at(14, 9, 5), Language.ENGLISH, zone))
    }

    @Test
    fun turkishDates() {
        assertEquals("15 Kasım Pazar 14.30", Format.date(at(15, 14, 30), Language.TURKISH, zone))
        assertEquals("09.05", Format.time(at(14, 9, 5), Language.TURKISH, zone))
    }

    @Test
    fun lists() {
        assertEquals("A", Format.list(listOf("A"), Language.ENGLISH))
        assertEquals("A and B", Format.list(listOf("A", "B"), Language.ENGLISH))
        assertEquals("A, B and C", Format.list(listOf("A", "B", "C"), Language.ENGLISH))
        assertEquals("A ve B", Format.list(listOf("A", "B"), Language.TURKISH))
        assertEquals("A, B ve C", Format.list(listOf("A", "B", "C"), Language.TURKISH))
    }

    @Test
    fun defaultLanguageIsEnglish() {
        assertEquals(Language.ENGLISH, Language.DEFAULT)
        assertEquals(Language.ENGLISH, Language.fromCode(null))
        assertEquals(Language.ENGLISH, Language.fromCode("fr"))
        assertEquals(Language.TURKISH, Language.fromCode("tr"))
    }
}
