package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Marks threads as seen without marking them read. "Seen" suppresses the notification;
 * "read" clears the unread badge.
 */
class MarkSeen @Inject constructor(
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        messageRepository.setSeen(threadIds)
    }
}
