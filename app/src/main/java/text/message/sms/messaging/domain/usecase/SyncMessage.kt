package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.SyncRepository
import javax.inject.Inject

/** Pulls a single provider row into the local cache, e.g. right after a broadcast. */
class SyncMessage @Inject constructor(
    private val syncRepository: SyncRepository,
) : UseCase {

    suspend operator fun invoke(providerUri: String) {
        syncRepository.syncMessage(providerUri)
    }
}
