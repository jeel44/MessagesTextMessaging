package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.ContactGroup

/** The full local contact list -- see [ContactRepository.contactsState]. [Loading] means no
 * [ContactRepository.refreshFromProvider] has completed since this process started (the only
 * time a picker showing this should render a spinner instead of a list); a persisted Room cache
 * from a previous process, or a device with genuinely zero contacts once a refresh has completed,
 * both resolve to [Loaded] -- see [text.message.sms.messaging.data.repository.LocalContactRepository]. */
sealed interface ContactsState {
    data object Loading : ContactsState
    data class Loaded(val contacts: List<Contact>) : ContactsState
}

/** Access to the locally cached mirror of the system contacts provider. */
interface ContactRepository {

    /** Hot for the lifetime of the process -- started as soon as this singleton is constructed,
     * not per-screen -- so a picker like [text.message.sms.messaging.ui.screens.newmessage
     * .NewMessageViewModel] can seed its own state from [StateFlow.value] and render the current
     * list on its very first frame instead of waiting out a fresh subscription. */
    val contactsState: StateFlow<ContactsState>

    suspend fun findByAddress(address: String): Contact?

    fun search(query: String): Flow<List<Contact>>

    /** Real, user-created contact groups ("Family", "Work") and their members -- for a
     * group-MMS recipient picker, e.g. "add everyone from Family". */
    fun observeGroups(): Flow<List<ContactGroup>>

    /** Re-reads the system contacts provider into the local cache. */
    suspend fun refreshFromProvider()
}
