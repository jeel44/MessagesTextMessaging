package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.ConversationWithRecipients
import text.message.sms.messaging.data.local.db.entity.RecipientEntity

@Dao
interface ConversationDao {

    @Transaction
    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 0 AND is_blocked = 0 AND last_message_at > 0
        ORDER BY is_pinned DESC, last_message_at DESC
        """
    )
    fun observeInbox(): Flow<List<ConversationWithRecipients>>

    @Transaction
    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 1 AND is_blocked = 0
        ORDER BY last_message_at DESC
        """
    )
    fun observeArchived(): Flow<List<ConversationWithRecipients>>

    @Transaction
    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun observeByThreadId(threadId: Long): Flow<ConversationWithRecipients?>

    @Transaction
    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    suspend fun findByThreadId(threadId: Long): ConversationWithRecipients?

    /** Every thread with its participants, regardless of archived/blocked/pinned state -- unlike
     * [observeInbox]/[observeArchived], which filter for the UI. Used by
     * [text.message.sms.messaging.data.repository.LocalBackupRepository] to recover each
     * message's thread addresses for a backup. */
    @Transaction
    @Query("SELECT * FROM conversations")
    suspend fun getAllWithRecipients(): List<ConversationWithRecipients>

    /**
     * Matches a thread's last-message snippet, any participant's raw address, or -- via a left
     * join to the contacts cache through [RecipientEntity.contactLookupKey] -- a saved contact's
     * display name, so searching "Mom" finds her thread even though no message or address
     * literally contains that text.
     */
    @Transaction
    @Query(
        """
        SELECT DISTINCT c.* FROM conversations c
        INNER JOIN recipients r ON r.thread_id = c.thread_id
        LEFT JOIN contacts ct ON ct.lookup_key = r.contact_lookup_key
        WHERE c.snippet LIKE '%' || :query || '%'
           OR r.address LIKE '%' || :query || '%'
           OR ct.display_name LIKE '%' || :query || '%'
        ORDER BY c.last_message_at DESC
        """
    )
    fun search(query: String): Flow<List<ConversationWithRecipients>>

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    /** Inserts a placeholder row for a brand-new thread only -- a no-op when [conversation]'s
     * thread id already has a row. Unlike [upsert], this never overwrites an existing
     * conversation's snippet/timestamp/flags/draft with the blank defaults of a freshly
     * constructed [ConversationEntity]. Used by
     * [text.message.sms.messaging.data.repository.LocalConversationRepository.resolveThreadId],
     * which is called just to find-or-create a thread id (e.g. tapping a contact in New Message,
     * before any message is sent) and must never disturb a conversation that already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversationIfAbsent(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipients(recipients: List<RecipientEntity>)

    /** A thread's already-stored recipient addresses, exactly as stored -- used by
     * [text.message.sms.messaging.data.repository.LocalConversationRepository.resolveThreadId]
     * to check a newly-resolved address against via [text.message.sms.messaging.util.PhoneNumbers
     * .addressesNotAlreadyPresent] before inserting it, so the same person addressed in two
     * different formats (e.g. a contact-picker's ContactsContract number vs. the raw Telephony
     * address an earlier message used) reuses the existing row instead of adding a second one. */
    @Query("SELECT address FROM recipients WHERE thread_id = :threadId")
    suspend fun getRecipientAddresses(threadId: Long): List<String>

    @Query("UPDATE conversations SET is_archived = :archived WHERE thread_id IN (:threadIds)")
    suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean)

    @Query("UPDATE conversations SET is_pinned = :pinned WHERE thread_id IN (:threadIds)")
    suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean)

    @Query("UPDATE conversations SET is_muted = :muted WHERE thread_id IN (:threadIds)")
    suspend fun setMuted(threadIds: Collection<Long>, muted: Boolean)

    @Query("UPDATE conversations SET is_blocked = :blocked WHERE thread_id IN (:threadIds)")
    suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean)

    @Query("UPDATE conversations SET draft = :draft WHERE thread_id = :threadId")
    suspend fun setDraft(threadId: Long, draft: String?)

    /** Remembers (or clears, when [slot] is null) which SIM slot sends this thread's messages
     * under the "Ask every time" send preference -- see
     * [text.message.sms.messaging.domain.model.Conversation.subscriptionSlot]. */
    @Query("UPDATE conversations SET subscription_slot = :slot WHERE thread_id = :threadId")
    suspend fun setSubscriptionSlot(threadId: Long, slot: Int?)

    @Query("UPDATE conversations SET unread_count = :count WHERE thread_id = :threadId")
    suspend fun setUnreadCount(threadId: Long, count: Int)

    @Query("DELETE FROM conversations WHERE thread_id IN (:threadIds)")
    suspend fun delete(threadIds: Collection<Long>)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}
