package text.message.sms.messaging.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import text.message.sms.messaging.data.local.db.dao.ContactDao
import text.message.sms.messaging.data.local.db.dao.ContactGroupDao
import text.message.sms.messaging.data.local.provider.ContactProviderGateway
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.data.mapper.toDomainGroups
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.ContactGroup
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ContactsState
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed mirror of the system contacts provider. */
@Singleton
class LocalContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactGroupDao: ContactGroupDao,
    private val contactProviderGateway: ContactProviderGateway,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ContactRepository {

    /** Flips true once [refreshFromProvider] has completed at least once THIS process -- the
     * signal [contactsState] needs to tell "never synced yet" (rows==[] because nothing has run)
     * apart from "synced, and this device genuinely has zero contacts" (rows==[] but a refresh
     * already completed). Room's own `contacts` table read alone can't tell those apart: a fresh
     * install's table is empty before the first provider scan too. */
    private val hasRefreshedOnce = MutableStateFlow(false)

    /** Eagerly hot in [applicationScope] from the moment this singleton is constructed (in
     * practice, [text.message.sms.messaging.MessagingApplication]'s own Hilt field injection,
     * before any Activity exists) -- never restarted per screen, so a picker opened well after
     * launch reads an already-resolved value instantly instead of re-subscribing to Room. */
    override val contactsState: StateFlow<ContactsState> = combine(
        contactDao.observeAll(),
        hasRefreshedOnce,
    ) { rows, refreshed ->
        if (rows.isNotEmpty() || refreshed) {
            ContactsState.Loaded(rows.map { it.toDomain() })
        } else {
            ContactsState.Loading
        }
    }.stateIn(applicationScope, SharingStarted.Eagerly, ContactsState.Loading)

    override suspend fun findByAddress(address: String): Contact? =
        contactDao.findByNormalizedAddress(PhoneNumbers.normalize(address))?.toDomain()

    override fun search(query: String): Flow<List<Contact>> =
        contactDao.search(query).map { rows -> rows.map { it.toDomain() } }

    override fun observeGroups(): Flow<List<ContactGroup>> =
        contactGroupDao.observeGroupRows().map { rows -> rows.toDomainGroups() }

    override suspend fun refreshFromProvider(): Unit = withContext(Dispatchers.IO) {
        // ContactProviderGateway's reads are plain blocking ContentResolver queries, not
        // suspend functions -- without this withContext they'd run on whatever dispatcher the
        // caller happens to be on (NewMessageViewModel launches this from viewModelScope, i.e.
        // Dispatchers.Main), blocking the UI thread for however long the device's contact list
        // takes to scan.
        val snapshot = contactProviderGateway.readContacts()
        contactDao.replaceAll(snapshot.contacts, snapshot.numbers)

        // Group membership rows are keyed by contact lookup_key (see ContactGroupMemberEntity's
        // class doc), and ContactGroupDao.observeGroupRows() INNER JOINs that key against the
        // contacts table -- so this must run after the contacts replaceAll above. Nothing
        // enforces that ordering at the schema level (there's no FK from group_members to
        // contacts, only to contact_groups), so getting it backwards wouldn't fail loudly; it
        // would just leave every group looking empty until the contacts table is repopulated.
        val groupSnapshot = contactProviderGateway.readGroups()
        contactGroupDao.replaceAll(groupSnapshot.groups, groupSnapshot.members)
        hasRefreshedOnce.value = true
    }
}
