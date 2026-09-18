package text.message.sms.messaging.ui.screens.conversationlist

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.datastore.SwipeActionPreference
import text.message.sms.messaging.data.local.datastore.SwipeActionPreferences
import text.message.sms.messaging.data.local.provider.ProviderChangeObserver
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.SyncProgress
import text.message.sms.messaging.domain.repository.SyncRepository
import text.message.sms.messaging.domain.usecase.DeleteConversation
import text.message.sms.messaging.domain.usecase.MarkArchived
import text.message.sms.messaging.domain.usecase.MarkRead
import text.message.sms.messaging.domain.usecase.MarkUnread
import text.message.sms.messaging.domain.usecase.MarkUnarchived
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard
import javax.inject.Inject

internal enum class ConversationFilter { ALL, UNREAD, PINNED }

/** A swipe just happened and needs an undo-able snackbar -- see [ConversationListScreen]'s
 * `LaunchedEffect` for how each is resolved (either undone, or left to take effect). */
internal sealed interface ConversationListEvent {
    /** [conversation] was archived immediately (a non-destructive, trivially-reversible write);
     * the snackbar's only job is offering [ConversationListViewModel.undoArchive]. */
    data class Archived(val conversation: Conversation) : ConversationListEvent

    /** [conversation] is hidden from the list but *not yet actually deleted* -- nothing
     * irreversible has happened yet. The screen must resolve this with either
     * [ConversationListViewModel.cancelPendingDelete] (snackbar's Undo tapped) or
     * [ConversationListViewModel.confirmPendingDelete] (snackbar timed out/dismissed) once the
     * snackbar's result is known, or the thread stays hidden forever without actually being
     * deleted. */
    data class PendingDelete(val conversation: Conversation) : ConversationListEvent
}

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
 *
 * [syncProgress] is a live view over [SyncRepository.observeProgress]: unlike the Language
 * onboarding screen (which deliberately stays silent about sync), Home surfaces it -- a
 * [SyncProgress.Running] first sync shouldn't look indistinguishable from a genuinely empty
 * inbox, and a [SyncProgress.Failed] sync shouldn't be invisible.
 */
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    conversationRepository: ConversationRepository,
    syncRepository: SyncRepository,
    private val defaultSmsAppGuard: DefaultSmsAppGuard,
    private val syncMessages: SyncMessages,
    private val providerChangeObserver: ProviderChangeObserver,
    private val swipeActionPreferences: SwipeActionPreferences,
    private val markArchivedUseCase: MarkArchived,
    private val markUnarchivedUseCase: MarkUnarchived,
    private val deleteConversationUseCase: DeleteConversation,
    private val markReadUseCase: MarkRead,
    private val markUnreadUseCase: MarkUnread,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(ConversationFilter.ALL)
    internal val filter: StateFlow<ConversationFilter> = selectedFilter.asStateFlow()

    private val _isDefaultSmsApp = MutableStateFlow(defaultSmsAppGuard.isDefault)
    internal val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    /** So a sync failure (partial or total) or a long first sync is visible on this screen
     * instead of just looking like an empty inbox -- see [ConversationListScreen]. */
    internal val syncProgress: StateFlow<SyncProgress> = syncRepository.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncProgress.Idle)

    /** Threads swiped for delete but still inside their undo window -- excluded from
     * [conversations] immediately (so the swipe still looks committed) even though
     * [DeleteConversation] hasn't actually run yet. See [ConversationListEvent.PendingDelete]. */
    private val pendingDeleteThreadIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _events = MutableSharedFlow<ConversationListEvent>(extraBufferCapacity = 1)
    internal val events: SharedFlow<ConversationListEvent> = _events

    val conversations: StateFlow<List<Conversation>> = combine(
        conversationRepository.observeInbox(),
        selectedFilter,
        pendingDeleteThreadIds,
    ) { inbox, filter, pendingDeletes ->
        val visible = inbox.filterNot { it.threadId in pendingDeletes }
        when (filter) {
            ConversationFilter.ALL -> visible
            ConversationFilter.UNREAD -> visible.filter { it.hasUnread }
            ConversationFilter.PINNED -> visible.filter { it.isPinned }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Backs the "Archived" entry point at the top of the inbox -- hidden entirely when there's
     * nothing archived yet, matching QKSMS. */
    internal val archivedCount: StateFlow<Int> = conversationRepository.observeArchived()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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

    /** Backs the failed-sync banner's Retry button. */
    internal fun retrySync() {
        viewModelScope.launch { syncMessages() }
    }

    /** Which action each swipe direction performs -- see [SwipeableConversationRow]. Live over
     * [SwipeActionPreferences.swipeActionPreference] so a change made in Settings' picker applies
     * to this screen immediately, the same pattern [text.message.sms.messaging.ui.screens.settings.SettingsViewModel]
     * uses for the theme preference. */
    internal val swipeActionPreference: StateFlow<SwipeActionPreference> = swipeActionPreferences.swipeActionPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeActionPreference())

    /** Archiving is immediate -- it's a cheap, non-destructive write, so there's nothing to
     * defer. The snackbar this triggers exists purely to offer [undoArchive]. */
    internal fun archiveConversation(conversation: Conversation) {
        viewModelScope.launch {
            markArchivedUseCase(listOf(conversation.threadId))
            _events.emit(ConversationListEvent.Archived(conversation))
        }
    }

    internal fun undoArchive(threadId: Long) {
        viewModelScope.launch { markUnarchivedUseCase(listOf(threadId)) }
    }

    /** Hides [conversation] from the list right away but does not call [DeleteConversation] yet
     * -- see [ConversationListEvent.PendingDelete]. The caller (the screen's snackbar) must
     * eventually call [cancelPendingDelete] or [confirmPendingDelete]. */
    internal fun requestDelete(conversation: Conversation) {
        pendingDeleteThreadIds.update { it + conversation.threadId }
        viewModelScope.launch { _events.emit(ConversationListEvent.PendingDelete(conversation)) }
    }

    /** Snackbar's Undo was tapped in time -- nothing was ever actually deleted, so this just
     * un-hides the thread. */
    internal fun cancelPendingDelete(threadId: Long) {
        pendingDeleteThreadIds.update { it - threadId }
    }

    /** Undo window passed without being tapped -- now actually deletes. */
    internal fun confirmPendingDelete(threadId: Long) {
        viewModelScope.launch {
            deleteConversationUseCase(listOf(threadId))
            pendingDeleteThreadIds.update { it - threadId }
        }
    }

    internal fun toggleRead(conversation: Conversation) {
        viewModelScope.launch {
            if (conversation.hasUnread) markReadUseCase(listOf(conversation.threadId)) else markUnreadUseCase(listOf(conversation.threadId))
        }
    }
}
