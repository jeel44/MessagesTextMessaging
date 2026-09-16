package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Moves the given threads out of the inbox and into the archive. */
class MarkArchived @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        conversationRepository.setArchived(threadIds, archived = true)
    }
}
