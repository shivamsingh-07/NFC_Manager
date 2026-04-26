package com.nfcmanager.app.domain.model

/**
 * Parsed, strongly-typed representation of an NDEF payload.
 *
 * Using a sealed hierarchy keeps parsing, rendering, and action-handling
 * type-safe and exhaustive across the presentation layer and notification actions.
 */
sealed interface TagPayload {

    /** A single plain-text NDEF record (RTD_TEXT, RFC 5646 language prefix). */
    data class Text(
        val text: String,
        val languageCode: String = "en",
    ) : TagPayload

    /**
     * A URI record. The raw scheme+value is preserved; [sanitizedUrl] is non-null
     * only when validation deemed it safe to auto-launch via an implicit intent.
     */
    data class Uri(
        val raw: String,
        val sanitizedUrl: String?,
    ) : TagPayload {
        val isSafeToOpen: Boolean get() = sanitizedUrl != null
    }

    /** A parsed vCard (text/x-vCard or text/vcard). */
    data class Contact(
        val displayName: String?,
        val phoneNumbers: List<String>,
        val emails: List<String>,
        val organization: String?,
        val rawVCard: String,
    ) : TagPayload

    /** A Wi-Fi configuration (WPS / simple WiFi Simple Config token). */
    data class WiFi(
        val ssid: String,
        val password: String?,
        val security: Security,
        val hidden: Boolean,
    ) : TagPayload {
        enum class Security { OPEN, WEP, WPA_WPA2_PSK, WPA3_SAE, UNKNOWN }
    }

    /** Bluetooth device information (OOB pairing). */
    data class Bluetooth(
        val deviceName: String,
        val macAddress: String,
    ) : TagPayload

    /**
     * Fallback for unknown MIME types / external types / unparseable records.
     * Keeps raw bytes around so the UI can show hex preview.
     */
    data class Raw(
        val mimeType: String?,
        val bytes: ByteArray,
    ) : TagPayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Raw) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int =
            31 * (mimeType?.hashCode() ?: 0) + bytes.contentHashCode()
    }
}
