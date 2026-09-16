package text.message.sms.messaging.data.local.provider.mms

import kotlin.random.Random

/**
 * Builds an M-Send.req PDU: the message this app hands to `SmsManager.sendMultimediaMessage`,
 * which takes care of the actual HTTP POST to the carrier's MMSC. Only the PDU bytes are our
 * responsibility.
 *
 * The encoder always uses the plain text-string (Extension-Media) form for Content-Type rather
 * than the well-known short-integer table: it costs a handful of extra bytes per part and
 * completely sidesteps any risk of a wrong table index corrupting an outgoing message, which
 * matters more here than on the decode side where a wrong guess only degrades one field.
 */
object MmsPduEncoder {

    fun encodeSendRequest(request: MmsSendRequest): ByteArray {
        val writer = PduWriter()

        writer.writeShortInteger(MmsFieldCode.MESSAGE_TYPE)
        writer.writeShortInteger(MmsMessageType.SEND_REQ)

        writer.writeShortInteger(MmsFieldCode.TRANSACTION_ID)
        writer.writeTextString(newTransactionId())

        writer.writeShortInteger(MmsFieldCode.MMS_VERSION)
        writer.writeShortInteger(MMS_VERSION_1_3)

        // Insert-address-token: let the carrier fill in our own MSISDN rather than us having to
        // know it, which is not reliably available on a dual-SIM or eSIM device anyway.
        writer.writeShortInteger(MmsFieldCode.FROM)
        writer.writeValueLength(1)
        writer.writeShortInteger(MmsAddressToken.INSERT_ADDRESS)

        request.to.forEach { address ->
            writer.writeShortInteger(MmsFieldCode.TO)
            writer.writeTextString("$address/TYPE=PLMN")
        }

        request.subject?.let { subject ->
            writer.writeShortInteger(MmsFieldCode.SUBJECT)
            writer.writeTextString(subject)
        }

        writer.writeShortInteger(MmsFieldCode.CONTENT_TYPE)
        writer.writeValueLength(TOP_LEVEL_CONTENT_TYPE.length + 1)
        writer.writeTextString(TOP_LEVEL_CONTENT_TYPE)

        writer.writeUintvar(request.parts.size.toLong())
        request.parts.forEach { part -> writePart(writer, part) }

        return writer.toByteArray()
    }

    private fun writePart(writer: PduWriter, part: MmsPart) {
        val header = PduWriter()
        writeContentTypeWithName(header, part.contentType, part.name)
        val headerBytes = header.toByteArray()

        writer.writeUintvar(headerBytes.size.toLong())
        writer.writeUintvar(part.data.size.toLong())
        writer.writeBytes(headerBytes)
        writer.writeBytes(part.data)
    }

    private fun writeContentTypeWithName(writer: PduWriter, contentType: String, name: String?) {
        if (name == null) {
            // Bare Extension-Media: no value-length wrapper needed without parameters.
            writer.writeTextString(contentType)
            return
        }

        val body = PduWriter()
        body.writeTextString(contentType)
        body.writeByte(MmsContentTypeParam.NAME)
        body.writeTextString(name)
        val bodyBytes = body.toByteArray()

        writer.writeValueLength(bodyBytes.size)
        writer.writeBytes(bodyBytes)
    }

    private fun newTransactionId(): String =
        "${System.currentTimeMillis().toString(RADIX)}${Random.nextInt(0, TX_ID_SALT_BOUND)}"

    private const val TOP_LEVEL_CONTENT_TYPE = "application/vnd.wap.multipart.related"
    private const val RADIX = 36
    private const val TX_ID_SALT_BOUND = 0xFFFF
}
