package com.nfcmanager.app.data.nfc

import com.nfcmanager.app.domain.model.TagPayload
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Parser for Wi-Fi Simple Configuration tokens (application/vnd.wfa.wsc).
 *
 * The payload is a sequence of TLVs: `u16 type | u16 length | bytes value`.
 * We walk into the Credential (0x100E) container and extract SSID (0x1045),
 * Network Key (0x1027), and Authentication Type (0x1003).
 *
 * Also recognises the much simpler `WIFI:T:WPA;S:...;P:...;H:...;;` string format,
 * because many NFC writing apps use that encoded as a URI / text record.
 */
internal object WifiSimpleConfigParser {

    private const val TYPE_CREDENTIAL = 0x100E
    private const val TYPE_SSID = 0x1045
    private const val TYPE_NETWORK_KEY = 0x1027
    private const val TYPE_AUTH = 0x1003

    /** Bitmask of auth values per WSC spec. */
    private const val AUTH_OPEN = 0x0001
    private const val AUTH_WPA_PSK = 0x0002
    private const val AUTH_SHARED = 0x0004 // WEP
    private const val AUTH_WPA2_PSK = 0x0020
    private const val AUTH_SAE = 0x0040

    fun parse(bytes: ByteArray): TagPayload.WiFi? {
        if (bytes.isEmpty()) return null
        val buf = ByteBuffer.wrap(bytes)
        while (buf.remaining() >= 4) {
            val type = buf.short.toInt() and 0xFFFF
            val len = buf.short.toInt() and 0xFFFF
            if (len < 0 || len > buf.remaining()) return null
            if (type == TYPE_CREDENTIAL) {
                val credBytes = ByteArray(len)
                buf.get(credBytes)
                return parseCredential(credBytes)
            }
            buf.position(buf.position() + len)
        }
        return null
    }

    private fun parseCredential(bytes: ByteArray): TagPayload.WiFi? {
        val buf = ByteBuffer.wrap(bytes)
        var ssid: String? = null
        var password: String? = null
        var authBits = 0

        while (buf.remaining() >= 4) {
            val type = buf.short.toInt() and 0xFFFF
            val len = buf.short.toInt() and 0xFFFF
            if (len < 0 || len > buf.remaining()) return null
            val value = ByteArray(len)
            buf.get(value)
            when (type) {
                TYPE_SSID -> ssid = value.toString(StandardCharsets.UTF_8)
                TYPE_NETWORK_KEY -> password = value.toString(StandardCharsets.UTF_8)
                TYPE_AUTH -> if (value.size >= 2) {
                    authBits = (value[0].toInt() and 0xFF) shl 8 or (value[1].toInt() and 0xFF)
                }
            }
        }
        if (ssid.isNullOrEmpty()) return null

        val security = when {
            authBits and AUTH_SAE != 0 -> TagPayload.WiFi.Security.WPA3_SAE
            authBits and (AUTH_WPA2_PSK or AUTH_WPA_PSK) != 0 -> TagPayload.WiFi.Security.WPA_WPA2_PSK
            authBits and AUTH_SHARED != 0 -> TagPayload.WiFi.Security.WEP
            authBits and AUTH_OPEN != 0 -> TagPayload.WiFi.Security.OPEN
            else -> TagPayload.WiFi.Security.UNKNOWN
        }

        return TagPayload.WiFi(
            ssid = ssid,
            password = password.takeUnless { it.isNullOrEmpty() },
            security = security,
            hidden = false,
        )
    }

    /**
     * Parses the compact string format produced by most QR/NFC Wi-Fi tags:
     *   WIFI:T:WPA;S:<ssid>;P:<password>;H:<true|false>;;
     * Returns null if the scheme does not match or required fields are missing.
     */
    fun parseUriForm(raw: String): TagPayload.WiFi? {
        if (!raw.startsWith("WIFI:", ignoreCase = true)) return null
        val body = raw.substring(5).trimEnd(';')
        val map = mutableMapOf<String, String>()
        val token = StringBuilder()
        var key: String? = null
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> { token.append(body[i + 1]); i++ }
                c == ':' && key == null -> { key = token.toString(); token.clear() }
                c == ';' -> {
                    if (key != null) map[key!!.uppercase()] = token.toString()
                    key = null
                    token.clear()
                }
                else -> token.append(c)
            }
            i++
        }
        if (key != null) map[key!!.uppercase()] = token.toString()

        val ssid = map["S"].orEmpty()
        if (ssid.isEmpty()) return null
        val security = when (map["T"]?.uppercase()) {
            "WPA", "WPA2", "WPA/WPA2" -> TagPayload.WiFi.Security.WPA_WPA2_PSK
            "SAE", "WPA3" -> TagPayload.WiFi.Security.WPA3_SAE
            "WEP" -> TagPayload.WiFi.Security.WEP
            "NOPASS", "" -> TagPayload.WiFi.Security.OPEN
            null -> TagPayload.WiFi.Security.UNKNOWN
            else -> TagPayload.WiFi.Security.UNKNOWN
        }
        return TagPayload.WiFi(
            ssid = ssid,
            password = map["P"].takeUnless { it.isNullOrEmpty() },
            security = security,
            hidden = map["H"].equals("true", ignoreCase = true),
        )
    }
}
