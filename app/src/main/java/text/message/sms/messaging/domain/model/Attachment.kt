package text.message.sms.messaging.domain.model

import androidx.compose.runtime.Immutable

/** A single MMS part: an image, video, audio clip, vCard or text segment. */
@Immutable
data class Attachment(
    val id: Long,
    val messageId: Long,
    val mimeType: String,
    val fileName: String?,
    val contentUri: String?,
    val byteSize: Long,
    val text: String?,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isTextPart: Boolean get() = mimeType.startsWith("text/")
}
