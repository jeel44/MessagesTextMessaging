package text.message.sms.messaging.ui.screens.conversationlist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
import text.message.sms.messaging.domain.usecase.SyncContacts
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.util.isOtp
import text.message.sms.messaging.util.isPersonal
import text.message.sms.messaging.util.isTransaction
import javax.inject.Inject

/** The inbox's category chip row. Each category is an independent membership test over a
 * [Conversation] rather than a mutually-exclusive bucket -- e.g. a resolved contact's message
 * that happens to contain an OTP keyword can appear under both [PERSONAL] and [OTP] -- since
 * there's no per-message sender classification in the data model to assign exactly one category,
 * only heuristics run over [Conversation.recipients]/[Conversation.snippet]. See
 * [text.message.sms.messaging.util.isPersonal]/[text.message.sms.messaging.util.isTransaction]/
 * [text.message.sms.messaging.util.isOtp] (shared with [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s
 * personal/non-personal mode). */
internal enum class ConversationFilter { ALL, PERSONAL, TRANSACTIONS, OTP }

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
    private val syncContacts: SyncContacts,
    @param:ApplicationContext private val context: Context,
    private val providerChangeObserver: ProviderChangeObserver,
    private val swipeActionPreferences: SwipeActionPreferences,
    private val markArchivedUseCase: MarkArchived,
    private val markUnarchivedUseCase: MarkUnarchived,
    private val deleteConversationUseCase: DeleteConversation,
    private val markReadUseCase: MarkRead,
    private val markUnreadUseCase: MarkUnread,
) : ViewModel() {

    // Perf-pass breadcrumb (Logcat tag "NavPerf"), not read by any UI decision -- logs once, the
    // very first time observeInbox's pipeline actually emits, so "Home's first data load" can be
    // read off a real device without a profiler.
    private val createdAtMillis = SystemClock.elapsedRealtime()
    private var hasLoggedFirstLoad = false

    private val selectedFilter = MutableStateFlow(ConversationFilter.ALL)
    internal val filter: StateFlow<ConversationFilter> = selectedFilter.asStateFlow()

    private val _isDefaultSmsApp = MutableStateFlow(defaultSmsAppGuard.isDefault)
    internal val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    /** Seeded with the real permission state so the first [refreshContactsPermissionStatus] call
     * (right after this ViewModel is constructed) compares against where things actually stood,
     * not an assumed false. */
    private var hasContactsPermission = hasReadContactsPermission()

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
            ConversationFilter.PERSONAL -> visible.filter { it.isPersonal() }
            ConversationFilter.TRANSACTIONS -> visible.filter { it.isTransaction() }
            ConversationFilter.OTP -> visible.filter { it.isOtp() }
        }
    }.onEach {
        if (!hasLoggedFirstLoad) {
            hasLoggedFirstLoad = true
            Log.d("NavPerf", "Home first data load: ${SystemClock.elapsedRealtime() - createdAtMillis}ms")
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

    /** Call on every screen resume, same as [refreshDefaultSmsAppStatus]. Onboarding requests
     * READ_CONTACTS mid-session (see [text.message.sms.messaging.ui.screens.onboarding.WelcomeScreen])
     * rather than restarting the process, so [text.message.sms.messaging.MessagingApplication]'s
     * own launch-time check -- which ran before that grant even happened -- never sees it; without
     * this, a contact's name would only ever appear after the user force-restarts the app or opens
     * New Message (whose own [text.message.sms.messaging.ui.screens.newmessage.NewMessageViewModel.onContactsPermissionGranted]
     * happens to run the same sync as a side effect of an unrelated screen). */
    internal fun refreshContactsPermissionStatus() {
        val hasPermissionNow = hasReadContactsPermission()
        val justGranted = hasPermissionNow && !hasContactsPermission
        hasContactsPermission = hasPermissionNow
        if (justGranted) {
            viewModelScope.launch { syncContacts() }
        }
    }

    private fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

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
