package text.message.sms.messaging.ui.screens.newmessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ContactsState
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
 * Backs [NewMessageScreen]. [ContactRepository.contactsState] is a single, app-scoped hot cache
 * (see [text.message.sms.messaging.data.repository.LocalContactRepository]) that outlives this
 * ViewModel -- this class never runs its own contacts query, it only collects that shared state
 * and filters it in memory as the user types, cheap for a typical device contact list and avoids
 * re-querying Room per keystroke. [contactRows] and [isContactsLoading] both seed from
 * [ContactRepository.contactsState]'s CURRENT value (a real [kotlinx.coroutines.flow.StateFlow]
 * always has one), not `emptyList()`/`true` placeholders, so a screen opened well after launch --
 * the overwhelmingly common case -- renders its first frame with the real list already in hand.
 */
@HiltViewModel
class NewMessageViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val conversationRepository: ConversationRepository,
    private val syncContacts: SyncContacts,
) : ViewModel() {

    private val _queryText = MutableStateFlow("")
    val queryText: StateFlow<String> = _queryText.asStateFlow()

    /** Backs [NewMessageScreen]'s loading gate -- true only while
     * [ContactRepository.contactsState] has never completed a refresh this process, so the
     * screen can withhold "No contacts found" until a real, possibly non-empty, result is known. */
    internal val isContactsLoading: StateFlow<Boolean> = contactRepository.contactsState
        .map { it is ContactsState.Loading }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            contactRepository.contactsState.value is ContactsState.Loading,
        )

    internal val contactRows: StateFlow<List<ContactRow>> = combine(
        _queryText,
        contactRepository.contactsState,
    ) { query, state ->
        buildRows(query.trim(), (state as? ContactsState.Loaded)?.contacts.orEmpty())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildRows("", (contactRepository.contactsState.value as? ContactsState.Loaded)?.contacts.orEmpty()),
    )

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
