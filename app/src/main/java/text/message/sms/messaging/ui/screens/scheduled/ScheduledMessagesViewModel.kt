package text.message.sms.messaging.ui.screens.scheduled

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.model.ScheduledStatus
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import text.message.sms.messaging.domain.usecase.CancelQueuedMessage
import text.message.sms.messaging.domain.usecase.ScheduledSendTrigger
import text.message.sms.messaging.domain.usecase.SendScheduledMessage
import javax.inject.Inject

/** One row of [ScheduledMessagesScreen]. */
@Immutable
internal data class ScheduledListItem(
    val messageId: Long,
    val threadId: Long,
    val recipient: String,
    val body: String,
    val sendAtMillis: Long,
    val status: ScheduledStatus,
)

/** Backs [ScheduledMessagesScreen]. */
@HiltViewModel
internal class ScheduledMessagesViewModel @Inject constructor(
    scheduledMessageRepository: ScheduledMessageRepository,
    private val conversationRepository: ConversationRepository,
    private val sendScheduledMessage: SendScheduledMessage,
    private val cancelQueuedMessage: CancelQueuedMessage,
) : ViewModel() {

    private val recipientNames = mutableMapOf<Long, String>()

    /** Null until the first list has loaded, so the screen never flashes its empty state. */
    val items: StateFlow<List<ScheduledListItem>?> = scheduledMessageRepository.observeAll()
        .map { scheduled ->
            scheduled.map { item ->
                val message = item.message
                ScheduledListItem(
                    messageId = message.id,
                    threadId = message.threadId,
                    recipient = recipientNames.getOrPut(message.threadId) {
                        conversationRepository.findByThreadId(message.threadId)?.title
                            ?: message.address.orEmpty()
                    },
                    body = message.body,
                    sendAtMillis = item.sendAtMillis,
                    status = item.status,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun sendNow(messageId: Long) {
        viewModelScope.launch { sendScheduledMessage(messageId, ScheduledSendTrigger.SEND_NOW) }
    }

    fun delete(messageId: Long) {
        viewModelScope.launch { cancelQueuedMessage(messageId) }
    }
}
