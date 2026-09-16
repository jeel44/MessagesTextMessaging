package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.data.local.db.dao.AttachmentDao
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
) : AttachmentRepository {

    override fun observeForMessage(messageId: Long): Flow<List<Attachment>> =
        attachmentDao.observeForMessage(messageId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findForMessage(messageId: Long): List<Attachment> =
        attachmentDao.findForMessage(messageId).map { it.toDomain() }

    override suspend fun insertAll(attachments: List<Attachment>) {
        attachmentDao.upsertAll(attachments.map { it.toEntity() })
    }

    override suspend fun exportToGallery(attachment: Attachment): String {
        TODO("Saving parts to shared storage arrives with the attachment pipeline")
    }

    override suspend fun delete(attachmentIds: Collection<Long>) {
        attachmentDao.delete(attachmentIds)
    }
}
