package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Returns the given threads from the archive to the inbox. */
class MarkUnarchived @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        conversationRepository.setArchived(threadIds, archived = false)
    }
}
