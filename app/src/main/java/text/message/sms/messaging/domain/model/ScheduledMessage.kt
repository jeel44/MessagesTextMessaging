package text.message.sms.messaging.domain.model

/**
 * A message with a row in `scheduled_messages`, plus when it's due. The row lives until the
 * carrier accepts the send ([text.message.sms.messaging.domain.usecase.MarkSent]), so a scheduled
 * message that failed or was missed keeps it too -- that's what lets the thread and the
 * Scheduled list still offer Send now / Cancel for it.
 */
data class ScheduledMessage(val message: Message, val sendAtMillis: Long) {

    val status: ScheduledStatus
        get() = when (message.folder) {
            MessageFolder.QUEUED -> ScheduledStatus.PENDING
            MessageFolder.FAILED -> ScheduledStatus.FAILED
            else -> ScheduledStatus.SENDING
        }
}

enum class ScheduledStatus {
    /** Waiting for its time. Editable. */
    PENDING,

    /** Claimed and handed to the radio; waiting on the sent broadcast. */
    SENDING,

    /** Failed or missed (more than [SchedulingRules.OVERDUE_LIMIT_MILLIS] late) -- never retried
     * on its own; the user can Send now or Cancel. */
    FAILED,
}

/** The limits Scheduled messages are held to (see `ScheduleMessage`/`SendScheduledMessage`). */
object SchedulingRules {
    /** A send time must be at least this far ahead when it's chosen. */
    const val MIN_LEAD_MILLIS: Long = 60_000L

    /** At most this many messages may be waiting to send at once. */
    const val MAX_PENDING: Int = 50

    /** A message whose worker runs more than this long after its time is not sent ("missed"). */
    const val OVERDUE_LIMIT_MILLIS: Long = 60 * 60 * 1000L
}
