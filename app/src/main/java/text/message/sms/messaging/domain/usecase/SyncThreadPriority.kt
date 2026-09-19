package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.SyncRepository
import javax.inject.Inject

/** Imports one thread's newest messages right away, ahead of the background [SyncMessages] walk
 * -- see [text.message.sms.messaging.ui.screens.chat.ChatViewModel], which calls this when a
 * Chat screen opens so that thread's recent history is on screen immediately even on a device
 * where the initial bulk sync hasn't reached it yet. */
class SyncThreadPriority @Inject constructor(
    private val syncRepository: SyncRepository,
) : UseCase {

    suspend operator fun invoke(threadId: Long, limit: Int = 40) {
        syncRepository.syncThreadPriority(threadId, limit)
    }
}
