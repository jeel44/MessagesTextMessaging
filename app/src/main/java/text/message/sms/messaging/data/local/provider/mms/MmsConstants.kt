package text.message.sms.messaging.data.local.provider.mms

/**
 * Numeric constants assigned by the MMS Encapsulation spec (OMA-WAP-209-MMS-Encapsulation) and
 * the WSP content-type registry it reuses. These are protocol identifiers, not application
 * logic -- every correct MMS implementation on any platform has to agree on the same numbers,
 * the same way every HTTP client agrees that 404 means Not Found. Cross-checked against the
 * public, Apache-2.0-licensed constant tables AOSP ships in `PduHeaders`, `PduContentTypes` and
 * `PduPart` (frameworks/opt/mms) -- referenced here only for the numbers themselves; the
 * decode/encode logic built on top is written fresh in [MmsPduDecoder] and [MmsPduEncoder].
 */
internal object MmsFieldCode {
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPORT = 0x90
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98
}

internal object MmsMessageType {
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86
}

internal object MmsExpiryToken {
    const val ABSOLUTE = 0x80
    const val RELATIVE = 0x81
}

internal object MmsAddressToken {
    const val ADDRESS_PRESENT = 0x80
    const val INSERT_ADDRESS = 0x81
}

/** Well-known Content-Type parameter codes (the subset MMS parts actually use). */
internal object MmsContentTypeParam {
    const val CHARSET = 0x81
    const val NAME_LEGACY = 0x85
    const val FILENAME_LEGACY = 0x86
    const val NAME = 0x97
    const val FILENAME = 0x98
}

/** MMS protocol version this app declares when sending: 1.3, the version every carrier still
 * in service supports. Encoded as (major << 4 | minor) with the short-integer top bit set. */
internal const val MMS_VERSION_1_3 = 0x80 or 0x13

/**
 * The well-known media-type table WSP content-type headers index into when a sender chooses
 * the compact (short-integer) form instead of spelling the type out as text. Only entries an
 * MMS part or top-level Content-Type realistically carries are listed; anything else falls back
 * to the header's raw byte value formatted as `application/octet-stream`, which never blocks
 * parsing -- the part's declared byte length still bounds how many bytes are consumed either
 * way (see [MmsPduDecoder]).
 */
internal val WELL_KNOWN_MEDIA_TYPES: Map<Int, String> = mapOf(
    0 to "*/*",
    1 to "text/*",
    2 to "text/html",
    3 to "text/plain",
    6 to "text/x-vCalendar",
    7 to "text/x-vCard",
    12 to "multipart/mixed",
    15 to "multipart/alternative",
    29 to "image/gif",
    30 to "image/jpeg",
    31 to "image/tiff",
    32 to "image/png",
    33 to "image/vnd.wap.wbmp",
    39 to "application/xml",
    40 to "text/xml",
    51 to "application/vnd.wap.multipart.related",
    61 to "text/css",
    62 to "application/vnd.wap.mms-message",
    79 to "audio/*",
    80 to "video/*",
)

internal val WELL_KNOWN_MEDIA_TYPE_CODES: Map<String, Int> =
    WELL_KNOWN_MEDIA_TYPES.entries.associate { (code, type) -> type to code }
