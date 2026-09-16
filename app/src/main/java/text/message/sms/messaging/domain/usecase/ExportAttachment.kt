package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.repository.AttachmentRepository
import javax.inject.Inject

/** Copies an MMS part out of app storage and into the shared media collection. */
class ExportAttachment @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
) : UseCase {

    suspend operator fun invoke(attachment: Attachment): String =
        attachmentRepository.exportToGallery(attachment)
}
