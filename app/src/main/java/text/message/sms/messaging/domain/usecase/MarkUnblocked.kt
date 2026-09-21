package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** What [MarkUnblocked] actually did -- see [BlockOutcome], the [MarkBlocked] counterpart this
 * mirrors. */
data class UnblockOutcome(
    val unblockedThreadIds: Set<Long>,
    val skippedGroupThreadIds: Set<Long>,
)

/**
 * Removes every address in the given non-group threads from the block list.
 *
 * Guarded the same way as [MarkBlocked], for the same reason -- a group thread can never actually
 * have been blocked (see [MarkBlocked]'s doc), so this is defensive symmetry rather than a path
 * expected to fire in practice: the UI never offers Unblock for a group thread in the first place.
 */
class MarkUnblocked @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>): UnblockOutcome {
        val conversations = threadIds.mapNotNull { conversationRepository.findByThreadId(it) }
        val (groupConversations, unblockable) = conversations.partition { it.isGroup }

        val addresses = unblockable.flatMap { conversation -> conversation.recipients.map { it.address } }.toSet()
        if (addresses.isNotEmpty()) blockedNumberRepository.unblock(addresses)

        val unblockedThreadIds = unblockable.map { it.threadId }
        if (unblockedThreadIds.isNotEmpty()) conversationRepository.setBlocked(unblockedThreadIds, blocked = false)

        return UnblockOutcome(
            unblockedThreadIds = unblockedThreadIds.toSet(),
            skippedGroupThreadIds = groupConversations.map { it.threadId }.toSet(),
        )
    }
}
