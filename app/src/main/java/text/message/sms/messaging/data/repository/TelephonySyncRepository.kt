package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import text.message.sms.messaging.domain.repository.SyncProgress
import text.message.sms.messaging.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the system Telephony provider into the local cache.
 *
 * The cursor walk itself arrives with the sync pipeline; this holds the progress channel the
 * UI subscribes to so the wiring can be exercised beforehand.
 */
@Singleton
class TelephonySyncRepository @Inject constructor() : SyncRepository {

    private val progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)

    override fun observeProgress(): Flow<SyncProgress> = progress.asStateFlow()

    override suspend fun syncAll() {
        TODO("Full provider sync arrives with the sync pipeline")
    }

    override suspend fun syncMessage(providerUri: String) {
        TODO("Single-row provider sync arrives with the sync pipeline")
    }

    override suspend fun lastSyncAtMillis(): Long? = null
}
