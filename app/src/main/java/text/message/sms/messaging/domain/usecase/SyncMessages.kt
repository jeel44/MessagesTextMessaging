package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.SyncRepository
import javax.inject.Inject

/** Rebuilds the local cache from the system Telephony provider. */
class SyncMessages @Inject constructor(
    private val syncRepository: SyncRepository,
) : UseCase {

    suspend operator fun invoke() {
        syncRepository.syncAll()
    }
}
