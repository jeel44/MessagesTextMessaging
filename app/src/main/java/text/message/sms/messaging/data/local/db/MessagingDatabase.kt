package text.message.sms.messaging.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import text.message.sms.messaging.data.local.db.converter.MessagingConverters
import text.message.sms.messaging.data.local.db.dao.AttachmentDao
import text.message.sms.messaging.data.local.db.dao.BlockedNumberDao
import text.message.sms.messaging.data.local.db.dao.ContactDao
import text.message.sms.messaging.data.local.db.dao.ContactGroupDao
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.db.dao.ScheduledMessageDao
import text.message.sms.messaging.data.local.db.dao.SyncStateDao
import text.message.sms.messaging.data.local.db.entity.AttachmentEntity
import text.message.sms.messaging.data.local.db.entity.BlockedNumberEntity
import text.message.sms.messaging.data.local.db.entity.ContactEntity
import text.message.sms.messaging.data.local.db.entity.ContactGroupEntity
import text.message.sms.messaging.data.local.db.entity.ContactGroupMemberEntity
import text.message.sms.messaging.data.local.db.entity.ContactNumberEntity
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.MessageEntity
import text.message.sms.messaging.data.local.db.entity.RecipientEntity
import text.message.sms.messaging.data.local.db.entity.ScheduledMessageEntity
import text.message.sms.messaging.data.local.db.entity.SyncStateEntity

/**
 * Local cache of the system Telephony and Contacts providers, plus the app-only state
 * (archive, pin, mute, block) that those providers have nowhere to store.
 *
 * The providers remain the source of truth; this database exists so the UI can observe
 * messages as a [kotlinx.coroutines.flow.Flow] instead of polling a cursor.
 */
@Database(
    entities = [
        ConversationEntity::class,
        RecipientEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ContactEntity::class,
        ContactNumberEntity::class,
        ContactGroupEntity::class,
        ContactGroupMemberEntity::class,
        BlockedNumberEntity::class,
        SyncStateEntity::class,
        ScheduledMessageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(MessagingConverters::class)
abstract class MessagingDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun contactDao(): ContactDao

    abstract fun contactGroupDao(): ContactGroupDao

    abstract fun blockedNumberDao(): BlockedNumberDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun scheduledMessageDao(): ScheduledMessageDao

    companion object {
        const val NAME: String = "messaging.db"
    }
}
