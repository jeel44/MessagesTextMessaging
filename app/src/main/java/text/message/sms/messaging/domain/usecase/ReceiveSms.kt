package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Handles an inbound SMS delivered to the app as the default handler: resolves the thread,
 * drops the message if the sender is blocked, and stores it locally.
 */
class ReceiveSms @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
) : UseCase {

    suspend operator fun invoke(params: Params): Message? {
        if (blockedNumberRepository.isBlocked(params.address)) return null

        val threadId = conversationRepository.resolveThreadId(setOf(params.address))
        val stored = messageRepository.insertIncoming(params.toMessage(threadId))
        return stored
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
