package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A message thread. The primary key is the system provider's thread id so the cache and the
 * Telephony provider stay addressable by the same value.
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["last_message_at"]),
        Index(value = ["is_archived", "is_blocked"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "snippet")
    val snippet: String = "",

    @ColumnInfo(name = "last_message_at")
    val lastMessageAtMillis: Long = 0L,

    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = false,

    @ColumnInfo(name = "is_muted")
    val isMuted: Boolean = false,

    @ColumnInfo(name = "draft")
    val draft: String? = null,
)
