package text.message.sms.messaging.data.local.provider.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builds structurally valid PDU byte streams by hand (using [PduWriter], the same primitives a
 * real encoder would use) and checks [MmsPduDecoder] reads them back correctly. This cannot
 * substitute for interop testing against a real carrier MMSC, but it does pin down every field
 * grammar this decoder claims to understand, and would catch a boundary or branch-selection bug
 * the same way a real PDU would trigger one.
 */
class MmsPduDecoderTest {

    @Test
    fun `decodes a notification-ind with every field this app reads`() {
        val writer = PduWriter()
        writer.writeShortInteger(MmsFieldCode.MESSAGE_TYPE)
        writer.writeShortInteger(MmsMessageType.NOTIFICATION_IND)

        writer.writeShortInteger(MmsFieldCode.TRANSACTION_ID)
        writer.writeTextString("T998877")

        writer.writeShortInteger(MmsFieldCode.MMS_VERSION)
        writer.writeShortInteger(MMS_VERSION_1_3)

        writeFrom(writer, "+15550101234")

        writer.writeShortInteger(MmsFieldCode.SUBJECT)
        writer.writeTextString("Photo")

        writer.writeShortInteger(MmsFieldCode.MESSAGE_CLASS)
        writer.writeShortInteger(0x80) // Personal

        writer.writeShortInteger(MmsFieldCode.MESSAGE_SIZE)
        writer.writeLongInteger(45_000L)

        writeExpiry(writer, 1_700_000_000L)

        writer.writeShortInteger(MmsFieldCode.CONTENT_LOCATION)
        writer.writeTextString("http://mmsc.carrier.example/get?id=998877")

        val notification = MmsPduDecoder.decodeNotification(writer.toByteArray())

        assertNotNull(notification)
        checkNotNull(notification)
        assertEquals("T998877", notification.transactionId)
        assertEquals("http://mmsc.carrier.example/get?id=998877", notification.contentLocation)
        assertEquals("+15550101234", notification.from)
        assertEquals("Photo", notification.subject)
        assertEquals(45_000L, notification.messageSizeBytes)
        assertEquals(1_700_000_000L, notification.expiryEpochSeconds)
    }

    @Test
    fun `notification-ind with an insert-address-token has no from address`() {
        val writer = PduWriter()
        writer.writeShortInteger(MmsFieldCode.MESSAGE_TYPE)
        writer.writeShortInteger(MmsMessageType.NOTIFICATION_IND)
        writer.writeShortInteger(MmsFieldCode.TRANSACTION_ID)
        writer.writeTextString("T1")

        val fromBody = PduWriter().apply { writeByte(MmsAddressToken.INSERT_ADDRESS) }.toByteArray()
        writer.writeShortInteger(MmsFieldCode.FROM)
        writer.writeValueLength(fromBody.size)
        writer.writeBytes(fromBody)

        writer.writeShortInteger(MmsFieldCode.CONTENT_LOCATION)
        writer.writeTextString("http://mmsc.example/x")

        val notification = MmsPduDecoder.decodeNotification(writer.toByteArray())
        assertNotNull(notification)
        assertNull(notification?.from)
    }

    @Test
    fun `missing content-location yields no notification rather than a partial one`() {
        val writer = PduWriter()
        writer.writeShortInteger(MmsFieldCode.MESSAGE_TYPE)
        writer.writeShortInteger(MmsMessageType.NOTIFICATION_IND)
        writer.writeShortInteger(MmsFieldCode.TRANSACTION_ID)
        writer.writeTextString("T1")

        assertNull(MmsPduDecoder.decodeNotification(writer.toByteArray()))
    }

    @Test
    fun `decodes a retrieve-conf with a text part and a binary part`() {
        val writer = PduWriter()
        writer.writeShortInteger(MmsFieldCode.MESSAGE_TYPE)
        writer.writeShortInteger(MmsMessageType.RETRIEVE_CONF)

        writer.writeShortInteger(MmsFieldCode.TRANSACTION_ID)
        writer.writeTextString("T2233")

        writer.writeShortInteger(MmsFieldCode.MMS_VERSION)
        writer.writeShortInteger(MMS_VERSION_1_3)

        writer.writeShortInteger(MmsFieldCode.DATE)
        writer.writeLongInteger(1_700_000_500L)

        writeFrom(writer, "+15551234567")

        writer.writeShortInteger(MmsFieldCode.TO)
        writer.writeTextString("+15559998888/TYPE=PLMN")

        writer.writeShortInteger(MmsFieldCode.SUBJECT)
        writer.writeTextString("Vacation photo")

        writer.writeShortInteger(MmsFieldCode.CONTENT_TYPE)
        writeGeneralMediaNoParams(writer, "application/vnd.wap.multipart.related")

        val textPart = buildPart("text/plain", "text_0.txt", "Hello from the beach!".toByteArray())
        val imagePart = buildPart("image/jpeg", "photo.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))

        writer.writeUintvar(2)
        writePartEntry(writer, textPart)
        writePartEntry(writer, imagePart)

        val retrieved = MmsPduDecoder.decodeRetrieveConf(writer.toByteArray())

        assertNotNull(retrieved)
        checkNotNull(retrieved)
        assertEquals("T2233", retrieved.transactionId)
        assertEquals("+15551234567", retrieved.from)
        assertEquals(listOf("+15559998888"), retrieved.to)
        assertEquals("Vacation photo", retrieved.subject)
        assertEquals(1_700_000_500L, retrieved.dateEpochSeconds)
        assertEquals(2, retrieved.parts.size)

        val text = retrieved.parts.first { it.contentType == "text/plain" }
        assertEquals("text_0.txt", text.name)
        assertEquals("Hello from the beach!", String(text.data))
        assertTrue(text.isText)

        val image = retrieved.parts.first { it.contentType == "image/jpeg" }
        assertEquals("photo.jpg", image.name)
        assertEquals(4, image.data.size)
        assertTrue(!image.isText)
    }

    @Test
    fun `retrieve-conf with no parts is rejected`() {
        val writer = PduWriter()
        writer.writeShortInteger(MmsFieldCode.MESSAGE_TYPE)
        writer.writeShortInteger(MmsMessageType.RETRIEVE_CONF)
        writer.writeShortInteger(MmsFieldCode.TRANSACTION_ID)
        writer.writeTextString("T1")
        writer.writeShortInteger(MmsFieldCode.CONTENT_TYPE)
        writeGeneralMediaNoParams(writer, "application/vnd.wap.multipart.related")
        writer.writeUintvar(0)

        assertNull(MmsPduDecoder.decodeRetrieveConf(writer.toByteArray()))
    }

    // --- shared PDU-fragment builders, mirroring the exact grammar MmsPduDecoder expects ---

    private fun writeFrom(writer: PduWriter, address: String) {
        val body = PduWriter()
            .writeByte(MmsAddressToken.ADDRESS_PRESENT)
            .writeTextString("$address/TYPE=PLMN")
            .toByteArray()
        writer.writeShortInteger(MmsFieldCode.FROM)
        writer.writeValueLength(body.size)
        writer.writeBytes(body)
    }

    private fun writeExpiry(writer: PduWriter, epochSeconds: Long) {
        val body = PduWriter().apply {
            writeByte(MmsExpiryToken.ABSOLUTE)
            writeLongInteger(epochSeconds)
        }.toByteArray()
        writer.writeShortInteger(MmsFieldCode.EXPIRY)
        writer.writeValueLength(body.size)
        writer.writeBytes(body)
    }

    private fun writeGeneralMediaNoParams(writer: PduWriter, mediaType: String) {
        val mediaBytes = PduWriter().writeTextString(mediaType).toByteArray()
        writer.writeValueLength(mediaBytes.size)
        writer.writeBytes(mediaBytes)
    }

    private data class BuiltPart(val headerBytes: ByteArray, val data: ByteArray)

    private fun buildPart(contentType: String, name: String, data: ByteArray): BuiltPart {
        val mediaAndParams = PduWriter().apply {
            writeTextString(contentType)
            writeByte(MmsContentTypeParam.NAME)
            writeTextString(name)
        }.toByteArray()

        val header = PduWriter().apply {
            writeValueLength(mediaAndParams.size)
            writeBytes(mediaAndParams)
        }.toByteArray()

        return BuiltPart(header, data)
    }

    private fun writePartEntry(writer: PduWriter, part: BuiltPart) {
        writer.writeUintvar(part.headerBytes.size.toLong())
        writer.writeUintvar(part.data.size.toLong())
        writer.writeBytes(part.headerBytes)
        writer.writeBytes(part.data)
    }
}
