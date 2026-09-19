package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder

/** Read and write access to individual SMS/MMS entries. */
interface MessageRepository {

    fun observeThread(threadId: Long): Flow<List<Message>>

    /** Live view over just the most recent [limit] messages of the thread, oldest first -- lets
     * [text.message.sms.messaging.ui.screens.chat.ChatViewModel] show the first frame without
     * paying for the whole thread's history. Default implementation falls back to trimming
     * [observeThread]'s full result, so a fake/test repository need not implement this separately;
     * [text.message.sms.messaging.data.repository.LocalMessageRepository] overrides it with a
     * SQL-limited query instead of loading everything first. */
    fun observeThreadPage(threadId: Long, limit: Int): Flow<List<Message>> =
        observeThread(threadId).map { it.takeLast(limit) }

    suspend fun findById(id: Long): Message?

    /** [channel] is required because the same provider row id space is not shared between the
     * SMS and MMS tables -- SMS row 12 and MMS row 12 are unrelated messages. */
    suspend fun findByProviderId(providerId: Long, channel: MessageChannel): Message?

    /**
     * Persists an outgoing message locally (including [attachmentUris], resolved to real
     * [text.message.sms.messaging.domain.model.Attachment] rows) and returns the stored row.
     *
     * [folder] defaults to [MessageFolder.OUTBOX] -- ready to hand to the radio now. Scheduled
     * sends pass [MessageFolder.QUEUED] instead, the same folder the system SMS provider itself
     * uses for a message waiting to go out, so the message is visibly "queued" rather than
     * "sending" until its scheduled time arrives.
     */
    suspend fun insertOutgoing(
        threadId: Long,
        address: String,
        body: String,
        subscriptionId: Int,
        attachmentUris: List<String> = emptyList(),
        folder: MessageFolder = MessageFolder.OUTBOX,
    ): Message

    /** Persists an inbound message, including any [Message.attachments] it already carries
     * (remapped onto the row's real id), and returns the stored row. [notifyConversation]
     * controls whether the conversation's counters (snippet/last-message-time/unread-count) are
     * updated as part of this call, or deferred to a caller doing a batched update. */
    suspend fun insertIncoming(message: Message, notifyConversation: Boolean = true): Message

    /** Bulk form of [insertIncoming] for a sync pass: every entry in [messages] is written inside
     * a single transaction instead of one commit per row, and none of them are written back to
     * the system provider (they're already sync sourced from it -- see
     * [text.message.sms.messaging.data.repository.TelephonySyncRepository]). The default
     * implementation just calls [insertIncoming] once per message, so a fake/test repository need
     * not override this; [text.message.sms.messaging.data.repository.LocalMessageRepository]
     * overrides it with a real single-transaction batch insert. */
    suspend fun insertIncomingBatch(messages: List<Message>): List<Message> =
        messages.map { insertIncoming(it, notifyConversation = false) }

    /** Which of [providerIds] (all the same [channel]) already have a cached row, in one call --
     * lets a bulk sync skip already-synced rows without a [findByProviderId] round trip per row.
     * The default implementation falls back to one [findByProviderId] call per id, so a fake/test
     * repository need not override this. */
    suspend fun findExistingProviderIds(providerIds: Collection<Long>, channel: MessageChannel): Set<Long> =
        providerIds.filterTo(mutableSetOf()) { findByProviderId(it, channel) != null }

    suspend fun setDeliveryState(messageId: Long, state: DeliveryState, errorCode: Int = 0)

    suspend fun setRead(threadIds: Collection<Long>, read: Boolean)

    suspend fun setSeen(threadIds: Collection<Long>)

    /** How many messages [threadId] has left -- used after [delete] to tell whether the thread
     * is now empty. */
    suspend fun countForThread(threadId: Long): Int

    /** Every message id across [threadIds] -- used by
     * [text.message.sms.messaging.domain.usecase.DeleteConversation] to route each thread's
     * messages through [delete] (which handles Telephony-provider cleanup, not just the local
     * cache) before the now-empty conversation rows themselves are removed. Default implementation
     * falls back to [observeThread], so a fake/test repository need not override this;
     * [text.message.sms.messaging.data.repository.LocalMessageRepository] overrides it with a
     * real SQL IN-list query instead. */
    suspend fun findIdsForThreads(threadIds: Collection<Long>): List<Long> =
        threadIds.flatMap { threadId -> observeThread(threadId).first().map { it.id } }

    /** The newest remaining message in [threadId], or `null` if none -- used after [delete] to
     * recompute the conversation's snippet/last-message time. */
    suspend fun findLatestForThread(threadId: Long): Message?

    /** Deletes [messageIds] from the local cache, and -- for any row already written to the
     * system Telephony provider (a non-zero [Message.providerId]) -- from `content://sms`/
     * `content://mms` too, so an incremental sync never re-imports a message the user just
     * deleted. A row with no provider id yet (e.g. a still-queued local draft) is removed from
     * the cache only. */
    suspend fun delete(messageIds: Collection<Long>)

    suspend fun deleteOlderThan(timestampMillis: Long)

    fun search(query: String): Flow<List<Message>>

    /** True once at least one message has ever been cached locally. Used only to detect the
     * cache-empty-but-watermark-advanced state a corrupted or partially cleared local database
     * could leave behind -- see [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll]. */
    suspend fun hasAnyMessages(): Boolean
}
