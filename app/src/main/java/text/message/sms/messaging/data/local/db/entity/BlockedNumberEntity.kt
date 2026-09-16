package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import text.message.sms.messaging.domain.model.BlockReason

/** A number whose messages never reach the inbox. */
@Entity(
    tableName = "blocked_numbers",
    indices = [Index(value = ["normalized_address"], unique = true)],
)
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "normalized_address")
    val normalizedAddress: String,

    @ColumnInfo(name = "reason")
    val reason: BlockReason = BlockReason.MANUAL,

    @ColumnInfo(name = "blocked_at")
    val blockedAtMillis: Long = 0L,
)
