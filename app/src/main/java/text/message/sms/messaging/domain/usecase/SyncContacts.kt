package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ContactRepository
import javax.inject.Inject

/** Refreshes the cached contact mirror from the system contacts provider. */
class SyncContacts @Inject constructor(
    private val contactRepository: ContactRepository,
) : UseCase {

    suspend operator fun invoke() {
        contactRepository.refreshFromProvider()
    }
}
