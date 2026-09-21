package text.message.sms.messaging.domain.usecase

import androidx.room.withTransaction
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.domain.repository.IncomingMessageSource
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Handles a WAP push notification for an inbound MMS. The platform writes the message and its
 * parts into the Telephony provider; this pulls the resulting row into the local cache, blocked
 * sender or not -- see [BlockedSenderGate]'s doc for why a blocked sender's message is stored
 * rather than dropped.
 */
class ReceiveMms @Inject constructor(
    private val database: MessagingDatabase,
    private val incomingMessageSource: IncomingMessageSource,
    private val messageRepository: MessageRepository,
    private val blockedSenderGate: BlockedSenderGate,
    private val incomingMessageNotifier: IncomingMessageNotifier,
) : UseCase {

    suspend operator fun invoke(providerUri: String): Message? {
        val pending = incomingMessageSource.readMessage(providerUri) ?: return null
        val blocked = blockedSenderGate.isBlocked(pending.address)

        // See ReceiveSms for why this runs after the transaction commits and only for a
        // genuinely new, non-blocked (non-duplicate) row.
        val inserted = database.withTransaction {
            val message = messageRepository.insertIncoming(pending)
            if (blocked && message != null) blockedSenderGate.markThreadsBlocked(listOf(pending.threadId))
            message
        }
        if (inserted != null && !blocked) incomingMessageNotifier.notify(inserted)
        return inserted
    }
}
