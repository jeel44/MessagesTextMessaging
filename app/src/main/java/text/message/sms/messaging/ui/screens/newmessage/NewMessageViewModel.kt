package text.message.sms.messaging.ui.screens.newmessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.usecase.SyncContacts
import javax.inject.Inject

/**
 * One selectable row in the recipient list: either a real contact's number, or -- when nothing
 * matches what the user typed -- a fallback letting them message that raw text/number anyway.
 */
internal data class ContactRow(
    val key: String,
    val displayName: String,
    val initials: String,
    val address: String,
    val photoUri: String?,
    val isFallback: Boolean,
)

/**
 * Backs [NewMessageScreen]. [ContactRepository.observeAll] is a live view over the local
 * contacts cache; nothing else in the app currently triggers [SyncContacts], so this screen
 * kicks off one refresh itself the first time contacts access is confirmed (see
 * [onContactsPermissionGranted]). The full contact list is then filtered in memory as the user
 * types -- cheap for a typical device contact list, and avoids re-querying Room per keystroke.
 */
@HiltViewModel
class NewMessageViewModel @Inject constructor(
    contactRepository: ContactRepository,
    private val conversationRepository: ConversationRepository,
    private val syncContacts: SyncContacts,
) : ViewModel() {

    private val _queryText = MutableStateFlow("")
    val queryText: StateFlow<String> = _queryText.asStateFlow()

    private val allContacts: StateFlow<List<Contact>> = contactRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal val contactRows: StateFlow<List<ContactRow>> = combine(_queryText, allContacts) { query, contacts ->
        buildRows(query.trim(), contacts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var hasSyncedContacts = false

    fun onQueryChanged(text: String) {
        _queryText.value = text
    }

    /** Called once the screen has confirmed READ_CONTACTS is granted. Refreshes the local
     * contact cache the first time -- see the class doc for why this has to happen here. */
    fun onContactsPermissionGranted() {
        if (hasSyncedContacts) return
        hasSyncedContacts = true
        viewModelScope.launch { syncContacts() }
    }

    /** Finds the existing thread for [address], or creates one; see
     * [ConversationRepository.resolveThreadId]. */
    suspend fun resolveThreadId(address: String): Long =
        conversationRepository.resolveThreadId(setOf(address))

    private fun buildRows(query: String, contacts: List<Contact>): List<ContactRow> {
        val rows = contacts.flatMap { contact ->
            contact.numbers.filter { it.isNotBlank() }.map { number ->
                ContactRow(
                    key = "${contact.id}_$number",
                    displayName = contact.displayName,
                    initials = contact.initials,
                    address = number,
                    photoUri = contact.photoUri,
                    isFallback = false,
                )
            }
        }

        if (query.isBlank()) return rows

        val filtered = rows.filter { row ->
            row.displayName.contains(query, ignoreCase = true) || row.address.contains(query)
        }

        return filtered.ifEmpty {
            listOf(
                ContactRow(
                    key = "fallback_$query",
                    displayName = query,
                    initials = "",
                    address = query,
                    photoUri = null,
                    isFallback = true,
                ),
            )
        }
    }
}
