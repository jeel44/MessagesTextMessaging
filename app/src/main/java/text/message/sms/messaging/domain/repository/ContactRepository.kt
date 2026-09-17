package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.ContactGroup

/** Access to the locally cached mirror of the system contacts provider. */
interface ContactRepository {

    fun observeAll(): Flow<List<Contact>>

    suspend fun findByAddress(address: String): Contact?

    fun search(query: String): Flow<List<Contact>>

    /** Real, user-created contact groups ("Family", "Work") and their members -- for a
     * group-MMS recipient picker, e.g. "add everyone from Family". */
    fun observeGroups(): Flow<List<ContactGroup>>

    /** Re-reads the system contacts provider into the local cache. */
    suspend fun refreshFromProvider()
}
