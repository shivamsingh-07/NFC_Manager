package com.nfcmanager.app.data.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.nfcmanager.app.domain.model.TagPayload
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds well-formed [NdefMessage]s from typed [TagPayload]s.
 *
 * All byte construction is done locally (without relying on
 * `NdefRecord.createTextRecord`, which depends on API levels and locale) so
 * the output is deterministic and easy to unit-test.
 */
@Singleton
class NdefBuilder @Inject constructor() {

    fun buildText(text: String, languageCode: String = "en"): NdefMessage {
        require(text.length <= MAX_TEXT_CHARS) { "Text too long" }
        val langBytes = languageCode.take(MAX_LANG_LEN).toByteArray(StandardCharsets.US_ASCII)
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val payload = ByteBuffer.allocate(1 + langBytes.size + textBytes.size)
            .put((langBytes.size and 0x3F).toByte()) // UTF-8 (status bit 7 = 0)
            .put(langBytes)
            .put(textBytes)
            .array()
        return NdefMessage(
            arrayOf(
                NdefRecord(
                    NdefRecord.TNF_WELL_KNOWN,
                    NdefRecord.RTD_TEXT,
                    ByteArray(0),
                    payload,
                ),
            ),
        )
    }

    fun buildUri(uri: String): NdefMessage {
        require(uri.isNotBlank()) { "URI must not be blank" }
        require(uri.length <= MAX_URI_CHARS) { "URI too long" }
        // Use the helper to get the efficient prefix-byte encoding for well-known schemes.
        return NdefMessage(arrayOf(NdefRecord.createUri(uri)))
    }

    fun buildVCard(
        displayName: String,
        phone: String?,
        email: String?,
        organization: String?,
    ): NdefMessage {
        require(displayName.isNotBlank()) { "Display name required" }
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\n")
        sb.append("VERSION:3.0\r\n")
        sb.append("FN:").append(escapeVCard(displayName)).append("\r\n")
        sb.append("N:").append(escapeVCard(displayName)).append(";;;;\r\n")
        if (!phone.isNullOrBlank()) sb.append("TEL;TYPE=CELL:").append(phone.trim()).append("\r\n")
        // TYPE=INTERNET is the vCard 3.0 hint many address books use to route
        // the value into the email field (some Android contact pickers reject
        // bare `EMAIL:` without it).
        if (!email.isNullOrBlank()) sb.append("EMAIL;TYPE=INTERNET:").append(email.trim()).append("\r\n")
        if (!organization.isNullOrBlank()) sb.append("ORG:").append(organization.trim()).append("\r\n")
        sb.append("END:VCARD\r\n")
        val payload = sb.toString().toByteArray(StandardCharsets.UTF_8)
        return NdefMessage(
            arrayOf(
                NdefRecord.createMime("text/vcard", payload),
            ),
        )
    }

    /**
     * Standardized Wi-Fi sharing. Includes BOTH:
     *  1. NFC Forum WSC (Wi-Fi Simple Configuration) MIME record
     *  2. WIFI: URI record (for legacy reader compatibility)
     */
    fun buildWifi(
        ssid: String,
        password: String?,
        security: TagPayload.WiFi.Security,
        hidden: Boolean,
    ): NdefMessage {
        require(ssid.isNotBlank()) { "SSID required" }
        
        // 1. WSC Record (application/vnd.wfa.wsc)
        val wscRecord = buildWscRecord(ssid, password, security)
        
        // 2. URI Record (WIFI:T:WPA;S:SSID;P:PASSWORD;H:HIDDEN;;)
        val securityTag = when (security) {
            TagPayload.WiFi.Security.OPEN -> "nopass"
            TagPayload.WiFi.Security.WEP -> "WEP"
            TagPayload.WiFi.Security.WPA_WPA2_PSK -> "WPA"
            TagPayload.WiFi.Security.WPA3_SAE -> "SAE"
            TagPayload.WiFi.Security.UNKNOWN -> "WPA"
        }
        val uriRaw = buildString {
            append("WIFI:")
            append("T:").append(securityTag).append(';')
            append("S:").append(escapeWifi(ssid)).append(';')
            if (!password.isNullOrEmpty() && security != TagPayload.WiFi.Security.OPEN) {
                append("P:").append(escapeWifi(password)).append(';')
            }
            append("H:").append(if (hidden) "true" else "false").append(';')
            append(';')
        }
        val uriRecord = NdefRecord.createUri(uriRaw)
        
        return NdefMessage(arrayOf(wscRecord, uriRecord))
    }

    private fun buildWscRecord(
        ssid: String,
        password: String?,
        security: TagPayload.WiFi.Security,
    ): NdefRecord {
        val ssidBytes = ssid.toByteArray(StandardCharsets.UTF_8)
        val passBytes = password?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        
        // Auth Type: 0x0001 (Open), 0x0002 (WPA-PSK), 0x0020 (WPA2-PSK), 0x0040 (SAE)
        val authBits = when (security) {
            TagPayload.WiFi.Security.OPEN -> 0x0001
            TagPayload.WiFi.Security.WEP -> 0x0004 // WEP Shared
            TagPayload.WiFi.Security.WPA_WPA2_PSK -> 0x0022 // WPA + WPA2
            TagPayload.WiFi.Security.WPA3_SAE -> 0x0040
            TagPayload.WiFi.Security.UNKNOWN -> 0x0022
        }

        // Credential TLVs: [Type(2) | Len(2) | Val]
        // Credential Container (0x100E) wraps SSID (0x1045), Auth (0x1003), Key (0x1027)
        val innerTotal = (4 + ssidBytes.size) + (4 + 2) + (4 + passBytes.size)
        val payload = ByteBuffer.allocate(4 + innerTotal).apply {
            // Credential Container
            putShort(0x100E.toShort())
            putShort(innerTotal.toShort())
            
            // SSID
            putShort(0x1045.toShort())
            putShort(ssidBytes.size.toShort())
            put(ssidBytes)
            
            // Auth Type
            putShort(0x1003.toShort())
            putShort(2)
            putShort(authBits.toShort())
            
            // Network Key
            putShort(0x1027.toShort())
            putShort(passBytes.size.toShort())
            put(passBytes)
        }.array()

        return NdefRecord.createMime("application/vnd.wfa.wsc", payload)
    }

    /**
     * Bluetooth OOB pairing record per the **NFC Forum Bluetooth Secure Simple
     * Pairing (BTSSP) Application Document**.
     *
     * Layout:
     *
     *   u16 LE  total length
     *   6 bytes BD_ADDR (little-endian, reverse of human-readable form)
     *   EIR    Complete Local Name TLV (0x09)
     *
     * MIME type `application/vnd.bluetooth.ep.oob` is the spec-defined type;
     * the receiver app (or stock Android) will recognise this and offer
     * pairing. Throws [IllegalArgumentException] for malformed MAC input — we
     * never want to publish a structurally-invalid OOB record.
     */
    fun buildBluetooth(deviceName: String, macAddress: String): NdefMessage {
        require(deviceName.isNotBlank()) { "Device name required" }
        val bdAddr = parseBluetoothMac(macAddress)
            ?: throw IllegalArgumentException("MAC must be 6 hex octets like 00:11:22:33:44:55")
        val nameBytes = deviceName.toByteArray(StandardCharsets.UTF_8)
        // Cap name to 248 bytes so total length fits in the 16-bit field.
        val truncatedName = if (nameBytes.size > 248) nameBytes.copyOf(248) else nameBytes
        // EIR record:  Length (1) | Type (1, 0x09 = Complete Local Name) | Value
        val eir = ByteArray(2 + truncatedName.size).apply {
            this[0] = (truncatedName.size + 1).toByte()
            this[1] = 0x09
            System.arraycopy(truncatedName, 0, this, 2, truncatedName.size)
        }
        val total = 2 + bdAddr.size + eir.size
        val payload = ByteBuffer.allocate(total).apply {
            // Per spec: little-endian u16 total length, then BD_ADDR (LE), then EIR.
            put((total and 0xFF).toByte())
            put(((total ushr 8) and 0xFF).toByte())
            put(bdAddr)
            put(eir)
        }.array()
        return NdefMessage(
            arrayOf(NdefRecord.createMime("application/vnd.bluetooth.ep.oob", payload)),
        )
    }

    /** "00:11:22:33:44:55" → byte[] in little-endian (BD_ADDR convention). */
    private fun parseBluetoothMac(raw: String): ByteArray? {
        val cleaned = raw.trim().replace("-", ":").uppercase()
        val parts = cleaned.split(':')
        if (parts.size != 6) return null
        val bytes = ByteArray(6)
        for (i in 0 until 6) {
            val token = parts[i]
            if (token.length != 2) return null
            bytes[5 - i] = token.toIntOrNull(16)?.toByte() ?: return null
        }
        return bytes
    }

    private fun escapeVCard(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    private fun escapeWifi(value: String): String {
        val sb = StringBuilder(value.length)
        for (c in value) {
            when (c) {
                '\\', ';', ',', '"', ':' -> { sb.append('\\'); sb.append(c) }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_TEXT_CHARS = 4096
        private const val MAX_URI_CHARS = 2048
        private const val MAX_LANG_LEN = 8
    }
}
