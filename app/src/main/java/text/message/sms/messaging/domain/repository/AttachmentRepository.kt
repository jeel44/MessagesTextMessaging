package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.Attachment

/** Access to MMS parts. */
interface AttachmentRepository {

    fun observeForMessage(messageId: Long): Flow<List<Attachment>>

    suspend fun findForMessage(messageId: Long): List<Attachment>

    /** Every image/video ever sent or received in [threadId], newest first -- backs the shared
     * media grid on the conversation info screen. */
    fun observeMediaForThread(threadId: Long): Flow<List<Attachment>>

    suspend fun insertAll(attachments: List<Attachment>)

    /** Copies [attachment] into shared storage and returns the resulting uri. */
    suspend fun exportToGallery(attachment: Attachment): String

    suspend fun delete(attachmentIds: Collection<Long>)
}
