package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Deletes individual messages while leaving the thread in place. */
class DeleteMessages @Inject constructor(
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(messageIds: Collection<Long>) {
        messageRepository.delete(messageIds)
    }
}
