package text.message.sms.messaging.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Date helpers shared by the conversation list and the chat timeline. */
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
