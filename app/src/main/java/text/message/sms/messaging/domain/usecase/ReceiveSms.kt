package text.message.sms.messaging.domain.usecase

import androidx.room.withTransaction
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Handles an inbound SMS delivered to the app as the default handler: resolves the thread and
 * stores the message, blocked sender or not -- see [BlockedSenderGate]'s doc for why a blocked
 * sender's message is stored rather than dropped.
 */
class ReceiveSms @Inject constructor(
    private val database: MessagingDatabase,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val blockedSenderGate: BlockedSenderGate,
    private val incomingMessageNotifier: IncomingMessageNotifier,
) : UseCase {

    suspend operator fun invoke(params: Params): Message? {
        val blocked = blockedSenderGate.isBlocked(params.address)

        // The notifier runs after the transaction commits, and only for a genuinely new,
        // non-blocked row (insertIncoming returns null for a duplicate) -- never inside it, since
        // it does its own suspending reads (conversation lookup, active-notification lookup) that
        // have no business holding a database transaction open.
        val inserted = database.withTransaction {
            val threadId = conversationRepository.resolveThreadId(setOf(params.address))
            val message = messageRepository.insertIncoming(params.toMessage(threadId))
            if (blocked && message != null) blockedSenderGate.markThreadsBlocked(listOf(threadId))
            message
        }
        if (inserted != null && !blocked) incomingMessageNotifier.notify(inserted)
        return inserted
    }

    data class Params(
        val address: String,
        val body: String,
        val sentAtMillis: Long,
        val receivedAtMillis: Long,
        val subscriptionId: Int,
    )

    private fun Params.toMessage(threadId: Long) = Message(
        id = 0L,
        threadId = threadId,
        providerId = 0L,
        channel = text.message.sms.messaging.domain.model.MessageChannel.SMS,
        folder = text.message.sms.messaging.domain.model.MessageFolder.INBOX,
        deliveryState = text.message.sms.messaging.domain.model.DeliveryState.NONE,
        address = address,
        body = body,
        subject = null,
        sentAtMillis = sentAtMillis,
        receivedAtMillis = receivedAtMillis,
        isRead = false,
        isSeen = false,
        subscriptionId = subscriptionId,
        errorCode = 0,
    )
}
