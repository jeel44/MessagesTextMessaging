package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.BlockedNumberEntity

@Dao
interface BlockedNumberDao {

    @Query("SELECT * FROM blocked_numbers ORDER BY blocked_at DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>

    /** Every row, read once -- the list is small, and matching happens in memory (see
     * [text.message.sms.messaging.util.PhoneNumbers.isSameSender]). */
    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAll(): List<BlockedNumberEntity>

    @Upsert
    suspend fun upsertAll(numbers: List<BlockedNumberEntity>)

    @Query("DELETE FROM blocked_numbers WHERE normalized_address IN (:normalizedAddresses)")
    suspend fun delete(normalizedAddresses: Collection<String>)

    @Query("DELETE FROM blocked_numbers")
    suspend fun clear()
}
