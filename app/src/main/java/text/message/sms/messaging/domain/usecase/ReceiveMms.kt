package text.message.sms.messaging.domain.usecase

import androidx.room.withTransaction
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.domain.repository.IncomingMessageSource
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Handles a WAP push notification for an inbound MMS. The platform writes the message and its
 * parts into the Telephony provider; this pulls the resulting row into the local cache.
 */
class ReceiveMms @Inject constructor(
    private val database: MessagingDatabase,
    private val incomingMessageSource: IncomingMessageSource,
    private val messageRepository: MessageRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
    private val incomingMessageNotifier: IncomingMessageNotifier,
) : UseCase {

    suspend operator fun invoke(providerUri: String): Message? {
        val pending = incomingMessageSource.readMessage(providerUri) ?: return null
        val sender = pending.address
        if (sender != null && blockedNumberRepository.isBlocked(sender)) return null

        // See ReceiveSms for why this runs after the transaction commits and only for a
        // genuinely new (non-duplicate) row.
        val inserted = database.withTransaction {
            messageRepository.insertIncoming(pending)
        }
        if (inserted != null) incomingMessageNotifier.notify(inserted)
        return inserted
    }
}
