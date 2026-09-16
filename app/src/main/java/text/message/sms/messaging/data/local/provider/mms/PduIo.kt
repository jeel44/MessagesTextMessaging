package text.message.sms.messaging.data.local.provider.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Binary primitives shared by every MMS PDU reader and writer, as defined by the WAP Wireless
 * Session Protocol encoding rules (WAP-230-WSP) that the MMS Encapsulation spec (OMA-WAP-209)
 * reuses for its own headers. Nothing here is MMS-specific -- it is the same variable-length
 * integer, string and length encoding WSP uses everywhere.
 */
private const val SHORT_LENGTH_MAX = 30
private const val LENGTH_QUOTE = 0x1F
private const val TEXT_QUOTE = 0x7F
private const val QUOTED_STRING_MARKER = 0x22
private const val SHORT_INTEGER_FLAG = 0x80
private const val UINTVAR_CONTINUATION = 0x80
private const val UINTVAR_PAYLOAD_MASK = 0x7F
private const val UINTVAR_PAYLOAD_BITS = 7
private const val BYTE_MASK = 0xFF

/** Cursor-based reader over a raw PDU byte array. Never throws on a structural read past the
 * end -- callers check [hasRemaining] -- but does throw [PduFormatException] when a specific
 * grammar rule (e.g. a missing NUL terminator) is violated, since that signals corrupt input
 * rather than "no more headers". */
class PduReader(private val bytes: ByteArray, start: Int = 0) {

    var position: Int = start
        private set

    val size: Int get() = bytes.size

    fun hasRemaining(count: Int = 1): Boolean = position + count <= bytes.size

    fun peekByte(): Int = bytes[position].toInt() and BYTE_MASK

    fun readByte(): Int {
        val value = peekByte()
        position++
        return value
    }

    fun skip(count: Int) {
        position = (position + count).coerceAtMost(bytes.size)
    }

    /** Jumps directly to a byte offset already computed by the caller (e.g. the end of a
     * Value-length-bounded structure), rather than skipping a relative count. */
    fun seekTo(offset: Int) {
        position = offset.coerceIn(0, bytes.size)
    }

    fun readBytes(count: Int): ByteArray {
        val end = (position + count).coerceAtMost(bytes.size)
        val slice = bytes.copyOfRange(position, end)
        position = end
        return slice
    }

    /** WSP Uintvar-integer: 7 payload bits per byte, continuation flagged by the top bit. */
    fun readUintvar(): Long {
        var value = 0L
        while (hasRemaining()) {
            val octet = readByte()
            value = (value shl UINTVAR_PAYLOAD_BITS) or (octet and UINTVAR_PAYLOAD_MASK).toLong()
            if (octet and UINTVAR_CONTINUATION == 0) return value
        }
        throw PduFormatException("Truncated uintvar at offset $position")
    }

    /** WSP Value-length: a direct 0-30 byte count, or octet 31 followed by a uintvar count. */
    fun readValueLength(): Int {
        val first = readByte()
        return if (first <= SHORT_LENGTH_MAX) first else readUintvar().toInt()
    }

    /** A Short-integer always has its top bit set; the payload is the low 7 bits. */
    fun readShortInteger(): Int = readByte() and UINTVAR_PAYLOAD_MASK

    /** A Long-integer is a length byte (1-8) followed by that many big-endian bytes. */
    fun readLongInteger(): Long {
        val length = readByte()
        var value = 0L
        repeat(length) { value = (value shl Byte.SIZE_BITS) or readByte().toLong() }
        return value
    }

    /**
     * A Text-String runs until a NUL byte. A leading [TEXT_QUOTE] (0x7F) marks a string whose
     * first real character would otherwise be mistaken for a Short-integer; it is consumed and
     * dropped, matching the encoder in [PduWriter.writeTextString].
     */
    fun readTextString(): String {
        if (hasRemaining() && peekByte() == TEXT_QUOTE) skip(1)
        val start = position
        while (hasRemaining() && peekByte() != 0) position++
        if (!hasRemaining()) throw PduFormatException("Unterminated text-string at offset $start")
        val text = String(bytes, start, position - start, Charsets.UTF_8)
        skip(1) // the NUL terminator
        return text
    }

    /** A Quoted-String is a Text-String whose payload happens to start with a literal `"`. */
    fun readQuotedString(): String {
        if (hasRemaining() && peekByte() == QUOTED_STRING_MARKER) skip(1)
        return readTextString()
    }

    /**
     * Encoded-string-value = Text-String | (Value-length Char-set Text-String). Real-world
     * senders overwhelmingly use plain UTF-8 or US-ASCII text, so a declared charset other than
     * those is decoded as UTF-8 on a best-effort basis rather than pulled in as a full charset
     * table -- wrong text in a rarely-used legacy encoding beats aborting the whole parse.
     */
    fun readEncodedString(): String {
        if (!hasRemaining()) return ""
        val marker = peekByte()
        return if (marker <= SHORT_LENGTH_MAX || marker == LENGTH_QUOTE) {
            val length = readValueLength()
            val end = position + length
            skip(1) // charset (Short-integer or text-string); charset table not needed, see above
            val text = readTextString()
            position = end
            text
        } else {
            readTextString()
        }
    }
}

/** Growable writer for the same primitives, used only when building an outgoing PDU. */
class PduWriter {

    private val buffer = ByteArrayOutputStream()

    fun writeByte(value: Int): PduWriter = apply { buffer.write(value and BYTE_MASK) }

    fun writeBytes(value: ByteArray): PduWriter = apply { buffer.write(value) }

    fun writeUintvar(value: Long): PduWriter = apply {
        var remaining = value
        val octets = ArrayDeque<Int>()
        octets.addFirst((remaining and UINTVAR_PAYLOAD_MASK.toLong()).toInt())
        remaining = remaining ushr UINTVAR_PAYLOAD_BITS
        while (remaining > 0) {
            octets.addFirst((remaining and UINTVAR_PAYLOAD_MASK.toLong()).toInt() or UINTVAR_CONTINUATION)
            remaining = remaining ushr UINTVAR_PAYLOAD_BITS
        }
        octets.forEach(::writeByte)
    }

    /** Writes a wire byte that already carries the well-known field/value code, top bit set. */
    fun writeShortInteger(wireByte: Int): PduWriter = apply {
        writeByte(if (wireByte and SHORT_INTEGER_FLAG != 0) wireByte else wireByte or SHORT_INTEGER_FLAG)
    }

    fun writeLongInteger(value: Long): PduWriter = apply {
        var remaining = value
        val octets = ArrayDeque<Int>()
        if (remaining == 0L) {
            octets.addFirst(0)
        } else {
            while (remaining > 0) {
                octets.addFirst((remaining and BYTE_MASK.toLong()).toInt())
                remaining = remaining ushr Byte.SIZE_BITS
            }
        }
        writeByte(octets.size)
        octets.forEach(::writeByte)
    }

    fun writeValueLength(length: Int): PduWriter = apply {
        if (length <= SHORT_LENGTH_MAX) {
            writeByte(length)
        } else {
            writeByte(LENGTH_QUOTE)
            writeUintvar(length.toLong())
        }
    }

    /** Text-String: UTF-8 bytes plus a NUL terminator, quoted if the first byte would collide
     * with a Short-integer's top bit. */
    fun writeTextString(value: String, charset: Charset = Charsets.UTF_8): PduWriter = apply {
        val encoded = value.toByteArray(charset)
        if (encoded.isNotEmpty() && (encoded[0].toInt() and SHORT_INTEGER_FLAG) != 0) {
            writeByte(TEXT_QUOTE)
        }
        writeBytes(encoded)
        writeByte(0)
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()

    val size: Int get() = buffer.size()
}

/** Raised when a PDU violates the grammar for a field this codec does know how to read --
 * never for "the next field is one we don't recognise", which the decoders handle by stopping
 * gracefully instead (see [MmsPduDecoder]). */
class PduFormatException(message: String) : Exception(message)
