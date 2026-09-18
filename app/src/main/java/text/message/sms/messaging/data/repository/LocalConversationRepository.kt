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
     * Contacts keyed by [PhoneNumbers.comparableSuffix], not [PhoneNumbers.normalize].
     *
     * A [RecipientEntity.address] is *never* copied from a [Contact]'s own saved number except
     * the one time a brand-new thread is created by picking a contact in
     * [text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen] -- and even then it's
     * the exact same string both sides of the lookup would use, so that path alone could never
     * mismatch. Every other recipient row -- which is to say, the address on any thread that has
     * ever received a message, i.e. nearly every real two-way personal conversation -- comes from
     * [text.message.sms.messaging.data.repository.TelephonySyncRepository] reading
     * `Telephony.Sms.ADDRESS` / the MMS addr table, a format entirely under the carrier/OEM
     * telephony stack's control and never reconciled against Contacts afterward (
     * [text.message.sms.messaging.domain.usecase.SyncContacts] only ever touches the `contacts`/
     * `contact_numbers` tables, never `recipients`). Two independent systems -- the carrier's
     * delivery report and whatever the user (or their account's contacts sync adapter) typed into
     * Contacts -- routinely disagree on whether a country code is present, so an exact-string
     * lookup keyed by [PhoneNumbers.normalize] alone regularly misses a real match: e.g. a contact
     * saved as "9876543210" never matching a recipient row Telephony reported as "919876543210" or
     * "+919876543210" for an incoming message from that same person. This can only ever affect a
     * real phone-number address -- a promotional/bank alphanumeric sender id is never a contact
     * match candidate in the first place -- so in practice it is specifically personal,
     * person-to-person conversations that silently fall back to showing a bare number.
     */
    private fun contactsByComparableSuffix(): Flow<Map<String, Contact>> =
        contactDao.observeAll().map { rows ->
            val contacts = rows.map { it.toDomain() }
            buildMap {
                for (contact in contacts) {
                    for (number in contact.numbers) {
                        // A digit-free "number" (blank, or someone stored junk) would produce an
                        // empty comparableSuffix -- skip it rather than let it become a key, since
                        // an unrelated alphanumeric sender id (which also comparableSuffix-es to
                        // "", having no digits at all) would then incorrectly resolve to this
                        // contact. PhoneNumbers.areEquivalent already guards the same case (see
                        // its "rejects two blank addresses" test); this mirrors that guard for a
                        // map key instead of a pairwise comparison.
                        val suffix = PhoneNumbers.comparableSuffix(number)
                        if (suffix.isNotEmpty()) put(suffix, contact)
                    }
                }
            }
        }
}
