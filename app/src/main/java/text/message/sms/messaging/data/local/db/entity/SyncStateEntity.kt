package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The incremental-sync high-water marks for [text.message.sms.messaging.data.repository.TelephonySyncRepository].
 * A single fixed-id row: there is one system Telephony provider, so there is only ever one
 * sync state.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = SINGLETON_ID,

    /** Newest `Telephony.Sms.DATE` (epoch millis) already pulled into the local cache. */
    @ColumnInfo(name = "last_sms_date_ms")
    val lastSmsDateMillis: Long = 0L,

    /** Newest `Telephony.Mms.DATE` (epoch seconds -- the MMS provider's native unit) already
     * pulled into the local cache. */
    @ColumnInfo(name = "last_mms_date_sec")
    val lastMmsDateSeconds: Long = 0L,

    @ColumnInfo(name = "last_full_sync_at")
    val lastFullSyncAtMillis: Long? = null,
) {
    companion object {
        const val SINGLETON_ID: String = "state"
    }
}
