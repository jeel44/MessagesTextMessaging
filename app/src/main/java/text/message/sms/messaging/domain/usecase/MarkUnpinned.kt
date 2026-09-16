package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Removes the given threads from the pinned section of the conversation list. */
class MarkUnpinned @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        conversationRepository.setPinned(threadIds, pinned = false)
    }
}
