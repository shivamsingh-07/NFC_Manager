package com.nfcmanager.app.data.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.nfcmanager.app.domain.model.TagPayload
import com.nfcmanager.app.domain.util.UrlValidator
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts raw [NdefMessage]s into domain [TagPayload]s.
 *
 * Everything here treats tag data as *adversarial input*: invalid UTF-8,
 * oversized records, and unknown TNFs are all handled without throwing.
 */
@Singleton
class NdefParser @Inject constructor(
    private val urlValidator: UrlValidator,
) {

    fun parse(messages: List<NdefMessage>): List<TagPayload> =
        messages.flatMap { msg -> msg.records.orEmpty().mapNotNull(::parseRecord) }

    fun parseRecord(record: NdefRecord): TagPayload? {
        return try {
            when (record.tnf) {
                NdefRecord.TNF_WELL_KNOWN -> parseWellKnown(record)
                NdefRecord.TNF_MIME_MEDIA -> parseMime(record)
                NdefRecord.TNF_ABSOLUTE_URI -> {
                    val raw = record.payload.toString(StandardCharsets.UTF_8)
                    val sanitized = urlValidator.sanitize(raw)
                    if (sanitized != null) {
                        WifiSimpleConfigParser.parseUriForm(sanitized)
                            ?: TagPayload.Uri(raw = raw, sanitizedUrl = sanitized)
                    } else {
                        TagPayload.Raw(mimeType = "unsafe-uri", bytes = record.payload ?: ByteArray(0))
                    }
                }
                NdefRecord.TNF_EXTERNAL_TYPE,
                NdefRecord.TNF_UNKNOWN,
                NdefRecord.TNF_EMPTY,
                -> TagPayload.Raw(mimeType = null, bytes = record.payload ?: ByteArray(0))
                else -> TagPayload.Raw(mimeType = null, bytes = record.payload ?: ByteArray(0))
            }
        } catch (t: Throwable) {
            // Never propagate parsing errors: prefer a Raw fallback so UI can still show something.
            TagPayload.Raw(mimeType = null, bytes = record.payload ?: ByteArray(0))
        }
    }

    private fun parseWellKnown(record: NdefRecord): TagPayload? {
        val type = record.type ?: return null
        return when {
            type.contentEquals(NdefRecord.RTD_TEXT) -> parseText(record.payload)
            type.contentEquals(NdefRecord.RTD_URI) -> parseUri(record.payload)
            else -> TagPayload.Raw(mimeType = null, bytes = record.payload ?: ByteArray(0))
        }
    }

    /**
     * RTD_TEXT encoding: status byte (bit 7 = UTF-16, bits 0-5 = language length)
     * followed by the language code, followed by the text.
     */
    private fun parseText(payload: ByteArray?): TagPayload? {
        if (payload == null || payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val isUtf16 = status and 0x80 != 0
        val langLen = status and 0x3F
        if (1 + langLen > payload.size) return null

        val lang = String(payload, 1, langLen, StandardCharsets.US_ASCII)
        val textOffset = 1 + langLen
        val textLen = payload.size - textOffset
        val charset: Charset = if (isUtf16) StandardCharsets.UTF_16 else StandardCharsets.UTF_8
        val text = String(payload, textOffset, textLen, charset)
        return TagPayload.Text(text = text, languageCode = lang)
    }

    /**
     * RTD_URI encoding: prefix byte followed by UTF-8 URI.
     * Prefix byte indexes into the URI_PREFIXES table defined by the NFC Forum.
     */
    private fun parseUri(payload: ByteArray?): TagPayload? {
        if (payload == null || payload.isEmpty()) return null
        val prefixIndex = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIXES.getOrNull(prefixIndex).orEmpty()
        val rest = String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
        val full = prefix + rest
        
        // Strict sanitization check
        val sanitized = urlValidator.sanitize(full) ?: return TagPayload.Raw(mimeType = "unsafe-uri", bytes = payload)
        
        // A few tag authoring tools store "WIFI:T:...;S:...;P:...;;" as a plain URI record.
        WifiSimpleConfigParser.parseUriForm(sanitized)?.let { return it }
        return TagPayload.Uri(raw = full, sanitizedUrl = sanitized)
    }

    private fun parseMime(record: NdefRecord): TagPayload? {
        val mime = record.type?.toString(StandardCharsets.US_ASCII)?.lowercase().orEmpty()
        val bytes = record.payload ?: ByteArray(0)
        return when {
            mime.startsWith("text/x-vcard") || mime == "text/vcard" -> parseVCard(bytes)
            mime == "application/vnd.wfa.wsc" -> WifiSimpleConfigParser.parse(bytes)
            mime.startsWith("text/") -> TagPayload.Text(
                text = bytes.toString(StandardCharsets.UTF_8),
            )
            else -> TagPayload.Raw(mimeType = mime.ifEmpty { null }, bytes = bytes)
        }
    }

    private fun parseVCard(bytes: ByteArray): TagPayload.Contact {
        val raw = bytes.toString(StandardCharsets.UTF_8)
        val props = VCardParser.parse(raw)
        return TagPayload.Contact(
            displayName = props.displayName,
            phoneNumbers = props.phones,
            emails = props.emails,
            organization = props.organization,
            rawVCard = raw,
        )
    }

    companion object {
        /** NFC Forum RTD_URI prefix table. */
        internal val URI_PREFIXES = arrayOf(
            "", "http://www.", "https://www.", "http://", "https://",
            "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://",
            "sftp://", "smb://", "nfs://", "ftp://", "dav://",
            "news:", "telnet://", "imap:", "rtsp://", "urn:",
            "pop:", "sip:", "sips:", "tftp:", "btspp://",
            "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://",
            "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:",
            "urn:nfc:",
        )
    }
}
