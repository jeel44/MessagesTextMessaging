package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Deletes whole threads, along with every message they contain. */
class DeleteConversation @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        conversationRepository.delete(threadIds)
    }
}
