package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place "is this inbound message from a blocked sender, and if so what happens to it" is
 * decided -- shared by every path that can insert a fresh incoming row: [ReceiveSms]/[ReceiveMms]'s
 * live receivers, and [text.message.sms.messaging.data.repository.TelephonySyncRepository]'s three
 * historical-backfill call sites (`prepareAndInsertSmsChunk`, `readAndPrepareMms`, the legacy
 * `syncSms`/`syncMms`).
 *
 * A blocked sender's message is still stored -- never dropped -- so that unblocking later restores
 * the whole conversation, messages included; the thread it resolved to is marked [markThreadsBlocked]
 * so [text.message.sms.messaging.data.local.db.dao.ConversationDao.observeInbox] keeps excluding it,
 * and callers are expected to skip posting a notification when [isBlocked] is true. Before this
 * existed, every one of those five call sites independently decided to skip the insert entirely for
 * a blocked sender, which meant unblocking had nothing left to restore.
 */
@Singleton
class BlockedSenderGate @Inject constructor(
    private val blockedNumberRepository: BlockedNumberRepository,
    private val conversationRepository: ConversationRepository,
) {

    suspend fun isBlocked(address: String?): Boolean =
        address != null && blockedNumberRepository.isBlocked(address)

    /** Marks every thread in [threadIds] blocked -- idempotent (a thread already blocked, still
     * receiving more messages from the same sender, is simply set blocked again), and safe to call
     * for a thread [ConversationRepository.resolveThreadId] just created moments ago, so a brand
     * new conversation from a blocked sender never has a chance to render in the main list even for
     * one frame. */
    suspend fun markThreadsBlocked(threadIds: Collection<Long>) {
        if (threadIds.isNotEmpty()) conversationRepository.setBlocked(threadIds, blocked = true)
    }
}
