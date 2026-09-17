package text.message.sms.messaging.data.repository

import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import text.message.sms.messaging.data.local.db.dao.SyncStateDao
import text.message.sms.messaging.data.local.db.entity.SyncStateEntity
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.SyncProgress
import text.message.sms.messaging.domain.repository.SyncRepository
import text.message.sms.messaging.service.DefaultSmsAppGuard
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the system Telephony provider into the local cache.
 *
 * Incremental by construction: [SyncStateEntity] tracks the newest SMS/MMS timestamp already
 * pulled in, and every sync -- whether the app-open full walk in `syncAll` or the single-row
 * pull `syncMessage` runs after a broadcast -- only ever asks the provider for rows newer than
 * that watermark. A row already present locally (matched by provider id + channel, the same
 * unique key [text.message.sms.messaging.data.local.db.dao.MessageDao] enforces) is skipped
 * rather than re-inserted, so re-running a sync is always safe.
 */
@Singleton
class TelephonySyncRepository @Inject constructor(
    private val smsProviderGateway: SmsProviderGateway,
    private val mmsProviderGateway: MmsProviderGateway,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
    private val syncStateDao: SyncStateDao,
    private val defaultSmsAppGuard: DefaultSmsAppGuard,
) : SyncRepository {

    private val progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)

    override fun observeProgress(): Flow<SyncProgress> = progress.asStateFlow()

    override suspend fun syncAll(): Unit = withContext(Dispatchers.IO) {
        // A non-default app can still read Telephony.Sms/Mms on many OS versions, but the read
        // can be partial or simply wrong (no guaranteed visibility into the full provider), and
        // writing sync-state/local-cache rows off that data would poison the cache once the role
        // is actually granted. Bail out before touching either provider.
        if (!defaultSmsAppGuard.isDefault) {
            progress.value = SyncProgress.NotDefaultApp
            return@withContext
        }

        try {
            val state = syncStateDao.get() ?: SyncStateEntity()
            val smsMessages = smsProviderGateway.querySince(state.lastSmsDateMillis)
            val mmsIds = mmsProviderGateway.queryIdsSince(state.lastMmsDateSeconds)
            val total = smsMessages.size + mmsIds.size

            if (total == 0) {
                syncStateDao.upsert(state.copy(lastFullSyncAtMillis = System.currentTimeMillis()))
                progress.value = SyncProgress.Idle
                return@withContext
            }

            progress.value = SyncProgress.Running(0, total)
            var completed = 0
            var failedCount = 0
            var newestSmsMillis = state.lastSmsDateMillis
            var newestMmsSeconds = state.lastMmsDateSeconds
            // Once a row in a channel fails, that channel's watermark stops advancing -- even
            // past later rows that succeed -- so the failed row (and everything after it) is
            // included again on the next sync instead of being silently skipped forever once the
            // watermark moves beyond its timestamp. Already-synced rows in that re-included range
            // are cheap no-ops (see syncSms/syncMms's own findByProviderId dedup check).
            var smsWatermarkStalled = false
            var mmsWatermarkStalled = false

            smsMessages.forEach { message ->
                try {
                    syncSms(message)
                    if (!smsWatermarkStalled) {
                        newestSmsMillis = maxOf(newestSmsMillis, message.receivedAtMillis)
                    }
                } catch (error: Exception) {
                    failedCount++
                    smsWatermarkStalled = true
                    Log.w(TAG, "Skipping malformed SMS row (provider id ${message.providerId})", error)
                }
                completed++
                progress.value = SyncProgress.Running(completed, total)
            }

            mmsIds.forEach { providerId ->
                try {
                    val receivedAtMillis = syncMms(providerId)
                    if (receivedAtMillis != null && !mmsWatermarkStalled) {
                        newestMmsSeconds = maxOf(newestMmsSeconds, receivedAtMillis / MILLIS_PER_SECOND)
                    }
                } catch (error: Exception) {
                    failedCount++
                    mmsWatermarkStalled = true
                    Log.w(TAG, "Skipping malformed MMS row (provider id $providerId)", error)
                }
                completed++
                progress.value = SyncProgress.Running(completed, total)
            }

            syncStateDao.upsert(
                SyncStateEntity(
                    lastSmsDateMillis = newestSmsMillis,
                    lastMmsDateSeconds = newestMmsSeconds,
                    lastFullSyncAtMillis = System.currentTimeMillis(),
                ),
            )
            progress.value = if (failedCount > 0) {
                SyncProgress.Failed("$failedCount of $total message(s) failed to sync", failedCount)
            } else {
                SyncProgress.Idle
            }
        } catch (error: Exception) {
            progress.value = SyncProgress.Failed(error.message ?: "Sync failed")
        }
    }

    override suspend fun syncMessage(providerUri: String): Unit = withContext(Dispatchers.IO) {
        val uri = providerUri.toUri()
        when (uri.authority) {
            "sms" -> {
                val providerId = uri.lastPathSegment?.toLongOrNull() ?: return@withContext
                smsProviderGateway.querySince(0L)
                    .lastOrNull { it.providerId == providerId }
                    ?.let { syncSms(it) }
            }

            "mms" -> {
                val providerId = uri.lastPathSegment?.toLongOrNull() ?: return@withContext
                syncMms(providerId)
            }
        }
    }

    override suspend fun lastSyncAtMillis(): Long? = syncStateDao.get()?.lastFullSyncAtMillis

    private suspend fun syncSms(message: Message) {
        if (messageRepository.findByProviderId(message.providerId, MessageChannel.SMS) != null) return
        if (message.address != null && blockedNumberRepository.isBlocked(message.address)) return

        val addresses = setOfNotNull(message.address)
        if (addresses.isNotEmpty()) conversationRepository.resolveThreadId(addresses)
        messageRepository.insertIncoming(message)
    }

    /** Returns the message's received timestamp (for the sync watermark) whether or not it was
     * newly inserted, so an already-cached row still lets the watermark move past it. */
    private suspend fun syncMms(providerId: Long): Long? {
        val message = mmsProviderGateway.readMessage("content://mms/$providerId") ?: return null
        val alreadyCached = messageRepository.findByProviderId(providerId, MessageChannel.MMS) != null

        if (!alreadyCached) {
            val recipients = (listOfNotNull(message.address) + mmsProviderGateway.readRecipients(providerId)).toSet()
            if (recipients.isNotEmpty()) conversationRepository.resolveThreadId(recipients)

            val blocked = message.address != null && blockedNumberRepository.isBlocked(message.address)
            if (!blocked) messageRepository.insertIncoming(message)
        }

        return message.receivedAtMillis
    }

    private companion object {
        const val TAG = "TelephonySyncRepository"
        const val MILLIS_PER_SECOND = 1_000L
    }
}
