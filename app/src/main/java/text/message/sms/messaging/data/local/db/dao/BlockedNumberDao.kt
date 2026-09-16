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

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE normalized_address = :normalizedAddress)")
    suspend fun isBlocked(normalizedAddress: String): Boolean

    @Upsert
    suspend fun upsertAll(numbers: List<BlockedNumberEntity>)

    @Query("DELETE FROM blocked_numbers WHERE normalized_address IN (:normalizedAddresses)")
    suspend fun delete(normalizedAddresses: Collection<String>)

    @Query("DELETE FROM blocked_numbers")
    suspend fun clear()
}
