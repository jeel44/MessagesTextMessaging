package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.data.local.db.dao.ContactDao
import text.message.sms.messaging.data.local.provider.ContactProviderGateway
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed mirror of the system contacts provider. */
@Singleton
class LocalContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactProviderGateway: ContactProviderGateway,
) : ContactRepository {

    override fun observeAll(): Flow<List<Contact>> =
        contactDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun findByAddress(address: String): Contact? =
        contactDao.findByNormalizedAddress(PhoneNumbers.normalize(address))?.toDomain()

    override fun search(query: String): Flow<List<Contact>> =
        contactDao.search(query).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshFromProvider() {
        val snapshot = contactProviderGateway.readContacts()
        contactDao.replaceAll(snapshot.contacts, snapshot.numbers)
    }
}
