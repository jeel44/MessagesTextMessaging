package text.message.sms.messaging.ui.screens.contactslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ContactsState
import javax.inject.Inject

/**
 * Backs [ContactsListScreen]. Same sourcing as [text.message.sms.messaging.ui.screens.newmessage
 * .NewMessageViewModel]: [ContactRepository.contactsState] is a single, app-scoped hot cache, so
 * this only collects and sorts it rather than running its own query.
 */
@HiltViewModel
class ContactsListViewModel @Inject constructor(
    contactRepository: ContactRepository,
) : ViewModel() {

    internal val contacts: StateFlow<List<Contact>> = contactRepository.contactsState
        .map { state ->
            (state as? ContactsState.Loaded)?.contacts
                ?.sortedBy { it.displayName.lowercase() }
                .orEmpty()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ((contactRepository.contactsState.value as? ContactsState.Loaded)?.contacts
                ?.sortedBy { it.displayName.lowercase() }).orEmpty(),
        )
}
