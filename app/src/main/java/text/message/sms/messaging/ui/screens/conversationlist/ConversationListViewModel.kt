package text.message.sms.messaging.ui.screens.conversationlist

import android.content.Intent
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
import text.message.sms.messaging.data.local.provider.ProviderChangeObserver
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard
import javax.inject.Inject

internal enum class ConversationFilter { ALL, UNREAD, PINNED }

/**
 * Backs [ConversationListScreen]. [conversations] is a live view over
 * [ConversationRepository.observeInbox] -- no manual refresh, no one-shot snapshot -- so rows
 * from the background sync kicked off during onboarding (or any later sync) appear the moment
 * Room commits them, whether or not the sync had already finished when this screen was reached.
 *
 * [isDefaultSmsApp] backs the screen's empty-state message: an empty inbox while the role is
 * missing means "sync never ran," not "genuinely no messages." [DefaultSmsAppGuard.isDefault] is
 * a plain synchronous read, not observable, so [refreshDefaultSmsAppStatus] re-reads it on every
 * screen resume (returning from the in-app role request, or from system Settings) and, only on a
 * false-to-true transition, starts the catch-up sync and registers [ProviderChangeObserver] for
 * live updates -- the same guarded pattern [text.message.sms.messaging.MainActivity] uses, kept
 * here too so this screen's own empty state flips back to normal without depending on which of
 * the two happens to run first. [ProviderChangeObserver.register] is a no-op if already
 * registered, so this never double-registers alongside [text.message.sms.messaging.MainActivity]
 * or [text.message.sms.messaging.MessagingApplication]'s own calls to it.
 */
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    conversationRepository: ConversationRepository,
    private val defaultSmsAppGuard: DefaultSmsAppGuard,
    private val syncMessages: SyncMessages,
    private val providerChangeObserver: ProviderChangeObserver,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(ConversationFilter.ALL)
    internal val filter: StateFlow<ConversationFilter> = selectedFilter.asStateFlow()

    private val _isDefaultSmsApp = MutableStateFlow(defaultSmsAppGuard.isDefault)
    internal val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

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

    /** The system intent that asks the user to make this app the default SMS handler. */
    internal fun defaultSmsAppRoleRequestIntent(): Intent = defaultSmsAppGuard.buildRoleRequestIntent()

    /** Call on every screen resume (and right after the in-app role request returns). Re-reads
     * the role and, only if it just went from not-held to held, starts a catch-up sync -- never
     * on every call, so returning to this screen repeatedly can't spam redundant syncs. */
    internal fun refreshDefaultSmsAppStatus() {
        val isDefaultNow = defaultSmsAppGuard.isDefault
        val justGranted = isDefaultNow && !_isDefaultSmsApp.value
        _isDefaultSmsApp.value = isDefaultNow
        if (justGranted) {
            providerChangeObserver.register()
            viewModelScope.launch { syncMessages() }
        }
    }
}
