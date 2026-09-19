package text.message.sms.messaging.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

class RelativeDateFormatterTest {

    private val zone = ZoneOffset.UTC
    private val locale = Locale.US

    /** 2026-09-19 -- a Saturday; picked only as a fixed anchor, never asserted on by name. */
    private val today = LocalDate.of(2026, 9, 19)

    private fun clockAt(date: LocalDate, time: LocalTime = LocalTime.of(15, 30)): Clock =
        Clock.fixed(LocalDateTime.of(date, time).atZone(zone).toInstant(), zone)

    private fun millisAt(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    private fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    @Test
    fun `listLabel shows a bare time for today, respecting the 12-24 hour flag`() {
        val clock = clockAt(today)
        val millis = millisAt(today, LocalTime.of(15, 30))
        assertEquals("3:30 PM", RelativeDateFormatter.listLabel(millis, is24Hour = false, clock = clock, locale = locale))
        assertEquals("15:30", RelativeDateFormatter.listLabel(millis, is24Hour = true, clock = clock, locale = locale))
    }

    @Test
    fun `listLabel shows a short weekday name for yesterday`() {
        val clock = clockAt(today)
        val date = today.minusDays(1)
        val millis = millisAt(date, LocalTime.of(9, 0))
        assertEquals(weekday(date), RelativeDateFormatter.listLabel(millis, is24Hour = false, clock = clock, locale = locale))
    }

    @Test
    fun `listLabel shows a short weekday name 3 days ago`() {
        val clock = clockAt(today)
        val date = today.minusDays(3)
        val millis = millisAt(date, LocalTime.of(9, 0))
        assertEquals(weekday(date), RelativeDateFormatter.listLabel(millis, is24Hour = false, clock = clock, locale = locale))
    }

    @Test
    fun `listLabel still shows a weekday name 6 days ago, the edge of the recent-day range`() {
        val clock = clockAt(today)
        val date = today.minusDays(6)
        val millis = millisAt(date, LocalTime.of(9, 0))
        assertEquals(weekday(date), RelativeDateFormatter.listLabel(millis, is24Hour = false, clock = clock, locale = locale))
    }

    @Test
    fun `listLabel switches to a day-month date 7 days ago, one past the recent-day range`() {
        val clock = clockAt(today)
        val millis = millisAt(today.minusDays(7), LocalTime.of(9, 0))
        assertEquals("12 Sep", RelativeDateFormatter.listLabel(millis, is24Hour = false, clock = clock, locale = locale))
    }

    @Test
    fun `listLabel includes the year for a previous year`() {
        val clock = clockAt(today)
        val millis = millisAt(LocalDate.of(2025, 9, 12), LocalTime.of(9, 0))
        assertEquals("12 Sep 2025", RelativeDateFormatter.listLabel(millis, is24Hour = false, clock = clock, locale = locale))
    }

    @Test
    fun `listLabel resolves the midnight boundary -- 11-59 PM is still yesterday, 12-01 AM is a new day`() {
        // "Now" is 2026-09-19 00:05 -- a message at 2026-09-18 23:59 is one calendar day back
        // (yesterday), but a message at 2026-09-19 00:01 is today, even though only six minutes
        // separate the two real-world instants.
        val clock = clockAt(today, LocalTime.of(0, 5))
        val lateLastNight = millisAt(today.minusDays(1), LocalTime.of(23, 59))
        val earlyThisMorning = millisAt(today, LocalTime.of(0, 1))

        assertEquals(
            weekday(today.minusDays(1)),
            RelativeDateFormatter.listLabel(lateLastNight, is24Hour = false, clock = clock, locale = locale),
        )
        assertEquals(
            "12:01 AM",
            RelativeDateFormatter.listLabel(earlyThisMorning, is24Hour = false, clock = clock, locale = locale),
        )
    }

    @Test
    fun `separatorLabel combines weekday and time through the recent-day range`() {
        val clock = clockAt(today)
        val millisToday = millisAt(today, LocalTime.of(15, 30))
        assertEquals(
            "${weekday(today)} 3:30 PM",
            RelativeDateFormatter.separatorLabel(millisToday, is24Hour = false, clock = clock, locale = locale),
        )

        val sixDaysAgo = today.minusDays(6)
        val millisSixDaysAgo = millisAt(sixDaysAgo, LocalTime.of(17, 20))
        assertEquals(
            "${weekday(sixDaysAgo)} 5:20 PM",
            RelativeDateFormatter.separatorLabel(millisSixDaysAgo, is24Hour = false, clock = clock, locale = locale),
        )
    }

    @Test
    fun `separatorLabel appends a day-month date once past the recent-day range`() {
        val clock = clockAt(today)
        val millis = millisAt(today.minusDays(7), LocalTime.of(3, 16))
        assertEquals("12 Sep, 3:16 AM", RelativeDateFormatter.separatorLabel(millis, is24Hour = false, clock = clock, locale = locale))
    }

    @Test
    fun `separatorLabel includes the year for a previous year`() {
        val clock = clockAt(today)
        val millis = millisAt(LocalDate.of(2025, 9, 12), LocalTime.of(3, 16))
        assertEquals("12 Sep 2025, 3:16 AM", RelativeDateFormatter.separatorLabel(millis, is24Hour = false, clock = clock, locale = locale))
    }

    @Test
    fun `dayHeaderLabel never shows a time, even for today`() {
        val clock = clockAt(today)
        assertEquals(weekday(today), RelativeDateFormatter.dayHeaderLabel(today, clock = clock, locale = locale))
    }

    @Test
    fun `dayHeaderLabel switches to a day-month date 7 days ago and includes the year for a previous year`() {
        val clock = clockAt(today)
        assertEquals("12 Sep", RelativeDateFormatter.dayHeaderLabel(today.minusDays(7), clock = clock, locale = locale))
        assertEquals("12 Sep 2025", RelativeDateFormatter.dayHeaderLabel(LocalDate.of(2025, 9, 12), clock = clock, locale = locale))
    }
}
