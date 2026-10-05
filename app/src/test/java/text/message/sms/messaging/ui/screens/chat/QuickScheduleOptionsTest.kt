package text.message.sms.messaging.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickScheduleOptionsTest {

    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `in one hour is to the minute, tomorrow is 9 AM local`() {
        val now = ZonedDateTime.of(2026, 10, 5, 22, 41, 37, 0, zone)

        val options = quickScheduleOptions(now.toInstant().toEpochMilli(), zone)

        assertEquals(ZonedDateTime.of(2026, 10, 5, 23, 41, 0, 0, zone).toInstant().toEpochMilli(), options.inOneHourMillis)
        assertEquals(ZonedDateTime.of(2026, 10, 6, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), options.tomorrowMorningMillis)
    }

    @Test
    fun `tomorrow 9 AM across a daylight-saving change is still 9 AM local`() {
        // Clocks go back on 25 Oct 2026 in Berlin.
        val now = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, zone)

        val options = quickScheduleOptions(now.toInstant().toEpochMilli(), zone)

        assertEquals(ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), options.tomorrowMorningMillis)
    }
}
