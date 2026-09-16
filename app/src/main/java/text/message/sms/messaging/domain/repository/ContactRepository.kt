package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.Contact

/** Access to the locally cached mirror of the system contacts provider. */
interface ContactRepository {

    fun observeAll(): Flow<List<Contact>>

    suspend fun findByAddress(address: String): Contact?

    fun search(query: String): Flow<List<Contact>>

    /** Re-reads the system contacts provider into the local cache. */
    suspend fun refreshFromProvider()
}
