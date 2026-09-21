package text.message.sms.messaging.data.repository

import android.os.SystemClock
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
import text.message.sms.messaging.domain.repository.ConversationCounterUpdate
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.SyncProgress
import text.message.sms.messaging.domain.repository.SyncRepository
import text.message.sms.messaging.domain.usecase.BlockedSenderGate
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the system Telephony provider into the local cache.
 *
 * Incremental by construction: [SyncStateEntity] tracks the newest SMS/MMS timestamp already
 * pulled in, and every full sync -- the app-open walk in `syncAll` -- only ever asks the provider
 * for rows newer than that watermark. A row already present locally (matched by provider id +
 * channel, the same unique key [text.message.sms.messaging.data.local.db.dao.MessageDao]
 * enforces) is skipped rather than re-inserted, so re-running a sync is always safe.
 *
 * `syncAll` processes rows newest-first, in chunks of [SYNC_CHUNK_SIZE], each chunk written in a
 * single transaction ([MessageRepository.insertIncomingBatch]) with its own conversation-counter
 * flush right after -- see [flushConversationUpdates]. Both are load-bearing for perceived speed:
 * a transaction per row (the previous shape) pays SQLite's commit cost thousands of times over for
 * a large first-run backfill, and [text.message.sms.messaging.data.local.db.dao.ConversationDao
 * .observeInbox]'s `last_message_at > 0` filter means Home shows nothing at all for a thread until
 * its counters are flushed -- so flushing only once at the very end (the previous shape) made Home
 * look empty for the entire sync instead of filling in progressively, newest conversations first.
 */
@Singleton
class TelephonySyncRepository @Inject constructor(
    private val smsProviderGateway: SmsProviderGateway,
    private val mmsProviderGateway: MmsProviderGateway,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val blockedSenderGate: BlockedSenderGate,
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

        val startedAtMillis = SystemClock.elapsedRealtime()
        Log.d(PERF_TAG, "syncAll start")

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
            val mmsRefs = mmsProviderGateway.queryIdsSince(state.lastMmsDateSeconds)
            val total = smsMessages.size + mmsRefs.size
            Log.d(
                PERF_TAG,
                "provider query returned ${smsMessages.size} sms + ${mmsRefs.size} mms " +
                    "in ${SystemClock.elapsedRealtime() - startedAtMillis}ms",
            )

            if (total == 0) {
                syncStateDao.upsert(state.copy(lastFullSyncAtMillis = System.currentTimeMillis()))
                progress.value = SyncProgress.Idle
                return@withContext
            }

            progress.value = SyncProgress.Running(0, total)
            val stats = SyncStats(newestSmsMillis = state.lastSmsDateMillis, newestMmsSeconds = state.lastMmsDateSeconds)
            // Telephony.Threads.getOrCreateThreadId is a real ContentResolver round-trip, not a
            // local lookup -- caching it per address set for the duration of this one sync pass
            // avoids repeating that call for every message in a thread that has many.
            val threadIdCache = mutableMapOf<Set<String>, Long>()

            // Newest-first, chunked: the most recently active conversations become visible in
            // Home within the first chunk's insert (typically well under a second), instead of
            // only once the *entire* backfill finishes -- see this class's doc comment.
            smsMessages.asReversed().chunked(SYNC_CHUNK_SIZE).forEachIndexed { index, chunk ->
                val chunkStartMillis = SystemClock.elapsedRealtime()
                val inserted = prepareAndInsertSmsChunk(chunk, threadIdCache, stats)
                flushConversationUpdates(inserted)
                progress.value = SyncProgress.Running(stats.completed, total)
                Log.d(
                    PERF_TAG,
                    "sms chunk $index (${chunk.size} rows, ${inserted.size} inserted) in " +
                        "${SystemClock.elapsedRealtime() - chunkStartMillis}ms",
                )
            }

            mmsRefs.asReversed().chunked(SYNC_CHUNK_SIZE).forEachIndexed { index, chunk ->
                val chunkStartMillis = SystemClock.elapsedRealtime()
                val inserted = prepareAndInsertMmsChunk(chunk, threadIdCache, stats)
                flushConversationUpdates(inserted)
                progress.value = SyncProgress.Running(stats.completed, total)
                Log.d(
                    PERF_TAG,
                    "mms chunk $index (${chunk.size} rows, ${inserted.size} inserted) in " +
                        "${SystemClock.elapsedRealtime() - chunkStartMillis}ms",
                )
            }

            syncStateDao.upsert(
                SyncStateEntity(
                    lastSmsDateMillis = stats.newestSmsMillis,
                    lastMmsDateSeconds = stats.newestMmsSeconds,
                    lastFullSyncAtMillis = System.currentTimeMillis(),
                ),
            )
            progress.value = if (stats.failedCount > 0) {
                SyncProgress.Failed(
                    "${stats.failedCount} of $total message(s) failed to sync: ${stats.firstFailure.describe()}",
                    stats.failedCount,
                )
            } else {
                SyncProgress.Idle
            }
            Log.d(
                PERF_TAG,
                "syncAll end: ${stats.completed}/$total processed, ${stats.failedCount} failed, " +
                    "total ${SystemClock.elapsedRealtime() - startedAtMillis}ms",
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "syncAll failed", error)
            progress.value = SyncProgress.Failed(error.describe())
        }
    }

    /**
     * Imports [threadId]'s newest [limit] SMS/MMS rows immediately, outside the normal
     * incremental watermark -- called when [text.message.sms.messaging.ui.screens.chat.ChatViewModel]
     * opens a thread, so that thread's recent history is on screen right away even if the
     * background [syncAll] pass (chunked oldest-thread-last within each channel) hasn't reached it
     * yet. Cheap to call unconditionally: [prepareAndInsertSmsChunk]/[prepareAndInsertMmsChunk]'s
     * batched existence check makes an already-fully-synced thread a near no-op. Never touches
     * [SyncStateEntity] -- this is a targeted, out-of-order import, not part of the sequential
     * cursor [syncAll] maintains, so it must not perturb that cursor's watermark.
     */
    override suspend fun syncThreadPriority(threadId: Long, limit: Int): Unit = withContext(Dispatchers.IO) {
        if (!defaultSmsAppGuard.isDefault || !defaultSmsAppGuard.hasCoreSmsPermissions) return@withContext

        try {
            val threadIdCache = mutableMapOf<Set<String>, Long>()
            val stats = SyncStats()

            val smsRows = smsProviderGateway.queryThreadRecent(threadId, limit)
            val insertedSms = prepareAndInsertSmsChunk(smsRows, threadIdCache, stats)

            val mmsIds = mmsProviderGateway.queryThreadRecentIds(threadId, limit)
            // dateSeconds is irrelevant here -- this path never writes SyncStateEntity, so the
            // watermark bookkeeping prepareAndInsertMmsChunk does internally is simply discarded.
            val mmsRefs = mmsIds.map { MmsProviderGateway.MmsIdAndDate(it, 0L) }
            val insertedMms = prepareAndInsertMmsChunk(mmsRefs, threadIdCache, stats)

            flushConversationUpdates(insertedSms + insertedMms)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "syncThreadPriority failed for thread $threadId", error)
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

    override suspend fun resetSyncWatermark(): Unit = withContext(Dispatchers.IO) {
        syncStateDao.upsert(SyncStateEntity())
    }

    override suspend fun lastSyncAtMillis(): Long? = syncStateDao.get()?.lastFullSyncAtMillis

    /**
     * Prepares a chunk of raw SMS rows -- skipping already-cached ones (one batched existence
     * check for the whole chunk, not one query per row) and addressless rows -- then inserts
     * everything that survived in a single transaction via [MessageRepository.insertIncomingBatch].
     * A blocked sender's row is still inserted (see [BlockedSenderGate]'s doc); its resolved thread
     * is marked blocked in one batched [BlockedSenderGate.markThreadsBlocked] call after the insert
     * rather than one write per row. [stats] is mutated in place for the caller's running
     * completed/failed/watermark bookkeeping across chunks.
     */
    private suspend fun prepareAndInsertSmsChunk(
        chunk: List<Message>,
        threadIdCache: MutableMap<Set<String>, Long>,
        stats: SyncStats,
    ): List<Message> {
        if (chunk.isEmpty()) return emptyList()
        val existing = messageRepository.findExistingProviderIds(chunk.map { it.providerId }, MessageChannel.SMS)
        val toInsert = mutableListOf<Message>()
        val blockedThreadIds = mutableSetOf<Long>()

        for (raw in chunk) {
            try {
                if (raw.providerId !in existing) {
                    val addresses = setOfNotNull(raw.address)
                    if (addresses.isEmpty()) {
                        // No address means there is nothing to resolve a thread from -- see
                        // the class doc on TelephonySyncRepository's original syncSms.
                        Log.w(TAG, "Skipping SMS provider id ${raw.providerId}: no address, cannot resolve a thread")
                    } else {
                        val resolvedThreadId = resolveThreadIdCached(threadIdCache, addresses)
                        toInsert += raw.copy(threadId = resolvedThreadId)
                        if (blockedSenderGate.isBlocked(raw.address)) blockedThreadIds += resolvedThreadId
                    }
                }
                if (!stats.smsWatermarkStalled) {
                    stats.newestSmsMillis = maxOf(stats.newestSmsMillis, raw.receivedAtMillis)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                stats.failedCount++
                stats.smsWatermarkStalled = true
                if (stats.firstFailure == null) stats.firstFailure = error
                Log.w(TAG, "Skipping malformed SMS row (provider id ${raw.providerId})", error)
            }
            stats.completed++
        }

        val inserted = insertChunkSafely(toInsert)
        blockedSenderGate.markThreadsBlocked(blockedThreadIds)
        return inserted
    }

    /** MMS equivalent of [prepareAndInsertSmsChunk]. The existence check runs *before*
     * [readAndPrepareMms], so an already-cached row skips its full header/address/part re-decode
     * entirely rather than only skipping the DB write, unlike the old per-row `syncMms`. */
    private suspend fun prepareAndInsertMmsChunk(
        chunk: List<MmsProviderGateway.MmsIdAndDate>,
        threadIdCache: MutableMap<Set<String>, Long>,
        stats: SyncStats,
    ): List<Message> {
        if (chunk.isEmpty()) return emptyList()
        val existing = messageRepository.findExistingProviderIds(chunk.map { it.providerId }, MessageChannel.MMS)
        val toInsert = mutableListOf<Message>()
        val blockedThreadIds = mutableSetOf<Long>()

        for (ref in chunk) {
            try {
                if (ref.providerId !in existing) {
                    readAndPrepareMms(ref.providerId, threadIdCache)?.let { message ->
                        toInsert += message
                        if (blockedSenderGate.isBlocked(message.address)) blockedThreadIds += message.threadId
                    }
                }
                if (!stats.mmsWatermarkStalled) {
                    stats.newestMmsSeconds = maxOf(stats.newestMmsSeconds, ref.dateSeconds)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                stats.failedCount++
                stats.mmsWatermarkStalled = true
                if (stats.firstFailure == null) stats.firstFailure = error
                Log.w(TAG, "Skipping malformed MMS row (provider id ${ref.providerId})", error)
            }
            stats.completed++
        }

        val inserted = insertChunkSafely(toInsert)
        blockedSenderGate.markThreadsBlocked(blockedThreadIds)
        return inserted
    }

    /** Reads and resolves one MMS row -- everything [prepareAndInsertMmsChunk] needs before a row
     * can join the chunk's batch insert. Returns `null` only for a row that genuinely can't be
     * resolved (no recipients) -- a blocked sender's row is still returned (see
     * [BlockedSenderGate]'s doc), [prepareAndInsertMmsChunk] itself decides whether to mark the
     * thread blocked. */
    private suspend fun readAndPrepareMms(providerId: Long, threadIdCache: MutableMap<Set<String>, Long>): Message? {
        val message = mmsProviderGateway.readMessage("content://mms/$providerId") ?: return null

        val rawRecipients = (listOfNotNull(message.address) + mmsProviderGateway.readRecipients(providerId)).toSet()
        if (rawRecipients.isEmpty()) {
            Log.w(TAG, "Skipping MMS provider id $providerId: no recipients, cannot resolve a thread")
            return null
        }

        // Same business/RCS-sender-address exclusion as MmsDownloadResultReceiver -- an address
        // like `agent@rbm.goog` must not count as a participant.
        val recipients = PhoneNumbers.realParticipantsOnly(rawRecipients)
        val resolvedThreadId = resolveThreadIdCached(threadIdCache, recipients)

        return message.copy(threadId = resolvedThreadId)
    }

    /**
     * Inserts [toInsert] as a single batch/transaction. A batch failure (expected only if the
     * data this chunk was built from is somehow inconsistent -- never seen in practice, since
     * every row was already validated while building [toInsert]) falls back to inserting one row
     * at a time so a single bad row can't lose the rest of an otherwise-healthy chunk, preserving
     * the same "one malformed row must not lose the whole batch" guarantee the old per-row
     * transactions gave for free.
     */
    private suspend fun insertChunkSafely(toInsert: List<Message>): List<Message> {
        if (toInsert.isEmpty()) return emptyList()
        return try {
            messageRepository.insertIncomingBatch(toInsert)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Batch insert failed for a chunk of ${toInsert.size} row(s); retrying individually", error)
            toInsert.mapNotNull { message ->
                try {
                    messageRepository.insertIncoming(message, notifyConversation = false)
                } catch (rowError: Exception) {
                    if (rowError is CancellationException) throw rowError
                    Log.w(TAG, "Skipping malformed row (provider id ${message.providerId}) during fallback insert", rowError)
                    null
                }
            }
        }
    }

    /** Batches [inserted] into one conversation-counters update per touched thread (keeping only
     * the latest-received row per thread) and flushes it in one transaction -- called once per
     * chunk rather than once per message, so the conversation list's live Flow re-sorts and
     * re-renders a handful of times per sync pass instead of flickering through every message. */
    private suspend fun flushConversationUpdates(inserted: List<Message>) {
        if (inserted.isEmpty()) return
        val updates = mutableMapOf<Long, Message>()
        inserted.forEach { recordConversationUpdate(updates, it) }
        conversationRepository.refreshCountersBatch(
            updates.mapValues { (_, message) -> ConversationCounterUpdate(message.body, message.receivedAtMillis) },
        )
    }

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
     *
     * Only used by the legacy single-row [syncMessage] path now; [syncAll] uses
     * [prepareAndInsertSmsChunk] instead.
     */
    private suspend fun syncSms(message: Message, threadIdCache: MutableMap<Set<String>, Long>): Message? {
        if (messageRepository.findByProviderId(message.providerId, MessageChannel.SMS) != null) return null

        val addresses = setOfNotNull(message.address)
        if (addresses.isEmpty()) {
            Log.w(TAG, "Skipping SMS provider id ${message.providerId}: no address, cannot resolve a thread")
            return null
        }

        val resolvedThreadId = resolveThreadIdCached(threadIdCache, addresses)
        val inserted = messageRepository.insertIncoming(message.copy(threadId = resolvedThreadId), notifyConversation = false)
        if (inserted != null && blockedSenderGate.isBlocked(message.address)) {
            blockedSenderGate.markThreadsBlocked(listOf(resolvedThreadId))
        }
        return inserted
    }

    /**
     * [MmsSyncResult.receivedAtMillis] is the message's received timestamp (for the sync
     * watermark) whether or not it was newly inserted, so an already-cached row still lets the
     * watermark move past it. [MmsSyncResult.insertedMessage] is non-null only when a row was
     * actually inserted, for the caller's batched conversation-counters update. Applies the same
     * resolved-thread-id rewrite as [syncSms], and for the same reason -- see its doc.
     *
     * Only used by the legacy single-row [syncMessage] path now; [syncAll] uses
     * [prepareAndInsertMmsChunk]/[readAndPrepareMms] instead.
     */
    private suspend fun syncMms(providerId: Long, threadIdCache: MutableMap<Set<String>, Long>): MmsSyncResult {
        val message = mmsProviderGateway.readMessage("content://mms/$providerId") ?: return MmsSyncResult(null, null)
        val alreadyCached = messageRepository.findByProviderId(providerId, MessageChannel.MMS) != null

        var insertedMessage: Message? = null
        if (!alreadyCached) {
            val rawRecipients = (listOfNotNull(message.address) + mmsProviderGateway.readRecipients(providerId)).toSet()
            if (rawRecipients.isEmpty()) {
                Log.w(TAG, "Skipping MMS provider id $providerId: no recipients, cannot resolve a thread")
                return MmsSyncResult(null, null)
            }

            val recipients = PhoneNumbers.realParticipantsOnly(rawRecipients)
            val resolvedThreadId = resolveThreadIdCached(threadIdCache, recipients)
            insertedMessage = messageRepository.insertIncoming(
                message.copy(threadId = resolvedThreadId),
                notifyConversation = false,
            )
            if (insertedMessage != null && blockedSenderGate.isBlocked(message.address)) {
                blockedSenderGate.markThreadsBlocked(listOf(resolvedThreadId))
            }
        }

        return MmsSyncResult(message.receivedAtMillis, insertedMessage)
    }

    /** Keeps only the latest-received row per thread, for a batched conversation-counters
     * update. */
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

    /** Running counters threaded through [syncAll]'s chunk loop -- a single mutable holder rather
     * than several `var`s, since both the SMS and MMS chunk-processing helpers need to update the
     * same state across many chunk calls. */
    private class SyncStats(
        var newestSmsMillis: Long = 0L,
        var newestMmsSeconds: Long = 0L,
    ) {
        var completed: Int = 0
        var failedCount: Int = 0
        var firstFailure: Exception? = null
        var smsWatermarkStalled: Boolean = false
        var mmsWatermarkStalled: Boolean = false
    }

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

        /** Temporary perf-pass breadcrumb (Logcat tag "SyncPerf") -- read with
         * `adb logcat -s SyncPerf:D` to see per-phase sync timings on a real device. */
        const val PERF_TAG = "SyncPerf"

        /** Rows per transaction during a bulk sync -- large enough that the fixed per-transaction
         * commit cost is amortized over many rows, small enough that the very first chunk (which
         * unlocks Home's first visible data) still commits in well under a second. */
        const val SYNC_CHUNK_SIZE = 500
    }
}
