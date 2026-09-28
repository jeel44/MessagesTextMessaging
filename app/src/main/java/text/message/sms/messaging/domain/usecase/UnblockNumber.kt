package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.first
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject

/** What [UnblockNumber] did -- enough for [ReblockNumber] to undo it exactly. */
data class UnblockedNumber(
    val number: BlockedNumber,
    val restoredThreadIds: Set<Long>,
)

/**
 * The number-keyed counterpart to [MarkUnblocked] (which starts from thread ids), for the Blocked
 * list: removes [BlockedNumber.address] from the block list, then clears `is_blocked` on every
 * blocked 1:1 thread with that number so it returns to the inbox.
 *
 * Threads match on [PhoneNumbers.normalize] -- the same exact match the block list itself and
 * [BlockedSenderGate] use -- rather than the looser [PhoneNumbers.areEquivalent], so a thread is
 * only restored when its number is genuinely the one no longer blocked. Group threads are never
 * blocked in the first place (see [MarkBlocked]), so they're left alone here too.
 */
class UnblockNumber @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
) : UseCase {

    suspend operator fun invoke(number: BlockedNumber): UnblockedNumber {
        val normalized = PhoneNumbers.normalize(number.address)
        val threadIds = conversationRepository.getBlockedConversations().first()
            .filter { conversation ->
                !conversation.isGroup &&
                    conversation.recipients.any { PhoneNumbers.normalize(it.address) == normalized }
            }
            .map { it.threadId }

        blockedNumberRepository.unblock(listOf(number.address))
        if (threadIds.isNotEmpty()) conversationRepository.setBlocked(threadIds, blocked = false)

        return UnblockedNumber(number = number, restoredThreadIds = threadIds.toSet())
    }
}

/** Undoes an [UnblockNumber]: blocks the number again (with its original reason) and hides the
 * threads it had restored. */
class ReblockNumber @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
) : UseCase {

    suspend operator fun invoke(unblocked: UnblockedNumber) {
        blockedNumberRepository.block(listOf(unblocked.number.address), unblocked.number.reason)
        if (unblocked.restoredThreadIds.isNotEmpty()) {
            conversationRepository.setBlocked(unblocked.restoredThreadIds, blocked = true)
        }
    }
}
