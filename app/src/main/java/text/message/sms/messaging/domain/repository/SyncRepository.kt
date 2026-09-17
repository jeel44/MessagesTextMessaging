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

    /** Timestamp of the last successful full sync, or `null` if it never ran. */
    suspend fun lastSyncAtMillis(): Long?
}

/** Progress of an in-flight sync. */
sealed interface SyncProgress {
    data object Idle : SyncProgress
    data class Running(val completed: Int, val total: Int) : SyncProgress
    data class Failed(val message: String) : SyncProgress

    /** [syncAll] was called without holding the default-SMS-app role, so it returned immediately
     * without querying the Telephony provider -- a non-default app's reads there can be partial
     * or plain wrong. Distinct from [Idle] so a caller can tell "nothing to do" apart from
     * "blocked pending the role" if it ever needs to. */
    data object NotDefaultApp : SyncProgress
}
