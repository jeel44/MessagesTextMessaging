package text.message.sms.messaging.domain.repository

import text.message.sms.messaging.domain.model.Message

/**
 * Port for surfacing a just-received message to the user. Implemented in the service layer
 * (see [text.message.sms.messaging.service.DefaultIncomingMessageNotifier]) as a real system
 * notification; kept as an interface -- like every other port here -- so
 * [text.message.sms.messaging.domain.usecase.ReceiveSms]/
 * [text.message.sms.messaging.domain.usecase.ReceiveMms] can be unit-tested against a fake
 * instead of the real implementation, which touches `Context`/`NotificationManagerCompat`.
 */
interface IncomingMessageNotifier {

    /** Called only after a successful, non-duplicate insert. */
    suspend fun notify(message: Message)

    /** Called when [threadId]'s chat screen is opened/resumed, or its notification's "Mark as
     * read" action is tapped. */
    fun cancel(threadId: Long)
}
