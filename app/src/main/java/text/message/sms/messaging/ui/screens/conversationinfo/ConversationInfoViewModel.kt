package text.message.sms.messaging.ui.screens.conversationinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.AttachmentRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.usecase.DeleteConversation
import text.message.sms.messaging.domain.usecase.MarkArchived
import text.message.sms.messaging.domain.usecase.MarkBlocked
import text.message.sms.messaging.domain.usecase.MarkMuted
import text.message.sms.messaging.domain.usecase.MarkUnarchived
import text.message.sms.messaging.domain.usecase.MarkUnblocked
import text.message.sms.messaging.ui.navigation.MessagingDestination
import javax.inject.Inject

/** Fired once the current thread has been removed from the normal inbox (archived, blocked, or
 * deleted) so [ConversationInfoScreen] can pop all the way back out to it -- staying on a chat
 * whose thread you just deleted or blocked would show a broken or pointless view. */
internal sealed interface ConversationInfoEvent {
    data object LeaveConversation : ConversationInfoEvent
}

/**
 * Backs [ConversationInfoScreen]. Both [conversation] and [sharedMedia] are live views -- over
 * [ConversationRepository.observeConversation] and
 * [AttachmentRepository.observeMediaForThread] respectively -- so a mute toggled elsewhere or a
 * new photo arriving mid-visit updates this screen without a manual refresh, the same pattern
 * [text.message.sms.messaging.ui.screens.chat.ChatViewModel] uses for the thread it backs.
 */
@HiltViewModel
class ConversationInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    conversationRepository: ConversationRepository,
    attachmentRepository: AttachmentRepository,
    private val markMuted: MarkMuted,
    private val markArchived: MarkArchived,
    private val markUnarchived: MarkUnarchived,
    private val markBlocked: MarkBlocked,
    private val markUnblocked: MarkUnblocked,
    private val deleteConversation: DeleteConversation,
) : ViewModel() {

    private val threadId: Long = checkNotNull(savedStateHandle[MessagingDestination.ARG_THREAD_ID])

    val conversation: StateFlow<Conversation?> = conversationRepository.observeConversation(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sharedMedia: StateFlow<List<Attachment>> = attachmentRepository.observeMediaForThread(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<ConversationInfoEvent>(extraBufferCapacity = 1)
    internal val events: SharedFlow<ConversationInfoEvent> = _events

    internal fun setMuted(muted: Boolean) {
        viewModelScope.launch { markMuted(listOf(threadId), muted) }
    }

    /** Archiving hides the thread from the inbox, so only that direction leaves the screen --
     * un-archiving from in here (an unlikely path today, with no archived-list screen yet to have
     * reached this one from) leaves the user right where they were. */
    internal fun toggleArchived() {
        val archiving = conversation.value?.isArchived != true
        viewModelScope.launch {
            if (archiving) markArchived(listOf(threadId)) else markUnarchived(listOf(threadId))
            if (archiving) _events.emit(ConversationInfoEvent.LeaveConversation)
        }
    }

    /** Same asymmetry as [toggleArchived]: blocking hides the thread and stops it receiving new
     * messages, so it leaves the screen; unblocking doesn't. */
    internal fun toggleBlocked() {
        val blocking = conversation.value?.isBlocked != true
        viewModelScope.launch {
            if (blocking) markBlocked(listOf(threadId)) else markUnblocked(listOf(threadId))
            if (blocking) _events.emit(ConversationInfoEvent.LeaveConversation)
        }
    }

    internal fun delete() {
        viewModelScope.launch {
            deleteConversation(listOf(threadId))
            _events.emit(ConversationInfoEvent.LeaveConversation)
        }
    }
}
