package text.message.sms.messaging.domain.repository

import text.message.sms.messaging.domain.model.Message

/**
 * Port for handing outgoing messages to the platform radio. Implemented in the data layer so
 * the use cases never touch `SmsManager` directly.
 */
interface MessageTransmitter {

    /** Sends [message] now: SMS is split into parts if the body exceeds one segment, MMS is
     * built into a PDU and handed to the carrier's MMSC via `SmsManager`. */
    suspend fun transmit(message: Message)

    /** Drops a message's scheduled send: cancels its job and deletes its schedule row. */
    suspend fun cancelPending(messageId: Long)

    /**
     * Registers [message] (already persisted, folder [text.message.sms.messaging.domain.model.MessageFolder.QUEUED])
     * to be handed to [transmit] at [sendAtMillis]: writes (or, for an edit, updates in place) the
     * schedule row first, then replaces the job -- so a job can never run without its row.
     *
     * Takes the already-created message rather than its raw fields (addresses/body/subscription)
     * so the same id can later be passed to [cancelPending] -- the original `Unit`-returning
     * signature gave a caller no way to reference a specific scheduled item afterwards, which
     * is a genuine gap this replaces; see `domain.usecase.ScheduleMessage`.
     */
    suspend fun schedule(message: Message, sendAtMillis: Long)

    /** Re-verifies every still-pending (QUEUED) scheduled send has a live scheduler job,
     * re-registering one for any that don't. Never sends anything itself. */
    suspend fun rearmAlarms()
}
