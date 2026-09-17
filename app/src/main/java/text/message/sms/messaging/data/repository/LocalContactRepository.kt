package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.data.local.db.dao.ContactDao
import text.message.sms.messaging.data.local.db.dao.ContactGroupDao
import text.message.sms.messaging.data.local.provider.ContactProviderGateway
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.data.mapper.toDomainGroups
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.ContactGroup
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed mirror of the system contacts provider. */
@Singleton
class LocalContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactGroupDao: ContactGroupDao,
    private val contactProviderGateway: ContactProviderGateway,
) : ContactRepository {

    override fun observeAll(): Flow<List<Contact>> =
        contactDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun findByAddress(address: String): Contact? =
        contactDao.findByNormalizedAddress(PhoneNumbers.normalize(address))?.toDomain()

    override fun search(query: String): Flow<List<Contact>> =
        contactDao.search(query).map { rows -> rows.map { it.toDomain() } }

    override fun observeGroups(): Flow<List<ContactGroup>> =
        contactGroupDao.observeGroupRows().map { rows -> rows.toDomainGroups() }

    override suspend fun refreshFromProvider() {
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
    }
}
