package text.message.sms.messaging.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.usecase.MarkRead
import text.message.sms.messaging.domain.usecase.SendMessage
import text.message.sms.messaging.ui.navigation.MessagingDestination
import javax.inject.Inject

internal sealed interface ChatEvent {
    data object MessageSent : ChatEvent
}

/**
 * Backs [ChatScreen]. [messages] is a live view over [MessageRepository.observeThread] --
 * incoming messages and delivery-state updates from the sync/receive pipeline land here the
 * moment Room commits them, no manual refresh.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    conversationRepository: ConversationRepository,
    private val sendMessage: SendMessage,
    private val markRead: MarkRead,
) : ViewModel() {

    val threadId: Long = checkNotNull(savedStateHandle[MessagingDestination.ARG_THREAD_ID])

    val messages: StateFlow<List<Message>> = messageRepository.observeThread(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversation: StateFlow<Conversation?> = conversationRepository.observeConversation(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _pendingAttachmentUri = MutableStateFlow<String?>(null)
    val pendingAttachmentUri: StateFlow<String?> = _pendingAttachmentUri.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 1)
    internal val events: SharedFlow<ChatEvent> = _events

    init {
        viewModelScope.launch { markRead(listOf(threadId)) }
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
        val body = _messageText.value.trim()
        val attachmentUri = _pendingAttachmentUri.value
        if (body.isEmpty() && attachmentUri == null) return

        val addresses = conversation.value?.recipients?.map { it.address }?.toSet()
            ?: messages.value.firstOrNull { it.address != null }?.address?.let(::setOf)
            ?: return

        viewModelScope.launch {
            sendMessage(
                SendMessage.Params(
                    addresses = addresses,
                    body = body,
                    threadId = threadId,
                    attachmentUris = listOfNotNull(attachmentUri),
                ),
            )
            _messageText.value = ""
            _pendingAttachmentUri.value = null
            _events.emit(ChatEvent.MessageSent)
        }
    }
}
