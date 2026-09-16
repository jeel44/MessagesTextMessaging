package text.message.sms.messaging.data.local.provider.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round-trips every WSP binary primitive [PduWriter] can produce back through [PduReader]. */
class PduIoTest {

    @Test
    fun `uintvar round-trips small and large values`() {
        listOf(0L, 1L, 127L, 128L, 16383L, 16384L, 2_097_151L, 268_435_455L).forEach { value ->
            val writer = PduWriter().apply { writeUintvar(value) }
            val reader = PduReader(writer.toByteArray())
            assertEquals("uintvar($value)", value, reader.readUintvar())
        }
    }

    @Test
    fun `uintvar matches the documented two-byte encoding for 128`() {
        // 128 = 0b10000000 -> high 7 bits = 1 (continuation set: 0x81), low 7 bits = 0 (0x00).
        val bytes = PduWriter().apply { writeUintvar(128L) }.toByteArray()
        assertEquals(2, bytes.size)
        assertEquals(0x81, bytes[0].toInt() and 0xFF)
        assertEquals(0x00, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `value-length round-trips both the short and length-quote forms`() {
        listOf(0, 1, 30, 31, 32, 1000, 70_000).forEach { length ->
            val writer = PduWriter().apply { writeValueLength(length) }
            val reader = PduReader(writer.toByteArray())
            assertEquals("value-length($length)", length, reader.readValueLength())
        }
    }

    @Test
    fun `value-length uses a single byte for 30 and the length-quote form starting at 31`() {
        assertEquals(1, PduWriter().apply { writeValueLength(30) }.toByteArray().size)
        val quoted = PduWriter().apply { writeValueLength(31) }.toByteArray()
        assertEquals(0x1F, quoted[0].toInt() and 0xFF)
    }

    @Test
    fun `short-integer keeps a wire byte that already carries the top bit`() {
        val writer = PduWriter().apply { writeShortInteger(0x8C) }
        val reader = PduReader(writer.toByteArray())
        assertEquals(0x0C, reader.readShortInteger())
    }

    @Test
    fun `long-integer round-trips including zero`() {
        listOf(0L, 1L, 255L, 65535L, 16_777_215L, Long.MAX_VALUE / 2).forEach { value ->
            val writer = PduWriter().apply { writeLongInteger(value) }
            val reader = PduReader(writer.toByteArray())
            assertEquals("long-integer($value)", value, reader.readLongInteger())
        }
    }

    @Test
    fun `text-string round-trips plain ascii`() {
        val writer = PduWriter().apply { writeTextString("hello world") }
        val reader = PduReader(writer.toByteArray())
        assertEquals("hello world", reader.readTextString())
    }

    @Test
    fun `text-string quotes a payload whose first byte would look like a short-integer`() {
        // 0xC0 as the first UTF-8 byte would otherwise be mistaken for a Short-integer.
        val value = "Ànchor" // U+00C0 encodes to a UTF-8 lead byte with the top bit set
        val bytes = PduWriter().apply { writeTextString(value) }.toByteArray()
        assertEquals(0x7F, bytes[0].toInt() and 0xFF)

        val decoded = PduReader(bytes).readTextString()
        assertEquals(value, decoded)
    }

    @Test
    fun `multiple fields read back in the order they were written`() {
        val writer = PduWriter()
            .writeShortInteger(0x8C)
            .writeTextString("T12345")
            .writeUintvar(4096L)

        val reader = PduReader(writer.toByteArray())
        assertEquals(0x0C, reader.readShortInteger())
        assertEquals("T12345", reader.readTextString())
        assertEquals(4096L, reader.readUintvar())
        assertTrue(!reader.hasRemaining())
    }
}
