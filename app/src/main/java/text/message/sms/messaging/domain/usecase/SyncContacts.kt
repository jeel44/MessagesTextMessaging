package text.message.sms.messaging.domain.usecase

import android.util.Log
import text.message.sms.messaging.domain.repository.ContactRepository
import javax.inject.Inject

/** Refreshes the cached contact mirror from the system contacts provider. */
class SyncContacts @Inject constructor(
    private val contactRepository: ContactRepository,
) : UseCase {

    suspend operator fun invoke() {
        Log.d("SyncContacts", "invoke: calling contactRepository.refreshFromProvider()")
        contactRepository.refreshFromProvider()
    }
}
