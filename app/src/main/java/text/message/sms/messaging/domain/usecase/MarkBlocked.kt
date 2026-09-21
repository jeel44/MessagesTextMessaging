package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** What [MarkBlocked] actually did -- [blockedThreadIds] got blocked, [skippedGroupThreadIds] were
 * left alone because they're group threads (see [MarkBlocked]'s doc). A caller with a
 * multi-thread selection uses [skippedGroupThreadIds] to tell the user some of their selection was
 * skipped, rather than silently blocking fewer threads than asked. */
data class BlockOutcome(
    val blockedThreadIds: Set<Long>,
    val skippedGroupThreadIds: Set<Long>,
)

/**
 * Adds every address in the given non-group threads to the block list and hides those threads.
 *
 * A group thread is never blocked: [BlockedNumberRepository] blocks by a single normalized
 * address, and blocking every participant's address in a group MMS would silently block those
 * people's other, unrelated 1:1 threads too -- there's no per-thread block, only per-address. Any
 * [threadIds] entry that resolves to a group conversation is simply left alone (not blocked, not
 * an error) and reported back via [BlockOutcome.skippedGroupThreadIds].
 */
class MarkBlocked @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>, reason: BlockReason = BlockReason.MANUAL): BlockOutcome {
        val conversations = threadIds.mapNotNull { conversationRepository.findByThreadId(it) }
        val (groupConversations, blockable) = conversations.partition { it.isGroup }

        val addresses = blockable.flatMap { conversation -> conversation.recipients.map { it.address } }.toSet()
        if (addresses.isNotEmpty()) blockedNumberRepository.block(addresses, reason)

        val blockedThreadIds = blockable.map { it.threadId }
        if (blockedThreadIds.isNotEmpty()) conversationRepository.setBlocked(blockedThreadIds, blocked = true)

        return BlockOutcome(
            blockedThreadIds = blockedThreadIds.toSet(),
            skippedGroupThreadIds = groupConversations.map { it.threadId }.toSet(),
        )
    }
}
