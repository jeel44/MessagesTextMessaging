package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Adds every address in the given threads to the block list and hides the threads. */
class MarkBlocked @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>, reason: BlockReason = BlockReason.MANUAL) {
        val addresses = threadIds
            .mapNotNull { conversationRepository.findByThreadId(it) }
            .flatMap { conversation -> conversation.recipients.map { it.address } }
            .toSet()

        blockedNumberRepository.block(addresses, reason)
        conversationRepository.setBlocked(threadIds, blocked = true)
    }
}
