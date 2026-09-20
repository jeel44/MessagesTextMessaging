package text.message.sms.messaging.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.TelephonyMessageTransmitter
import text.message.sms.messaging.data.repository.LocalAttachmentRepository
import text.message.sms.messaging.data.repository.LocalBackupRepository
import text.message.sms.messaging.data.repository.LocalBlockedNumberRepository
import text.message.sms.messaging.data.repository.LocalContactRepository
import text.message.sms.messaging.data.repository.LocalConversationRepository
import text.message.sms.messaging.data.repository.LocalMessageRepository
import text.message.sms.messaging.data.repository.TelephonySyncRepository
import text.message.sms.messaging.domain.repository.AttachmentRepository
import text.message.sms.messaging.domain.repository.BackupRepository
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.domain.repository.IncomingMessageSource
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.repository.SyncRepository
import text.message.sms.messaging.service.DefaultIncomingMessageNotifier
import javax.inject.Singleton

/** Binds every domain interface to its data-layer implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: LocalConversationRepository,
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: LocalMessageRepository): MessageRepository

    @Binds
    @Singleton
    abstract fun bindAttachmentRepository(impl: LocalAttachmentRepository): AttachmentRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: LocalContactRepository): ContactRepository

    @Binds
    @Singleton
    abstract fun bindBlockedNumberRepository(
        impl: LocalBlockedNumberRepository,
    ): BlockedNumberRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: TelephonySyncRepository): SyncRepository

    @Binds
    @Singleton
    abstract fun bindMessageTransmitter(impl: TelephonyMessageTransmitter): MessageTransmitter

    @Binds
    @Singleton
    abstract fun bindIncomingMessageSource(impl: MmsProviderGateway): IncomingMessageSource

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: LocalBackupRepository): BackupRepository

    @Binds
    @Singleton
    abstract fun bindIncomingMessageNotifier(
        impl: DefaultIncomingMessageNotifier,
    ): IncomingMessageNotifier
}
