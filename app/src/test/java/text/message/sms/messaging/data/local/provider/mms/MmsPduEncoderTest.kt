package text.message.sms.messaging.data.local.provider.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MmsPduEncoder] builds an M-Send.req, a PDU type [MmsPduDecoder] never needs to read back (this
 * app only ever sends that type, never decodes one), so there is no round-trip through the
 * production decoder to lean on. Instead this walks the encoded bytes with the same low-level
 * [PduReader] primitives a decoder would use, checking the header sequence and part framing are
 * structurally exactly what the MMS Encapsulation spec requires.
 */
class MmsPduEncoderTest {

    @Test
    fun `encodes the header sequence a send-req requires, in order`() {
        val request = MmsSendRequest(
            to = listOf("+15551234567"),
            subject = "Trip photos",
            parts = listOf(MmsPart("text/plain", "body.txt", null, "Hi!".toByteArray())),
        )

        val reader = PduReader(MmsPduEncoder.encodeSendRequest(request))

        assertEquals(MmsFieldCode.MESSAGE_TYPE, reader.readByte())
        assertEquals(MmsMessageType.SEND_REQ, reader.readByte())

        assertEquals(MmsFieldCode.TRANSACTION_ID, reader.readByte())
        val transactionId = reader.readTextString()
        assertTrue("transaction id should not be blank", transactionId.isNotBlank())

        assertEquals(MmsFieldCode.MMS_VERSION, reader.readByte())
        assertEquals(MMS_VERSION_1_3, reader.readByte())

        assertEquals(MmsFieldCode.FROM, reader.readByte())
        val fromLength = reader.readValueLength()
        assertEquals(1, fromLength)
        assertEquals(MmsAddressToken.INSERT_ADDRESS, reader.readByte())

        assertEquals(MmsFieldCode.TO, reader.readByte())
        assertEquals("+15551234567/TYPE=PLMN", reader.readTextString())

        assertEquals(MmsFieldCode.SUBJECT, reader.readByte())
        assertEquals("Trip photos", reader.readTextString())

        assertEquals(MmsFieldCode.CONTENT_TYPE, reader.readByte())
        val contentTypeLength = reader.readValueLength()
        val mediaStart = reader.position
        assertEquals("application/vnd.wap.multipart.related", reader.readTextString())
        assertEquals(contentTypeLength, reader.position - mediaStart)

        assertEquals(1L, reader.readUintvar()) // one part
        val headersLength = reader.readUintvar()
        val dataLength = reader.readUintvar()
        assertEquals(3L, dataLength) // "Hi!"

        val headersEnd = reader.position + headersLength.toInt()
        // A named part's Content-Type is the general form (Value-length Media *Parameter): the
        // value-length here wraps everything that follows, exactly like a top-level Content-Type.
        val partContentTypeLength = reader.readValueLength()
        val partMediaStart = reader.position
        assertEquals("text/plain", reader.readTextString())
        assertEquals(MmsContentTypeParam.NAME, reader.readByte())
        assertEquals("body.txt", reader.readTextString())
        assertEquals(partContentTypeLength, reader.position - partMediaStart)
        assertEquals(headersEnd, reader.position)

        assertEquals("Hi!", String(reader.readBytes(dataLength.toInt())))
        assertTrue(!reader.hasRemaining())
    }

    @Test
    fun `omits subject when none is given`() {
        val request = MmsSendRequest(
            to = listOf("+15551234567"),
            subject = null,
            parts = listOf(MmsPart("text/plain", null, null, "Hi!".toByteArray())),
        )

        val bytes = MmsPduEncoder.encodeSendRequest(request)
        val decodedAsText = String(bytes)
        assertTrue(!decodedAsText.contains("Subject"))
    }

    @Test
    fun `encodes one To header per recipient`() {
        val request = MmsSendRequest(
            to = listOf("+15551234567", "+15559998888"),
            subject = null,
            parts = listOf(MmsPart("text/plain", null, null, ByteArray(0))),
        )

        val bytes = MmsPduEncoder.encodeSendRequest(request)
        val toFieldCount = bytes.count { (it.toInt() and 0xFF) == MmsFieldCode.TO }
        assertEquals(2, toFieldCount)
    }
}
