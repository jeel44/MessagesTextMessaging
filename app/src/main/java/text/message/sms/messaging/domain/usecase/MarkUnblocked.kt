package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Removes every address in the given threads from the block list. */
class MarkUnblocked @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        val addresses = threadIds
            .mapNotNull { conversationRepository.findByThreadId(it) }
            .flatMap { conversation -> conversation.recipients.map { it.address } }
            .toSet()

        blockedNumberRepository.unblock(addresses)
        conversationRepository.setBlocked(threadIds, blocked = false)
    }
}
