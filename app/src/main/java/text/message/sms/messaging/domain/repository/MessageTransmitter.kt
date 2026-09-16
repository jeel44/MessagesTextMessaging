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

    /** Drops a message that is still waiting out its scheduled-send delay. */
    suspend fun cancelPending(messageId: Long)

    /**
     * Registers [message] (already persisted, folder [text.message.sms.messaging.domain.model.MessageFolder.QUEUED])
     * to be handed to [transmit] at [sendAtMillis].
     *
     * Takes the already-created message rather than its raw fields (addresses/body/subscription)
     * so the same id can later be passed to [cancelPending] -- the original `Unit`-returning
     * signature gave a caller no way to reference a specific scheduled item afterwards, which
     * is a genuine gap this replaces; see `domain.usecase.ScheduleMessage`.
     */
    suspend fun schedule(message: Message, sendAtMillis: Long)

    /** Sends every scheduled message whose time has passed but that, for some reason, no
     * scheduler job is going to pick up (e.g. app data was cleared and reinstalled). Under
     * normal operation the scheduler drives this itself; see the WorkManager note on the
     * implementation for why. */
    suspend fun dispatchDue(nowMillis: Long)

    /** Re-verifies every still-pending scheduled send has a live scheduler job, re-registering
     * one for any that don't. */
    suspend fun rearmAlarms()
}
