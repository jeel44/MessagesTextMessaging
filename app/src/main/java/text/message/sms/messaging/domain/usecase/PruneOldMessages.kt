package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Trims history older than the configured retention window. */
class PruneOldMessages @Inject constructor(
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(olderThanMillis: Long) {
        messageRepository.deleteOlderThan(olderThanMillis)
    }
}
