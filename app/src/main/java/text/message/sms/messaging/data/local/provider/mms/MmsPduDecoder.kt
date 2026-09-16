package text.message.sms.messaging.data.local.provider.mms

/**
 * Decodes the two PDU types this app ever receives: the M-Notification.ind a WAP push carries,
 * and the M-Retrieve.conf downloaded from the MMSC afterwards.
 *
 * The top-level MMS header list has no overall length prefix, so a header this decoder does not
 * recognise cannot be skipped safely -- each field's byte layout has to be known to stay in
 * sync. Both decoders therefore read headers in the order real encoders emit them and stop
 * gracefully the moment something unexpected turns up, keeping whatever fields were already
 * read. For [decodeRetrieveConf] this is safe by design: the loop always terminates at the
 * Content-Type header, which -- because Content-Type is itself length-prefixed, and every part
 * after it carries an explicit header/data byte count -- re-synchronises the parse regardless of
 * what happened earlier, so the (much larger) body payload never depends on every preceding
 * header having been understood.
 */
object MmsPduDecoder {

    fun decodeNotification(bytes: ByteArray): MmsNotification? {
        val reader = PduReader(bytes)
        var transactionId: String? = null
        var contentLocation: String? = null
        var expiry: Long? = null
        var messageSize: Long? = null
        var from: String? = null
        var subject: String? = null

        while (reader.hasRemaining()) {
            when (reader.peekByte()) {
                MmsFieldCode.MESSAGE_TYPE -> {
                    reader.skip(1)
                    reader.readShortInteger()
                }

                MmsFieldCode.TRANSACTION_ID -> {
                    reader.skip(1)
                    transactionId = reader.readTextString()
                }

                MmsFieldCode.MMS_VERSION -> {
                    reader.skip(1)
                    reader.readShortInteger()
                }

                MmsFieldCode.FROM -> {
                    reader.skip(1)
                    from = readFrom(reader)
                }

                MmsFieldCode.SUBJECT -> {
                    reader.skip(1)
                    subject = reader.readEncodedString()
                }

                MmsFieldCode.MESSAGE_CLASS -> {
                    reader.skip(1)
                    if (reader.peekByte() and 0x80 != 0) reader.readShortInteger() else reader.readTextString()
                }

                MmsFieldCode.MESSAGE_SIZE -> {
                    reader.skip(1)
                    messageSize = reader.readLongInteger()
                }

                MmsFieldCode.EXPIRY -> {
                    reader.skip(1)
                    expiry = readExpiry(reader)
                }

                MmsFieldCode.DELIVERY_REPORT -> {
                    reader.skip(1)
                    reader.readShortInteger()
                }

                MmsFieldCode.CONTENT_LOCATION -> {
                    reader.skip(1)
                    contentLocation = reader.readTextString()
                }

                else -> return buildNotification(
                    transactionId, contentLocation, expiry, messageSize, from, subject,
                )
            }
        }

        return buildNotification(transactionId, contentLocation, expiry, messageSize, from, subject)
    }

    private fun buildNotification(
        transactionId: String?,
        contentLocation: String?,
        expiry: Long?,
        messageSize: Long?,
        from: String?,
        subject: String?,
    ): MmsNotification? {
        if (transactionId == null || contentLocation == null) return null
        return MmsNotification(transactionId, contentLocation, expiry, messageSize, from, subject)
    }

    fun decodeRetrieveConf(bytes: ByteArray): MmsRetrieved? {
        val reader = PduReader(bytes)
        var transactionId: String? = null
        var messageId: String? = null
        var subject: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var date = 0L

        while (reader.hasRemaining()) {
            when (reader.peekByte()) {
                MmsFieldCode.MESSAGE_TYPE -> {
                    reader.skip(1)
                    reader.readShortInteger()
                }

                MmsFieldCode.TRANSACTION_ID -> {
                    reader.skip(1)
                    transactionId = reader.readTextString()
                }

                MmsFieldCode.MMS_VERSION -> {
                    reader.skip(1)
                    reader.readShortInteger()
                }

                MmsFieldCode.MESSAGE_ID -> {
                    reader.skip(1)
                    messageId = reader.readTextString()
                }

                MmsFieldCode.DATE -> {
                    reader.skip(1)
                    date = reader.readLongInteger()
                }

                MmsFieldCode.FROM -> {
                    reader.skip(1)
                    from = readFrom(reader)
                }

                MmsFieldCode.TO -> {
                    reader.skip(1)
                    to += reader.readEncodedString().substringBefore('/')
                }

                MmsFieldCode.CC -> {
                    reader.skip(1)
                    cc += reader.readEncodedString().substringBefore('/')
                }

                MmsFieldCode.SUBJECT -> {
                    reader.skip(1)
                    subject = reader.readEncodedString()
                }

                MmsFieldCode.PRIORITY -> {
                    reader.skip(1)
                    reader.readShortInteger()
                }

                MmsFieldCode.DELIVERY_REPORT, MmsFieldCode.READ_REPORT -> {
                    reader.skip(1)
                    reader.readShortInteger()
                }

                MmsFieldCode.CONTENT_TYPE -> {
                    reader.skip(1)
                    // Length-anchored: from here on every read is bounded by an explicit byte
                    // count, so the rest of the parse no longer depends on the headers above.
                    decodeContentTypeGeneral(reader)
                    val parts = decodeMultipartBody(reader)
                    if (transactionId == null || parts.isEmpty()) return null
                    return MmsRetrieved(transactionId, messageId, subject, from, to, cc, date, parts)
                }

                else -> return null // unrecognised header before Content-Type: cannot resync
            }
        }

        return null
    }

    private fun readFrom(reader: PduReader): String? {
        val length = reader.readValueLength()
        val end = reader.position + length
        val token = reader.peekByte()
        val from = when (token) {
            MmsAddressToken.ADDRESS_PRESENT -> {
                reader.skip(1)
                reader.readEncodedString().substringBefore('/')
            }

            else -> {
                reader.skip(1) // insert-address-token: carrier fills in our own number
                null
            }
        }
        reader.skip((end - reader.position).coerceAtLeast(0))
        return from
    }

    private fun readExpiry(reader: PduReader): Long {
        val length = reader.readValueLength()
        val end = reader.position + length
        reader.skip(1) // absolute/relative token; both wrap a Long-integer next
        val value = reader.readLongInteger()
        reader.skip((end - reader.position).coerceAtLeast(0))
        return value
    }

    /** Content-type-value general form: `Value-length Media (*Parameter)`, bounded so the
     * caller can resynchronise on [PduReader.position] regardless of which parameters were
     * understood. Returns the media type; parameters are consumed but not currently surfaced,
     * since the top-level Content-Type is always multipart/related in practice. */
    private fun decodeContentTypeGeneral(reader: PduReader): String {
        val marker = reader.peekByte()
        if (marker > 0x1F && marker and 0x80 == 0) {
            // Constrained-media, Extension-Media form: a bare text-string, no parameters.
            return reader.readTextString()
        }

        val length = reader.readValueLength()
        val end = reader.position + length
        val media = readMediaType(reader)
        reader.skip((end - reader.position).coerceAtLeast(0))
        return media
    }

    private fun readMediaType(reader: PduReader): String {
        val marker = reader.peekByte()
        return if (marker and 0x80 != 0) {
            val code = reader.readShortInteger()
            WELL_KNOWN_MEDIA_TYPES[code] ?: "application/octet-stream"
        } else {
            reader.readTextString()
        }
    }

    /** WSP multipart body: `nEntries=Uintvar`, then per entry a header/data byte-count pair.
     * Each entry is fully self-describing, so one malformed or unrecognised part parameter
     * never desyncs the entries that follow it. */
    private fun decodeMultipartBody(reader: PduReader): List<MmsPart> {
        if (!reader.hasRemaining()) return emptyList()
        val entryCount = reader.readUintvar()
        val parts = mutableListOf<MmsPart>()

        repeat(entryCount.toInt()) {
            if (!reader.hasRemaining()) return parts
            val headersLength = reader.readUintvar().toInt()
            val dataLength = reader.readUintvar().toInt()
            val headersEnd = reader.position + headersLength

            val header = decodePartHeaders(reader, headersEnd)
            reader.skip((headersEnd - reader.position).coerceAtLeast(0))

            val data = reader.readBytes(dataLength)
            parts += MmsPart(header.contentType, header.name, header.contentId, data)
        }

        return parts
    }

    private data class PartHeader(val contentType: String, val name: String?, val contentId: String?)

    /** A part's own header block, bounded by [headersEnd] -- any field this decoder does not
     * special-case is skipped generically by byte count rather than causing the part to fail. */
    private fun decodePartHeaders(reader: PduReader, headersEnd: Int): PartHeader {
        var contentType = "application/octet-stream"
        var name: String? = null
        var contentId: String? = null
        var sawContentType = false

        while (reader.position < headersEnd && reader.hasRemaining()) {
            val marker = reader.peekByte()

            // MMS part headers conventionally lead with Content-Type; take that branch only
            // for the very first header in this part's block.
            if (!sawContentType) {
                val result = decodeContentTypeWithParams(reader, headersEnd)
                contentType = result.first
                name = name ?: result.second
                sawContentType = true
                continue
            }

            when (marker) {
                MmsFieldCode.CONTENT_LOCATION -> {
                    reader.skip(1)
                    contentId = contentId ?: reader.readTextString()
                }

                else -> skipGenericHeaderValue(reader)
            }
        }

        return PartHeader(contentType, name, contentId)
    }

    private fun decodeContentTypeWithParams(reader: PduReader, boundEnd: Int): Pair<String, String?> {
        val marker = reader.peekByte()
        if (marker > 0x1F && marker and 0x80 == 0) {
            return reader.readTextString() to null
        }
        if (marker and 0x80 != 0 && marker != LENGTH_QUOTE_BYTE) {
            // Bare well-known short-integer, no parameters.
            val code = reader.readShortInteger()
            return (WELL_KNOWN_MEDIA_TYPES[code] ?: "application/octet-stream") to null
        }

        val length = reader.readValueLength()
        val end = (reader.position + length).coerceAtMost(boundEnd)
        val media = readMediaType(reader)
        var name: String? = null

        while (reader.position < end && reader.hasRemaining()) {
            when (reader.peekByte()) {
                MmsContentTypeParam.NAME, MmsContentTypeParam.NAME_LEGACY,
                MmsContentTypeParam.FILENAME, MmsContentTypeParam.FILENAME_LEGACY,
                -> {
                    reader.skip(1)
                    name = reader.readTextOrQuotedParamValue()
                }

                MmsContentTypeParam.CHARSET -> {
                    reader.skip(1)
                    skipParamValue(reader)
                }

                else -> skipParamValue(reader)
            }
        }

        reader.skip((end - reader.position).coerceAtLeast(0))
        return media to name
    }

    /** A Content-Type parameter's value is a Short-integer, a Text-String, or (rarely) another
     * Value-length wrapped structure -- decoded generically since only Name/Filename matter. */
    private fun PduReader.readTextOrQuotedParamValue(): String {
        val marker = peekByte()
        return when {
            marker and 0x80 != 0 -> { skip(1); "" } // well-known token, not a real filename
            marker == 0x22 -> readQuotedString()
            marker <= 0x1F -> { // value-length wrapped (e.g. charset-prefixed text)
                val length = readValueLength()
                val end = position + length
                skip(1)
                val text = readTextString()
                seekTo(end)
                text
            }
            else -> readTextString()
        }
    }

    private fun skipParamValue(reader: PduReader) {
        val marker = reader.peekByte()
        when {
            marker and 0x80 != 0 -> reader.skip(1)
            marker <= 0x1F -> {
                val length = reader.readValueLength()
                reader.skip(length)
            }
            else -> reader.readTextString()
        }
    }

    /** Generic fallback for a header field this decoder has no specific grammar for: infer the
     * value's shape from its leading byte and skip exactly that many bytes, the same rule WSP
     * uses for forward-compatible extension headers. */
    private fun skipGenericHeaderValue(reader: PduReader) {
        reader.skip(1) // the field-name byte
        if (!reader.hasRemaining()) return
        val marker = reader.peekByte()
        when {
            marker and 0x80 != 0 -> reader.skip(1)
            marker <= 0x1F -> reader.skip(reader.readValueLength())
            else -> reader.readTextString()
        }
    }

    private const val LENGTH_QUOTE_BYTE = 0x1F
}
