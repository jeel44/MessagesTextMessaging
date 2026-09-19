package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Deletes whole threads, along with every message they contain -- from the local cache *and* the
 * system Telephony provider, the same [MessageRepository.delete] cleanup
 * [text.message.sms.messaging.ui.screens.chat.ChatViewModel.deleteSelected] already relies on for
 * a single message. Each thread's messages are routed through [MessageRepository.delete] before
 * [ConversationRepository.delete] removes the (now-empty) conversation row: [MessageRepository
 * .delete] reads each message's provider id off its still-present Room row, so it must run before
 * that row's own removal (whether it goes via this direct call or -- if this use case is skipped
 * entirely -- an unrelated cascade) leaves nothing to look the provider id up from. Without this,
 * only the Room side is ever deleted, and a later incremental sync -- which discovers a thread's
 * messages by walking the *provider*, never by trusting the local cache -- would simply re-import
 * everything and resurrect the "deleted" conversation.
 */
class DeleteConversation @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(threadIds: Collection<Long>) {
        if (threadIds.isEmpty()) return
        val messageIds = messageRepository.findIdsForThreads(threadIds)
        if (messageIds.isNotEmpty()) messageRepository.delete(messageIds)
        conversationRepository.delete(threadIds)
    }
}
