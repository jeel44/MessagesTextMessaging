package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.ContactEntity
import text.message.sms.messaging.data.local.db.entity.ContactNumberEntity
import text.message.sms.messaging.data.local.db.entity.ContactWithNumbers

@Dao
interface ContactDao {

    @Transaction
    @Query("SELECT * FROM contacts ORDER BY is_starred DESC, display_name ASC")
    fun observeAll(): Flow<List<ContactWithNumbers>>

    @Transaction
    @Query(
        """
        SELECT c.* FROM contacts c
        INNER JOIN contact_numbers n ON n.contact_id = c.id
        WHERE n.normalized_address = :normalizedAddress
        LIMIT 1
        """
    )
    suspend fun findByNormalizedAddress(normalizedAddress: String): ContactWithNumbers?

    @Transaction
    @Query(
        """
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN contact_numbers n ON n.contact_id = c.id
        WHERE c.display_name LIKE '%' || :query || '%' OR n.address LIKE '%' || :query || '%'
        ORDER BY c.display_name ASC
        """
    )
    fun search(query: String): Flow<List<ContactWithNumbers>>

    @Upsert
    suspend fun upsertContacts(contacts: List<ContactEntity>)

    @Upsert
    suspend fun upsertNumbers(numbers: List<ContactNumberEntity>)

    @Query("DELETE FROM contacts")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(contacts: List<ContactEntity>, numbers: List<ContactNumberEntity>) {
        clear()
        upsertContacts(contacts)
        upsertNumbers(numbers)
    }
}
