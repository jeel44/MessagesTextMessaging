package text.message.sms.messaging.data.repository

import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.data.local.db.dao.AttachmentDao
import text.message.sms.messaging.data.local.provider.MmsAttachmentStorage
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.data.mapper.toEntity
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.repository.AttachmentRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [AttachmentRepository]. */
@Singleton
class LocalAttachmentRepository @Inject constructor(
    private val attachmentDao: AttachmentDao,
    private val attachmentStorage: MmsAttachmentStorage,
) : AttachmentRepository {

    override fun observeForMessage(messageId: Long): Flow<List<Attachment>> =
        attachmentDao.observeForMessage(messageId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findForMessage(messageId: Long): List<Attachment> =
        attachmentDao.findForMessage(messageId).map { it.toDomain() }

    override fun observeMediaForThread(threadId: Long): Flow<List<Attachment>> =
        attachmentDao.observeMediaForThread(threadId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun insertAll(attachments: List<Attachment>) {
        attachmentDao.upsertAll(attachments.map { it.toEntity() })
    }

    override suspend fun exportToGallery(attachment: Attachment): String {
        val sourceUri = requireNotNull(attachment.contentUri) {
            "Attachment ${attachment.id} has no file to export (it is inline text)"
        }
        val displayName = attachment.fileName ?: "attachment_${attachment.id}"
        val exported = attachmentStorage.exportToMediaStore(
            source = sourceUri.toUri(),
            mimeType = attachment.mimeType,
            displayName = displayName,
        )
        return requireNotNull(exported) {
            "Export to shared storage needs Android 10 (API 29) or newer"
        }.toString()
    }

    override suspend fun delete(attachmentIds: Collection<Long>) {
        attachmentDao.delete(attachmentIds)
    }
}
