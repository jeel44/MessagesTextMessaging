package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.first
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Clears the pending-notification state across every thread, e.g. when the app is opened. */
class MarkAllSeen @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke() {
        val threadIds = conversationRepository.observeInbox().first().map { it.threadId }
        messageRepository.setSeen(threadIds)
    }
}
