package text.message.sms.messaging.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.datastore.SearchPreferences
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Which category of result [SearchScreen] shows. Only filters the data layer can actually
 * answer are offered -- see the class doc on [SearchViewModel] for why there is no Media chip. */
internal enum class SearchFilter { ALL, PEOPLE, MESSAGES }

/** One "Messages" tab row: a matching message, resolved back to the thread it belongs to so the
 * row can show that thread's avatar/name, the same way Home identifies a conversation. */
internal data class MessageSearchRow(
    val message: Message,
    val conversation: Conversation,
)

/**
 * Backs [SearchScreen].
 *
 * Two of the three filters map directly onto existing repository search: [ConversationRepository.search]
 * for "People" (thread snippet, participant address, or -- since this pass's DAO change -- a
 * saved contact's name) and [MessageRepository.search] for "Messages" (message bodies). A "Media"
 * chip was in the original design reference but is deliberately not offered: [text.message.sms.messaging.domain.repository.AttachmentRepository]
 * has no query over attachment file names/text, and Room has no existing indexed path from an
 * attachment back to a thread for search purposes -- that would need new repository/DAO work
 * outside this pass's scope.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val searchPreferences: SearchPreferences,
) : ViewModel() {

    private val _queryText = MutableStateFlow("")
    val queryText: StateFlow<String> = _queryText.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    internal val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    internal val recentSearches: StateFlow<List<String>> = searchPreferences.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> = _queryText
        .map { it.trim() }
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val peopleResults: StateFlow<List<Conversation>> = debouncedQuery
        .flatMapLatest { query -> if (query.isEmpty()) flowOf(emptyList()) else conversationRepository.search(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every non-archived-non-blocked-inclusive thread, keyed by id, used only to attach an
     * avatar/name to a message hit -- not itself filtered by the query. */
    private val allConversationsByThreadId: Flow<Map<Long, Conversation>> = combine(
        conversationRepository.observeInbox(),
        conversationRepository.observeArchived(),
    ) { inbox, archived -> (inbox + archived).associateBy { it.threadId } }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val messageResults: StateFlow<List<MessageSearchRow>> = combine(
        debouncedQuery.flatMapLatest { query -> if (query.isEmpty()) flowOf(emptyList()) else messageRepository.search(query) },
        allConversationsByThreadId,
    ) { messages, conversationsByThreadId ->
        // A hit inside a blocked thread resolves to no conversation here (observeInbox and
        // observeArchived both exclude is_blocked = 1), so it is silently dropped -- consistent
        // with blocked conversations staying out of sight everywhere else in the app.
        messages.mapNotNull { message ->
            val conversation = conversationsByThreadId[message.threadId] ?: return@mapNotNull null
            MessageSearchRow(message, conversation)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal fun onQueryChanged(text: String) {
        _queryText.value = text
    }

    internal fun onFilterSelected(filter: SearchFilter) {
        _filter.value = filter
    }

    internal fun onRecentSearchSelected(term: String) {
        _queryText.value = term
    }

    /** Records the current query as a recent search. Called on keyboard submit and on tapping a
     * result, never on every keystroke, so half-typed text never pollutes the recent-searches
     * list. */
    internal fun commitSearch() {
        val query = _queryText.value.trim()
        if (query.isEmpty()) return
        viewModelScope.launch { searchPreferences.addSearch(query) }
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 300L
