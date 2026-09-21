package text.message.sms.messaging.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import text.message.sms.messaging.data.local.db.MIGRATION_1_2
import text.message.sms.messaging.data.local.db.MIGRATION_2_3
import text.message.sms.messaging.data.local.db.MIGRATION_3_4
import text.message.sms.messaging.data.local.db.MIGRATION_4_5
import text.message.sms.messaging.data.local.db.MIGRATION_5_6
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.data.local.db.dao.AttachmentDao
import text.message.sms.messaging.data.local.db.dao.BlockedNumberDao
import text.message.sms.messaging.data.local.db.dao.ContactDao
import text.message.sms.messaging.data.local.db.dao.ContactGroupDao
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.db.dao.ScheduledMessageDao
import text.message.sms.messaging.data.local.db.dao.SyncStateDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MessagingDatabase =
        Room.databaseBuilder(context, MessagingDatabase::class.java, MessagingDatabase.NAME)
            // Foreign keys drive the cascade from a deleted thread to its messages and parts.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideConversationDao(database: MessagingDatabase): ConversationDao =
        database.conversationDao()

    @Provides
    fun provideMessageDao(database: MessagingDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideAttachmentDao(database: MessagingDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideContactDao(database: MessagingDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideContactGroupDao(database: MessagingDatabase): ContactGroupDao =
        database.contactGroupDao()

    @Provides
    fun provideBlockedNumberDao(database: MessagingDatabase): BlockedNumberDao =
        database.blockedNumberDao()

    @Provides
    fun provideSyncStateDao(database: MessagingDatabase): SyncStateDao = database.syncStateDao()

    @Provides
    fun provideScheduledMessageDao(database: MessagingDatabase): ScheduledMessageDao =
        database.scheduledMessageDao()
}
