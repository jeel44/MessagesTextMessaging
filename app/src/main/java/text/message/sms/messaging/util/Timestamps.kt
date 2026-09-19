package text.message.sms.messaging.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Date helpers shared by the conversation list, search, and the chat timeline. */
object Timestamps {

    fun isToday(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(epochMillis, zone) == LocalDate.now(zone)

    fun isSameDay(
        firstEpochMillis: Long,
        secondEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = toLocalDate(firstEpochMillis, zone) == toLocalDate(secondEpochMillis, zone)

    private fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}

/**
 * Shared day/time formatting for every place the app shows a message's or conversation's
 * timestamp as a short, human label -- [listLabel] (Home's row date column, Search's result date
 * column), [separatorLabel] (Chat's date-separator text above a run of messages), and
 * [dayHeaderLabel] (a whole-day section header, e.g. [text.message.sms.messaging.ui.screens
 * .archived.ArchivedScreen]'s grouping). None of the three ever prints the words "Today" or
 * "Yesterday": a same-day timestamp reads as a plain time ([listLabel]) or a weekday name
 * ([separatorLabel], [dayHeaderLabel]) instead, so a translation never has to carry those two
 * words as a special case on top of the weekday/month names [java.time.DayOfWeek]/[java.time.Month]
 * already localize.
 *
 * Every entry point takes [clock] (defaulting to the real wall clock) rather than reading
 * [LocalDate.now] internally, so a fixed [Clock] can pin "today" in a test without any Android
 * framework dependency -- see `TimestampsTest`.
 */
object RelativeDateFormatter {

    private val shortMonthDay = DateTimeFormatter.ofPattern("d MMM")
    private val shortMonthDayYear = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val time12Hour = DateTimeFormatter.ofPattern("h:mm a")
    private val time24Hour = DateTimeFormatter.ofPattern("HH:mm")

    /** Last 7 calendar days (today plus the 6 before it), inclusive on both ends -- shared by
     * every bucket below that treats "today" and "the rest of the last week" as one weekday-name
     * range. */
    private const val RecentDayRangeEnd = 6L

    /**
     * Home's conversation-row date column and Search's result date column: a bare time for
     * today ("3:16 AM"), a short weekday name for yesterday through 6 days ago ("Fri"), "12 Sep"
     * for anything older this year, "12 Sep 2025" for a previous year.
     */
    fun listLabel(
        timestampMillis: Long,
        is24Hour: Boolean,
        clock: Clock = Clock.systemDefaultZone(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val zoned = Instant.ofEpochMilli(timestampMillis).atZone(clock.zone)
        val today = LocalDate.now(clock)
        val daysBetween = ChronoUnit.DAYS.between(zoned.toLocalDate(), today)
        return when {
            daysBetween == 0L -> zoned.format(timeFormatter(is24Hour, locale))
            daysBetween in 1L..RecentDayRangeEnd -> zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            zoned.year == today.year -> zoned.format(shortMonthDay.withLocale(locale))
            else -> zoned.format(shortMonthDayYear.withLocale(locale))
        }
    }

    /**
     * Chat's date-separator text above a run of messages sharing a day: weekday name plus time
     * for today through 6 days ago ("Sat 3:16 AM", "Fri 5:20 PM"), "12 Sep, 3:16 AM" for anything
     * older this year, "12 Sep 2025, 3:16 AM" for a previous year.
     */
    fun separatorLabel(
        timestampMillis: Long,
        is24Hour: Boolean,
        clock: Clock = Clock.systemDefaultZone(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val zoned = Instant.ofEpochMilli(timestampMillis).atZone(clock.zone)
        val today = LocalDate.now(clock)
        val daysBetween = ChronoUnit.DAYS.between(zoned.toLocalDate(), today)
        val time = zoned.format(timeFormatter(is24Hour, locale))
        return when {
            daysBetween in 0L..RecentDayRangeEnd -> "${zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} $time"
            zoned.year == today.year -> "${zoned.format(shortMonthDay.withLocale(locale))}, $time"
            else -> "${zoned.format(shortMonthDayYear.withLocale(locale))}, $time"
        }
    }

    /**
     * A whole-day section header (e.g. [text.message.sms.messaging.ui.screens.archived
     * .ArchivedScreen]'s date grouping): a short weekday name for today through 6 days ago, "12
     * Sep" for anything older this year, "12 Sep 2025" for a previous year. Never a bare time,
     * unlike [listLabel] -- a section header spans a whole calendar day, not one specific moment,
     * so "today" falls into the same weekday-name bucket as the rest of the last week rather than
     * getting a time it doesn't have.
     */
    fun dayHeaderLabel(
        date: LocalDate,
        clock: Clock = Clock.systemDefaultZone(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val today = LocalDate.now(clock)
        val daysBetween = ChronoUnit.DAYS.between(date, today)
        return when {
            daysBetween in 0L..RecentDayRangeEnd -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            date.year == today.year -> shortMonthDay.withLocale(locale).format(date)
            else -> shortMonthDayYear.withLocale(locale).format(date)
        }
    }

    private fun timeFormatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter =
        (if (is24Hour) time24Hour else time12Hour).withLocale(locale)
}
