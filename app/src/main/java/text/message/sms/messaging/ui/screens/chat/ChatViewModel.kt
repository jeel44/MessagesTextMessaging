package text.message.sms.messaging.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.datastore.SimPreferences
import text.message.sms.messaging.data.local.datastore.SimSendPreference
import text.message.sms.messaging.data.local.telephony.SimRepository
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.SimInfo
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.usecase.DeleteMessages
import text.message.sms.messaging.domain.usecase.MarkRead
import text.message.sms.messaging.domain.usecase.ResolveSendSubscription
import text.message.sms.messaging.domain.usecase.SendMessage
import text.message.sms.messaging.domain.usecase.SendSubscriptionResult
import text.message.sms.messaging.domain.usecase.SyncThreadPriority
import text.message.sms.messaging.ui.navigation.MessagingDestination
import text.message.sms.messaging.util.ChatOpenHint
import text.message.sms.messaging.util.isPersonalChat
import java.time.ZoneId
import javax.inject.Inject

/** [ChatScreen]'s personal/non-personal render mode -- see [ChatViewModel.chatMode]. Never
 * defaults to [PERSONAL]: [UNKNOWN] is the only state before the real answer (from a Home/Archived
 * tap's [ChatOpenHint], or otherwise [ChatViewModel]'s own [Conversation] load) is known, and
 * [ChatScreen] renders no call icon and no bottom area (neither composer nor security card) while
 * it's [UNKNOWN], so the mode can only ever be set once, correctly, never flip after the fact. */
internal enum class ChatMode { UNKNOWN, PERSONAL, NON_PERSONAL }

private fun Conversation.toChatMode(): ChatMode = if (isPersonalChat()) ChatMode.PERSONAL else ChatMode.NON_PERSONAL

/** [ChatViewModel.chatMessagesState] -- the single value [ChatScreen] gates
 * [text.message.sms.messaging.ui.screens.chat.ChatMessageList] on, replacing what used to be a
 * separate `hasLoadedInitialMessages: Boolean` collected alongside (but independently of) the
 * grouped list. [Loaded] is only ever produced together with the [ChatListItem]s it describes, in
 * the same map step, so there's no frame where a collector can see "loaded" without also seeing
 * the real list -- see [ChatViewModel.chatMessagesState]'s doc comment. */
internal sealed interface ChatMessagesState {
    data object Loading : ChatMessagesState
    data class Loaded(val items: List<ChatListItem>) : ChatMessagesState
}

internal sealed interface ChatEvent {
    data object MessageSent : ChatEvent

    /** The "always use SIM N" preference's SIM wasn't active, so this message went out on the
     * other SIM instead -- [slotNumber] (1-based) is the one it actually used. */
    data class SimFallback(val slotNumber: Int) : ChatEvent

    /** The selection top bar's Copy action built [text] (see [ChatViewModel.copySelected]) --
     * [ChatScreen] does the actual clipboard write and "Copied" toast, since neither belongs on a
     * ViewModel. */
    data class CopyRequested(val text: String) : ChatEvent

    /** The selection top bar's Forward action resolved [text] to hand off to the contact picker
     * -- [hadUnsupportedAttachments] is true when one or more selected messages carried a
     * non-text MMS part that got left out (see [ChatViewModel.forwardSelected]). */
    data class ForwardRequested(val text: String, val hadUnsupportedAttachments: Boolean) : ChatEvent

    /** The selection top bar's Share action -- exactly one of [text]/[imageContentUri] is set;
     * see [ChatViewModel.shareSelected]. */
    data class ShareRequested(val text: String?, val imageContentUri: String?) : ChatEvent

    /** [count] selected messages were deleted and the thread still has others left. */
    data class MessagesDeleted(val count: Int) : ChatEvent

    /** The selection top bar's Delete action emptied the whole thread -- the conversation itself
     * was removed too (see [ChatViewModel.deleteSelected]), so [ChatScreen] navigates back out. */
    data object ThreadDeleted : ChatEvent
}

/** A send the user asked for but that's waiting on [ChatViewModel.simPickerVisible] to resolve
 * which SIM to use -- held rather than lost while the picker is up. */
private data class PendingSend(val addresses: Set<String>, val body: String, val attachmentUri: String?)

/**
 * Backs [ChatScreen]. [messages] is a live view over [MessageRepository.observeThread] --
 * incoming messages and delivery-state updates from the sync/receive pipeline land here the
 * moment Room commits them, no manual refresh.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val sendMessage: SendMessage,
    private val markRead: MarkRead,
    private val syncThreadPriority: SyncThreadPriority,
    private val resolveSendSubscription: ResolveSendSubscription,
    private val simRepository: SimRepository,
    private val deleteMessages: DeleteMessages,
    simPreferences: SimPreferences,
) : ViewModel() {

    val threadId: Long = checkNotNull(savedStateHandle[MessagingDestination.ARG_THREAD_ID])

    // Starts small so the first frame never waits on a long thread's full history, then grows on
    // demand as ChatScreen calls loadOlderMessages() while the user scrolls toward the top --
    // flatMapLatest re-subscribes observeThreadPage at the new limit each time, which still lands
    // the user on the newest message exactly as before (only the amount of *history above* it
    // grows).
    private val messagePageSize = MutableStateFlow(INITIAL_MESSAGE_PAGE_SIZE)

    /** Whatever Home/Archived already knew about this thread at the moment it was tapped -- see
     * [ChatOpenHint]. Used as the seed for [conversation]/[chatMode] below so a hinted open never
     * shows a placeholder mode at all; `null` for every other entry point (deep link,
     * notification, search, forward, a brand-new thread), which fall back to [ChatMode.UNKNOWN]
     * until the real query resolves, same as before this existed. */
    private val openHint: Conversation? = ChatOpenHint.consume(threadId)

    private data class CoreState(val conversation: Conversation?, val messages: List<Message>)

    private val zoneId: ZoneId = ZoneId.systemDefault()

    // The single hot subscription behind both `chatMessagesState` and `coreState`/`messages`
    // below -- `null` is a real, distinct third state ("no real page has arrived yet"), never
    // confusable with `emptyList()` ("the page arrived and the thread is genuinely empty"), which
    // a plain `List<Message>` can't represent on its own. WhileSubscribed(5_000) means both
    // downstream collectors share the exact same Room query rather than each re-running it.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawMessagePage: StateFlow<List<Message>?> = messagePageSize
        .flatMapLatest { limit -> messageRepository.observeThreadPage(threadId, limit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Carries the "has the first page loaded" decision and the grouped list it's based on as ONE
    // value from ONE emission of `rawMessagePage`, so a collector can never observe one half
    // without the other -- unlike a separate boolean flag (set as a side effect on one flow) next
    // to a list derived through its own, differently-timed chain of combine/distinctUntilChanged/
    // stateIn stages, which is exactly what let ChatScreen previously see the flag flip on a frame
    // where the grouped list was still its emptyList() seed (see NavPerfTracer's "ChatListBlink"
    // log). ChatScreen holds off composing ChatMessageList (and its LazyColumn) until this is
    // Loaded, so the list is never first drawn before its real first-page contents are grouped and
    // ready -- see ChatMessageList's doc comment for why that matters. Loaded(emptyList()) is a
    // legitimate value (a genuinely empty thread), distinct from Loading (no real page yet).
    // Built by the free function below (rather than inline) so the exact same operator chain can
    // be driven directly by a fake `rawMessagePage` in a plain unit test -- see
    // ChatMessagesStateFlowTest -- without needing a full ChatViewModel and its Android-framework
    // dependencies (SimRepository/SimPreferences need a real or Robolectric Context).
    internal val chatMessagesState: StateFlow<ChatMessagesState> =
        chatMessagesStateFlow(viewModelScope, rawMessagePage, zoneId)

    // conversation and messages are combined into ONE upstream Flow, rather than each being its
    // own independent stateIn, so a screen collecting both (see ChatScreen) recomposes once per
    // meaningful change instead of once per Flow -- two Room queries that happen to resolve a few
    // milliseconds apart no longer show up as two separate partial frames.
    private val coreState: StateFlow<CoreState> = combine(
        conversationRepository.observeConversation(threadId),
        rawMessagePage.filterNotNull(),
    ) { conversation, messages -> CoreState(conversation, messages) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoreState(openHint, emptyList()))

    val messages: StateFlow<List<Message>> = coreState.map { it.messages }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversation: StateFlow<Conversation?> = coreState.map { it.conversation }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), openHint)

    /** [ChatScreen]'s personal/non-personal mode -- see [ChatMode]'s doc comment. Resolved from
     * [conversation] the instant it's non-null (immediately, for a hinted open); [ChatMode.UNKNOWN]
     * only in the narrow window before that, never [ChatMode.PERSONAL] by default. */
    internal val chatMode: StateFlow<ChatMode> = conversation
        .map { convo -> convo?.toChatMode() ?: ChatMode.UNKNOWN }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), openHint?.toChatMode() ?: ChatMode.UNKNOWN)

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _pendingAttachmentUri = MutableStateFlow<String?>(null)
    val pendingAttachmentUri: StateFlow<String?> = _pendingAttachmentUri.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 1)
    internal val events: SharedFlow<ChatEvent> = _events

    /** Live active-SIM list -- empty on every single-SIM/no-SIM/permission-not-granted device, in
     * which case every dual-SIM affordance below ([simPickerVisible], the composer's SIM badge)
     * stays entirely hidden. */
    val activeSims: StateFlow<List<SimInfo>> = simRepository.activeSims
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sendPreference: StateFlow<SimSendPreference> = simPreferences.sendPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimSendPreference.ASK)

    /** 0-based slot the composer's SIM badge shows -- the same slot [ResolveSendSubscription]
     * would currently pick, computed without side effects purely for display. Null on a
     * single-SIM device, or in "Ask" mode before any SIM has been chosen for this thread yet. */
    val simIndicatorSlot: StateFlow<Int?> = combine(activeSims, sendPreference, conversation) { sims, preference, convo ->
        if (sims.size < 2) return@combine null
        when (preference) {
            SimSendPreference.SLOT_0 -> sims.firstOrNull { it.slotIndex == 0 }?.slotIndex ?: sims.first().slotIndex
            SimSendPreference.SLOT_1 -> sims.firstOrNull { it.slotIndex == 1 }?.slotIndex ?: sims.first().slotIndex
            SimSendPreference.ASK -> convo?.subscriptionSlot?.takeIf { slot -> sims.any { it.slotIndex == slot } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _simPickerVisible = MutableStateFlow(false)
    /** Whether the "Send with" SIM sheet is up -- either because [send] hit
     * [SendSubscriptionResult.NeedsUserChoice], or the user tapped the composer's SIM badge. */
    val simPickerVisible: StateFlow<Boolean> = _simPickerVisible.asStateFlow()

    private var pendingSend: PendingSend? = null

    /** [Set] of selected message ids -- non-empty exactly when the screen is in multi-select
     * mode. Lives here (not `SavedStateHandle`) the same way [messageText]/[pendingAttachmentUri]
     * do: it survives rotation because this ViewModel does, but not process death, matching every
     * other piece of in-progress Chat screen state. Cleared whenever a new [ChatViewModel] is
     * created for a different thread (a fresh instance always starts empty), and explicitly by
     * [clearSelection] after back/close, delete, copy, forward or share. */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    init {
        // A Forward hands its prefilled body straight through SavedStateHandle, the same way
        // threadId itself arrives -- see MessagingDestination.Chat's ARG_INITIAL_TEXT doc.
        val initialText: String? = savedStateHandle[MessagingDestination.ARG_INITIAL_TEXT]
        if (!initialText.isNullOrEmpty()) {
            _messageText.value = MessagingDestination.Chat.decodeInitialText(initialText)
        }

        viewModelScope.launch { markRead(listOf(threadId)) }
        // Cheap even when this thread is already fully synced (a quick batched existence check
        // short-circuits it) -- see SyncThreadPriority's doc. Ensures a thread the background
        // sync hasn't reached yet still shows its recent history immediately, matching
        // INITIAL_MESSAGE_PAGE_SIZE below so nothing beyond the first frame's needs is imported
        // just to open the thread.
        viewModelScope.launch { syncThreadPriority(threadId, INITIAL_MESSAGE_PAGE_SIZE) }
    }

    /** Long-press on a bubble that isn't already in selection mode: enters selection mode with
     * just that one message selected. */
    fun startSelection(messageId: Long) {
        _selectedIds.value = setOf(messageId)
    }

    /** A tap (in selection mode) or a long-press (any time) on a bubble: adds or removes just
     * that message from the selection. */
    fun toggleSelection(messageId: Long) {
        _selectedIds.update { current -> if (messageId in current) current - messageId else current + messageId }
    }

    /** Selects every message [ChatScreen] currently has loaded -- never triggers
     * [loadOlderMessages], matching the "don't pull in the rest of a paginated thread just to
     * select all of it" rule. */
    fun selectAllLoaded() {
        _selectedIds.value = messages.value.map { it.id }.toSet()
    }

    /** Exits selection mode -- system back, the top bar's close icon, and every selection action
     * below all funnel through this. */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** The currently-selected messages, in [messages]' own oldest-first order -- every selection
     * action below builds its text from this. */
    private fun selectedMessagesInOrder(): List<Message> {
        val ids = _selectedIds.value
        return messages.value.filter { it.id in ids }
    }

    /** Non-blank bodies of the selected messages, oldest to newest, blank-line separated -- the
     * shared text shape Copy/Forward/Share all build (see each of their doc comments for how they
     * differ beyond that). `null` when nothing selected has any text at all. */
    private fun joinedSelectedText(): String? {
        val texts = selectedMessagesInOrder().map { it.body }.filter { it.isNotBlank() }
        if (texts.isEmpty()) return null
        return texts.joinToString(separator = "\n\n")
    }

    /** Copies every selected message's text (see [joinedSelectedText]) and exits selection --
     * a no-op if nothing selected has any text, so the top bar's Copy icon should be disabled in
     * that case rather than relying on this silently doing nothing. */
    fun copySelected() {
        val text = joinedSelectedText() ?: return
        viewModelScope.launch { _events.emit(ChatEvent.CopyRequested(text)) }
        clearSelection()
    }

    /** Builds a Forward hand-off from the selected messages' text parts (see [joinedSelectedText])
     * and exits selection. A selected MMS message's non-text parts are left out -- this app's
     * compose flow has nowhere to carry more than one already-pending attachment, and the contact
     * picker Forward opens next accepts no attachment at all -- and [ChatEvent.ForwardRequested
     * .hadUnsupportedAttachments] tells [ChatScreen] to say so. */
    fun forwardSelected() {
        val selected = selectedMessagesInOrder()
        if (selected.isEmpty()) return
        val hadUnsupportedAttachments = selected.any { message -> message.attachments.any { !it.isTextPart } }
        val text = selected.joinToString(separator = "\n\n") { it.body }.trim()
        viewModelScope.launch { _events.emit(ChatEvent.ForwardRequested(text, hadUnsupportedAttachments)) }
        clearSelection()
    }

    /** Shares the selection via the system share sheet and exits selection. A single MMS image
     * message with no text of its own shares the image itself (its attachment's `content://`
     * Uri); every other case shares the joined text (see [joinedSelectedText]) the same shape
     * Copy/Forward use. */
    fun shareSelected() {
        val selected = selectedMessagesInOrder()
        if (selected.isEmpty()) return

        val onlyMessage = selected.singleOrNull()
        val soleImage = onlyMessage
            ?.takeIf { it.body.isBlank() }
            ?.attachments
            ?.singleOrNull { it.isImage }
            ?.contentUri

        val event = if (soleImage != null) {
            ChatEvent.ShareRequested(text = null, imageContentUri = soleImage)
        } else {
            ChatEvent.ShareRequested(text = selected.joinToString(separator = "\n\n") { it.body }, imageContentUri = null)
        }
        viewModelScope.launch { _events.emit(event) }
        clearSelection()
    }

    /** Deletes the selected messages -- from Room *and* the system Telephony provider, see
     * [MessageRepository.delete] -- then either recomputes the conversation's snippet/last-message
     * time (still-nonempty thread) or removes the conversation entirely and tells [ChatScreen] to
     * navigate back out (now-empty thread). Exits selection either way. */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            deleteMessages(ids)
            val remaining = messageRepository.countForThread(threadId)
            if (remaining == 0) {
                conversationRepository.delete(listOf(threadId))
                _events.emit(ChatEvent.ThreadDeleted)
            } else {
                messageRepository.findLatestForThread(threadId)?.let { latest ->
                    val snippet = latest.body.ifBlank { latest.attachments.firstOrNull()?.fileName.orEmpty() }
                    val timestampMillis = if (latest.isOutgoing) latest.sentAtMillis else latest.receivedAtMillis
                    conversationRepository.setLastMessage(threadId, snippet, timestampMillis)
                }
                _events.emit(ChatEvent.MessagesDeleted(ids.size))
            }
            clearSelection()
        }
    }

    /** Widens [messagePageSize] so the next emission from [messages] includes an older page --
     * called by [ChatScreen] once the user scrolls near the top of what's currently loaded.
     * Harmless to call past the thread's actual length: the underlying `LIMIT` just returns
     * everything the thread has. */
    fun loadOlderMessages() {
        messagePageSize.update { it + MESSAGE_PAGE_INCREMENT }
    }

    fun onMessageTextChanged(text: String) {
        _messageText.value = text
    }

    fun onAttachmentSelected(uri: String?) {
        _pendingAttachmentUri.value = uri
    }

    fun clearAttachment() {
        _pendingAttachmentUri.value = null
    }

    fun send() {
        // Guards against a double-fire while an earlier call is still waiting on the SIM picker
        // -- resolved the moment that picker resolves ([onSimPicked]) or is dismissed
        // ([onSimPickerDismissed]).
        if (_simPickerVisible.value) return
        val body = _messageText.value.trim()
        val attachmentUri = _pendingAttachmentUri.value
        if (body.isEmpty() && attachmentUri == null) return

        val addresses = conversation.value?.recipients?.map { it.address }?.toSet()
            ?: messages.value.firstOrNull { it.address != null }?.address?.let(::setOf)
            ?: return

        viewModelScope.launch {
            when (val result = resolveSendSubscription(threadId)) {
                is SendSubscriptionResult.Resolved -> doSend(addresses, body, attachmentUri, result.subscriptionId)
                is SendSubscriptionResult.FellBack -> {
                    doSend(addresses, body, attachmentUri, result.subscriptionId)
                    val slotNumber = activeSims.value.firstOrNull { it.subscriptionId == result.subscriptionId }
                        ?.slotNumber ?: 1
                    _events.emit(ChatEvent.SimFallback(slotNumber))
                }
                SendSubscriptionResult.NeedsUserChoice -> {
                    // Text/attachment stay exactly as the user left them -- nothing is cleared,
                    // and nothing sends, until onSimPicked resolves this.
                    pendingSend = PendingSend(addresses, body, attachmentUri)
                    _simPickerVisible.value = true
                }
            }
        }
    }

    /** [ChatScreen]'s composer SIM badge -- opens the same picker [send] uses for "Ask every
     * time", but with no message waiting: picking a SIM here just changes what the *next* send
     * uses (and, if remembered, this thread's "Ask" choice), rather than sending anything now. */
    fun onSimBadgeClick() {
        if (activeSims.value.size < 2 || _simPickerVisible.value) return
        _simPickerVisible.value = true
    }

    /** The user picked [sim] from the SIM sheet -- either to actually send [pendingSend], or (from
     * [onSimBadgeClick]) just to change the thread's remembered choice ahead of time. */
    fun onSimPicked(sim: SimInfo, remember: Boolean) {
        viewModelScope.launch {
            if (remember) conversationRepository.setSubscriptionSlot(threadId, sim.slotIndex)
            _simPickerVisible.value = false
            val send = pendingSend
            if (send != null) {
                pendingSend = null
                doSend(send.addresses, send.body, send.attachmentUri, sim.subscriptionId)
            }
        }
    }

    /** Dismissing the SIM sheet cancels whatever [send] was waiting on it -- the composer's text
     * and attachment are untouched, so the user can just try again. */
    fun onSimPickerDismissed() {
        _simPickerVisible.value = false
        pendingSend = null
    }

    private suspend fun doSend(addresses: Set<String>, body: String, attachmentUri: String?, subscriptionId: Int) {
        sendMessage(
            SendMessage.Params(
                addresses = addresses,
                body = body,
                threadId = threadId,
                subscriptionId = subscriptionId,
                attachmentUris = listOfNotNull(attachmentUri),
            ),
        )
        _messageText.value = ""
        _pendingAttachmentUri.value = null
        _events.emit(ChatEvent.MessageSent)
    }
}

/** Builds [ChatViewModel.chatMessagesState] from [rawMessagePage] -- a free function (rather than
 * inline in the class) so a unit test can drive this exact operator chain with a fake
 * [rawMessagePage] and assert it never yields a [ChatMessagesState.Loaded] with an empty
 * [ChatMessagesState.Loaded.items] for a page that actually had messages, without constructing a
 * whole [ChatViewModel] (which needs Android-framework objects -- see
 * [text.message.sms.messaging.data.local.telephony.SimRepository]/
 * [text.message.sms.messaging.data.local.datastore.SimPreferences] -- unavailable to a plain JVM
 * unit test in this project). [rawMessagePage] being `null` (not yet loaded) versus `emptyList()`
 * (loaded, genuinely empty) is what lets this map step alone decide [ChatMessagesState.Loading]
 * vs [ChatMessagesState.Loaded] -- see [ChatViewModel.rawMessagePage]'s doc comment. */
internal fun chatMessagesStateFlow(
    scope: CoroutineScope,
    rawMessagePage: StateFlow<List<Message>?>,
    zoneId: ZoneId,
): StateFlow<ChatMessagesState> = rawMessagePage
    .map<List<Message>?, ChatMessagesState> { page ->
        if (page == null) ChatMessagesState.Loading else ChatMessagesState.Loaded(groupMessages(page, zoneId))
    }
    .distinctUntilChanged()
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ChatMessagesState.Loading)

/** Messages loaded on a chat's very first frame -- enough to fill and comfortably overscroll a
 * typical screen without paying to load/map a long thread's entire history up front. */
private const val INITIAL_MESSAGE_PAGE_SIZE = 40

/** How many more messages [ChatViewModel.loadOlderMessages] pulls in per call. */
private const val MESSAGE_PAGE_INCREMENT = 40
