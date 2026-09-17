package text.message.sms.messaging.ui.screens.conversationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

internal enum class ConversationFilter { ALL, UNREAD, PINNED }

/**
 * Backs [ConversationListScreen]. [conversations] is a live view over
 * [ConversationRepository.observeInbox] -- no manual refresh, no one-shot snapshot -- so rows
 * from the background sync kicked off during onboarding (or any later sync) appear the moment
 * Room commits them, whether or not the sync had already finished when this screen was reached.
 */
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    conversationRepository: ConversationRepository,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(ConversationFilter.ALL)
    internal val filter: StateFlow<ConversationFilter> = selectedFilter.asStateFlow()

    val conversations: StateFlow<List<Conversation>> = combine(
        conversationRepository.observeInbox(),
        selectedFilter,
    ) { inbox, filter ->
        when (filter) {
            ConversationFilter.ALL -> inbox
            ConversationFilter.UNREAD -> inbox.filter { it.hasUnread }
            ConversationFilter.PINNED -> inbox.filter { it.isPinned }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    internal fun selectFilter(filter: ConversationFilter) {
        selectedFilter.value = filter
    }
}
