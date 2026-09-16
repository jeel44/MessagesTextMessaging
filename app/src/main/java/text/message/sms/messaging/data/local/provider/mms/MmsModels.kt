package text.message.sms.messaging.data.local.provider.mms

/** The M-Notification.ind a WAP push delivers: "a message is waiting, fetch it from here". */
data class MmsNotification(
    val transactionId: String,
    val contentLocation: String,
    val expiryEpochSeconds: Long?,
    val messageSizeBytes: Long?,
    val from: String?,
    val subject: String?,
)

/** One decoded body part -- text, an image, audio, video, or an SMIL layout document. */
data class MmsPart(
    val contentType: String,
    val name: String?,
    val contentId: String?,
    val data: ByteArray,
) {
    val isText: Boolean
        get() = contentType.startsWith("text/") || contentType == "application/smil"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is MmsPart &&
                contentType == other.contentType &&
                name == other.name &&
                contentId == other.contentId &&
                data.contentEquals(other.data)
            )

    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (contentId?.hashCode() ?: 0)
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/** The M-Retrieve.conf downloaded from the MMSC once we act on a [MmsNotification]. */
data class MmsRetrieved(
    val transactionId: String,
    val messageId: String?,
    val subject: String?,
    val from: String?,
    val to: List<String>,
    val cc: List<String>,
    val dateEpochSeconds: Long,
    val parts: List<MmsPart>,
)

/** Everything needed to build an outgoing M-Send.req. */
data class MmsSendRequest(
    val to: List<String>,
    val subject: String?,
    val parts: List<MmsPart>,
)
