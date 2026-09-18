package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.data.local.db.dao.ContactDao
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.ConversationWithRecipients
import text.message.sms.messaging.data.local.db.entity.RecipientEntity
import text.message.sms.messaging.data.local.provider.TelephonyThreadResolver
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [ConversationRepository]; thread identity still comes from the platform. */
@Singleton
class LocalConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val threadResolver: TelephonyThreadResolver,
    private val messageDao: MessageDao,
) : ConversationRepository {

    override fun observeInbox(): Flow<List<Conversation>> =
        conversationDao.observeInbox().withContacts()

    override fun observeArchived(): Flow<List<Conversation>> =
        conversationDao.observeArchived().withContacts()

    override fun observeConversation(threadId: Long): Flow<Conversation?> =
        combine(
            conversationDao.observeByThreadId(threadId),
            contactsByComparableSuffix(),
        ) { conversation, contacts -> conversation?.toDomain(contacts) }

    override suspend fun findByThreadId(threadId: Long): Conversation? =
        conversationDao.findByThreadId(threadId)?.toDomain()

    override suspend fun resolveThreadId(addresses: Set<String>): Long {
        val threadId = threadResolver.resolve(addresses)
        conversationDao.upsert(ConversationEntity(threadId = threadId))
        conversationDao.insertRecipients(
            addresses.map { address -> RecipientEntity(threadId = threadId, address = address) },
        )
        return threadId
    }

    override suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean) {
        conversationDao.setArchived(threadIds, archived)
    }

    override suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean) {
        conversationDao.setPinned(threadIds, pinned)
    }

    override suspend fun setMuted(threadIds: Collection<Long>, muted: Boolean) {
        conversationDao.setMuted(threadIds, muted)
    }

    override suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean) {
        conversationDao.setBlocked(threadIds, blocked)
    }

    override suspend fun saveDraft(threadId: Long, draft: String?) {
        conversationDao.setDraft(threadId, draft)
    }

    override suspend fun refreshCounters(threadId: Long, snippet: String, lastMessageAtMillis: Long) {
        val existing = conversationDao.findByThreadId(threadId)?.conversation ?: return
        val isNewer = lastMessageAtMillis >= existing.lastMessageAtMillis
        conversationDao.upsert(
            existing.copy(
                snippet = if (isNewer) snippet else existing.snippet,
                lastMessageAtMillis = if (isNewer) lastMessageAtMillis else existing.lastMessageAtMillis,
                unreadCount = messageDao.countUnread(threadId),
            ),
        )
    }

    override suspend fun delete(threadIds: Collection<Long>) {
        conversationDao.delete(threadIds)
    }

    override fun search(query: String): Flow<List<Conversation>> =
        conversationDao.search(query).withContacts()

    private fun Flow<List<ConversationWithRecipients>>.withContacts(): Flow<List<Conversation>> =
        combine(contactsByComparableSuffix()) { conversations, contacts ->
            conversations.map { it.toDomain(contacts) }
        }

    /**
     * Contacts keyed by [PhoneNumbers.comparableSuffix], not [PhoneNumbers.normalize] -- a
     * [RecipientEntity.address] and a [Contact]'s own saved number can come from entirely
     * different sources (the system Telephony provider for an address synced from an incoming
     * message, versus [text.message.sms.messaging.data.local.provider.ContactProviderGateway] for
     * one picked from Contacts when starting a new outgoing thread) and legitimately differ in
     * whether a country code is present even though they reach the same handset. An exact-string
     * lookup keyed by [PhoneNumbers.normalize] alone would miss that pairing -- e.g. a contact
     * saved as "5550101234" never matching a recipient row stored as "+15550101234" -- which is
     * exactly why a newly-created outgoing thread (recipient address fresh from Contacts) could
     * fail to resolve a name that an existing, previously-synced-from-Telephony thread for the
     * same contact happened to already show correctly.
     */
    private fun contactsByComparableSuffix(): Flow<Map<String, Contact>> =
        contactDao.observeAll().map { rows ->
            val contacts = rows.map { it.toDomain() }
            buildMap {
                for (contact in contacts) {
                    for (number in contact.numbers) {
                        put(PhoneNumbers.comparableSuffix(number), contact)
                    }
                }
            }
        }
}
