package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Pins the given threads to the top of the conversation list. */
class MarkPinned @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        conversationRepository.setPinned(threadIds, pinned = true)
    }
}
