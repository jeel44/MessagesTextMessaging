package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import text.message.sms.messaging.data.local.db.entity.SyncStateEntity

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE id = :id")
    suspend fun get(id: String = SyncStateEntity.SINGLETON_ID): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)
}
