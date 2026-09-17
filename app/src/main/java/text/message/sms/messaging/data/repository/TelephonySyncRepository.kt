package text.message.sms.messaging.data.repository

import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
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
        // is actually granted. Bail out before touching either provider. The role and the
        // dangerous runtime permissions are separate Android systems -- isDefault can be true
        // while READ_SMS etc. are still denied (e.g. right after role grant, before the
        // permission dialog resolves), so both must hold before querying the provider.
        if (!defaultSmsAppGuard.isDefault || !defaultSmsAppGuard.hasCoreSmsPermissions) {
            progress.value = SyncProgress.NotDefaultApp
            return@withContext
        }

        try {
            val persistedState = syncStateDao.get() ?: SyncStateEntity()
            // Normally sync_state and messages/conversations live in the same Room database and
            // are always cleared together (an app-data clear, an uninstall) -- there is no
            // ordinary path where the watermark survives but the cached messages don't. This
            // guards the one abnormal path that could still produce that split (a corrupted
            // table, a bug, or a future partial-wipe feature): if the cache is empty but the
            // watermark isn't at its zero default, trusting the watermark would mean only pulling
            // rows newer than it and silently never backfilling everything below it. Resetting to
            // a fresh SyncStateEntity() here forces the full backfill a truly empty cache needs;
            // an already-consistent empty-cache/zero-watermark state (a real first run) is a
            // no-op through this branch.
            val cacheIsEmpty = !messageRepository.hasAnyMessages()
            val state = if (cacheIsEmpty && persistedState != SyncStateEntity()) {
                Log.w(TAG, "Local message cache is empty but sync watermark is not -- resetting to full backfill")
                SyncStateEntity()
            } else {
                persistedState
            }
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
            // The first row's exception, so the aggregate SyncProgress.Failed below can surface
            // the real cause -- e.g. "IllegalArgumentException: column 'error_code' does not
            // exist" -- rather than just a count. Later failures are still logged individually
            // (below) with their own full stack trace; only the first is promoted to the visible
            // progress state, since on real devices repeated failures in one sync are almost
            // always the same root cause (e.g. one OEM-missing column hit by every row).
            var firstFailure: Exception? = null
            var newestSmsMillis = state.lastSmsDateMillis
            var newestMmsSeconds = state.lastMmsDateSeconds
            // Once a row in a channel fails, that channel's watermark stops advancing -- even
            // past later rows that succeed -- so the failed row (and everything after it) is
            // included again on the next sync instead of being silently skipped forever once the
            // watermark moves beyond its timestamp. Already-synced rows in that re-included range
            // are cheap no-ops (see syncSms/syncMms's own findByProviderId dedup check).
            var smsWatermarkStalled = false
            var mmsWatermarkStalled = false
            // Per-thread snippet/last-message-time, tracking only the latest-received row per
            // thread across the whole batch -- upserted once after both loops below finish
            // instead of once per message, so the conversation list's live Flow re-sorts and
            // re-renders once per sync pass rather than flickering through every intermediate
            // state while a large catch-up sync is still running.
            val conversationUpdates = mutableMapOf<Long, Message>()
            // Telephony.Threads.getOrCreateThreadId is a real ContentResolver round-trip, not a
            // local lookup -- caching it per address set for the duration of this one sync pass
            // avoids repeating that call for every message in a thread that has many.
            val threadIdCache = mutableMapOf<Set<String>, Long>()

            smsMessages.forEach { message ->
                try {
                    syncSms(message, threadIdCache)?.let { recordConversationUpdate(conversationUpdates, it) }
                    if (!smsWatermarkStalled) {
                        newestSmsMillis = maxOf(newestSmsMillis, message.receivedAtMillis)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failedCount++
                    smsWatermarkStalled = true
                    if (firstFailure == null) firstFailure = error
                    Log.w(TAG, "Skipping malformed SMS row (provider id ${message.providerId})", error)
                }
                completed++
                progress.value = SyncProgress.Running(completed, total)
            }

            mmsIds.forEach { providerId ->
                try {
                    val result = syncMms(providerId, threadIdCache)
                    result.insertedMessage?.let { recordConversationUpdate(conversationUpdates, it) }
                    if (result.receivedAtMillis != null && !mmsWatermarkStalled) {
                        newestMmsSeconds = maxOf(newestMmsSeconds, result.receivedAtMillis / MILLIS_PER_SECOND)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failedCount++
                    mmsWatermarkStalled = true
                    if (firstFailure == null) firstFailure = error
                    Log.w(TAG, "Skipping malformed MMS row (provider id $providerId)", error)
                }
                completed++
                progress.value = SyncProgress.Running(completed, total)
            }

            conversationUpdates.forEach { (threadId, message) ->
                conversationRepository.refreshCounters(threadId, message.body, message.receivedAtMillis)
            }

            syncStateDao.upsert(
                SyncStateEntity(
                    lastSmsDateMillis = newestSmsMillis,
                    lastMmsDateSeconds = newestMmsSeconds,
                    lastFullSyncAtMillis = System.currentTimeMillis(),
                ),
            )
            progress.value = if (failedCount > 0) {
                SyncProgress.Failed(
                    "$failedCount of $total message(s) failed to sync: ${firstFailure.describe()}",
                    failedCount,
                )
            } else {
                SyncProgress.Idle
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "syncAll failed", error)
            progress.value = SyncProgress.Failed(error.describe())
        }
    }

    override suspend fun syncMessage(providerUri: String): Unit = withContext(Dispatchers.IO) {
        // A single-row pull, so there's no batch to amortize a cache over -- an empty map here
        // just means the one resolveThreadId call below always misses, same as before.
        val threadIdCache = mutableMapOf<Set<String>, Long>()
        val uri = providerUri.toUri()
        when (uri.authority) {
            "sms" -> {
                val providerId = uri.lastPathSegment?.toLongOrNull() ?: return@withContext
                smsProviderGateway.querySince(0L)
                    .lastOrNull { it.providerId == providerId }
                    ?.let { syncSms(it, threadIdCache) }
            }

            "mms" -> {
                val providerId = uri.lastPathSegment?.toLongOrNull() ?: return@withContext
                syncMms(providerId, threadIdCache)
            }
        }
    }

    override suspend fun lastSyncAtMillis(): Long? = syncStateDao.get()?.lastFullSyncAtMillis

    /**
     * [message]'s own `threadId` is whatever raw value was already stamped on the Telephony
     * cursor row -- assigned at some point in the past by whichever component wrote it (the
     * platform itself, or a previous default SMS app), using its own address resolution at that
     * time. [ConversationRepository.resolveThreadId] is a separate, independent lookup: it asks
     * the platform "what thread do these addresses belong to *right now*" and upserts a
     * [text.message.sms.messaging.data.local.db.entity.ConversationEntity] row keyed by
     * *that* answer -- it is the only place a ConversationEntity ever gets created. Nothing
     * guarantees the two agree (address formatting, or simply a thread that predates this app
     * holding the default-SMS-app role, can resolve differently), so [message] must always be
     * rewritten to carry the resolved id before insert -- otherwise `messages.thread_id`'s
     * foreign key has nothing to point at and every insert fails.
     */
    private suspend fun syncSms(message: Message, threadIdCache: MutableMap<Set<String>, Long>): Message? {
        if (messageRepository.findByProviderId(message.providerId, MessageChannel.SMS) != null) return null
        if (message.address != null && blockedNumberRepository.isBlocked(message.address)) return null

        val addresses = setOfNotNull(message.address)
        if (addresses.isEmpty()) {
            // No address means there is nothing to resolve a thread from, and therefore no
            // ConversationEntity to insert this row against -- see the class doc above. Falling
            // through to insert with message's own (unresolved) threadId would hit the exact
            // foreign-key failure this fix exists to prevent.
            Log.w(TAG, "Skipping SMS provider id ${message.providerId}: no address, cannot resolve a thread")
            return null
        }

        val resolvedThreadId = resolveThreadIdCached(threadIdCache, addresses)
        return messageRepository.insertIncoming(message.copy(threadId = resolvedThreadId), notifyConversation = false)
    }

    /**
     * [MmsSyncResult.receivedAtMillis] is the message's received timestamp (for the sync
     * watermark) whether or not it was newly inserted, so an already-cached row still lets the
     * watermark move past it. [MmsSyncResult.insertedMessage] is non-null only when a row was
     * actually inserted, for the caller's batched conversation-counters update. Applies the same
     * resolved-thread-id rewrite as [syncSms], and for the same reason -- see its doc.
     */
    private suspend fun syncMms(providerId: Long, threadIdCache: MutableMap<Set<String>, Long>): MmsSyncResult {
        val message = mmsProviderGateway.readMessage("content://mms/$providerId") ?: return MmsSyncResult(null, null)
        val alreadyCached = messageRepository.findByProviderId(providerId, MessageChannel.MMS) != null

        var insertedMessage: Message? = null
        if (!alreadyCached) {
            val recipients = (listOfNotNull(message.address) + mmsProviderGateway.readRecipients(providerId)).toSet()
            if (recipients.isEmpty()) {
                Log.w(TAG, "Skipping MMS provider id $providerId: no recipients, cannot resolve a thread")
                return MmsSyncResult(null, null)
            }

            val resolvedThreadId = resolveThreadIdCached(threadIdCache, recipients)
            val blocked = message.address != null && blockedNumberRepository.isBlocked(message.address)
            if (!blocked) {
                insertedMessage = messageRepository.insertIncoming(
                    message.copy(threadId = resolvedThreadId),
                    notifyConversation = false,
                )
            }
        }

        return MmsSyncResult(message.receivedAtMillis, insertedMessage)
    }

    /** Keeps only the latest-received row per thread, for the batched conversation-counters
     * update at the end of [syncAll]. */
    private fun recordConversationUpdate(updates: MutableMap<Long, Message>, message: Message) {
        val current = updates[message.threadId]
        if (current == null || message.receivedAtMillis >= current.receivedAtMillis) {
            updates[message.threadId] = message
        }
    }

    /** [ConversationRepository.resolveThreadId] is a real ContentResolver round-trip -- [cache]
     * is scoped to a single caller (one sync pass, or one [syncMessage] call), never persisted or
     * shared, since a longer-lived cache could go stale between syncs. */
    private suspend fun resolveThreadIdCached(cache: MutableMap<Set<String>, Long>, addresses: Set<String>): Long =
        cache.getOrPut(addresses) { conversationRepository.resolveThreadId(addresses) }

    private data class MmsSyncResult(val receivedAtMillis: Long?, val insertedMessage: Message?)

    /**
     * The real exception, not a generic string -- this is what actually shows up in
     * [SyncProgress.Failed.message] and thus the on-device failure banner, precisely so a real
     * device failure (e.g. an OEM Telephony provider quirk this app's fixed test devices never
     * hit) is diagnosable from the banner or a bug report alone, without needing a debugger
     * attached. [Log.w] above already logs the full stack trace separately for `adb logcat`.
     */
    private fun Throwable?.describe(): String =
        this?.let { "${it::class.simpleName}: ${it.message}" } ?: "unknown error"

    private companion object {
        const val TAG = "TelephonySyncRepository"
        const val MILLIS_PER_SECOND = 1_000L
    }
}
