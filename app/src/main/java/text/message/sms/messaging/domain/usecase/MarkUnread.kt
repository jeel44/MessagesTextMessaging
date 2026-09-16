package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Marks every message in the given threads as unread. */
class MarkUnread @Inject constructor(
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        messageRepository.setRead(threadIds, read = false)
    }
}
