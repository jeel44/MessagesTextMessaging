package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Silences or un-silences notifications for the given threads. */
class MarkMuted @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>, muted: Boolean) {
        conversationRepository.setMuted(threadIds, muted)
    }
}
