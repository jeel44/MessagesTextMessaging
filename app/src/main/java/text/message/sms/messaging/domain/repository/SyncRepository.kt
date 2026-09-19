package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow

/** Mirrors the system Telephony provider into the local Room cache. */
interface SyncRepository {

    /** Emits the progress of a full or partial sync. */
    fun observeProgress(): Flow<SyncProgress>

    /** Rebuilds the entire local cache from the system provider. */
    suspend fun syncAll()

    /** Pulls a single provider row into the cache, e.g. after a broadcast. */
    suspend fun syncMessage(providerUri: String)

    /** Imports [threadId]'s newest [limit] messages right away, ahead of wherever [syncAll]'s
     * background chunked walk currently is -- see
     * [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncThreadPriority] for
     * why this never touches the incremental watermark [syncAll] maintains. Cheap to call even
     * when the thread is already fully cached; a fake/test repository can treat this as a no-op. */
    suspend fun syncThreadPriority(threadId: Long, limit: Int = 40) {}

    /** Clears the incremental-sync watermark so the next [syncAll] walks the entire system
     * provider again instead of only rows newer than what was already pulled -- needed after
     * [text.message.sms.messaging.domain.repository.BackupRepository.import] writes historical
     * rows into the provider that would otherwise be older than the watermark and silently
     * skipped. */
    suspend fun resetSyncWatermark()

    /** Timestamp of the last successful full sync, or `null` if it never ran. */
    suspend fun lastSyncAtMillis(): Long?
}

/** Progress of an in-flight sync. */
sealed interface SyncProgress {
    data object Idle : SyncProgress
    data class Running(val completed: Int, val total: Int) : SyncProgress
    /** [failedCount] is 0 for a total failure (the outer sync itself threw, e.g. the Telephony
     * query call failed) and greater than 0 for a partial failure -- most rows synced fine, but
     * this many individual rows were skipped. Either way the sync is visible as failed rather
     * than silently looking like an empty inbox. */
    data class Failed(val message: String, val failedCount: Int = 0) : SyncProgress

    /** [syncAll] was called without holding the default-SMS-app role, so it returned immediately
     * without querying the Telephony provider -- a non-default app's reads there can be partial
     * or plain wrong. Distinct from [Idle] so a caller can tell "nothing to do" apart from
     * "blocked pending the role" if it ever needs to. */
    data object NotDefaultApp : SyncProgress
}
