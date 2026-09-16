package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** Stores the unsent text for a thread, clearing it when [draft] is blank. */
class SaveDraft @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadId: Long, draft: String) {
        conversationRepository.saveDraft(threadId, draft.ifBlank { null })
    }
}
