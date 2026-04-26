package com.nfcmanager.app.domain.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Produces a privacy-preserving, stable-per-install hash of an NFC tag UID.
 *
 * The hash is `SHA-256(salt || uidHex)` where `salt` is a cryptographically
 * random 32-byte value generated on first run and persisted in a dedicated
 * DataStore. Consequences:
 *
 *  - UIDs are never stored in plaintext.
 *  - Two different installations produce different hashes for the same tag,
 *    so the local database is not a tracking vector if exfiltrated.
 *  - Uninstalling the app rotates the salt, which is the intended privacy
 *    trade-off (mappings are wiped by the OS with app data regardless).
 *
 * The UID is normalised to uppercase hex without separators before hashing
 * so that minor byte-ordering differences in the source don't diverge.
 */
private val Context.uidSaltStore by preferencesDataStore(name = "uid_salt")

@Singleton
class UidHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val saltKey = stringPreferencesKey("salt_hex")

    suspend fun hash(uidBytes: ByteArray): String {
        require(uidBytes.isNotEmpty()) { "UID must not be empty" }
        val salt = loadOrCreateSalt()
        return hashWith(salt, uidBytes)
    }

    /** Hash a UID already normalised to uppercase hex (no separators). */
    suspend fun hashHex(uidHex: String): String {
        require(uidHex.isNotBlank()) { "UID hex must not be blank" }
        val bytes = hexToBytes(uidHex)
        return hash(bytes)
    }

    /** Exposed for tests + deterministic internal use. */
    internal fun hashWith(saltBytes: ByteArray, uidBytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltBytes)
        md.update(normalizeHex(uidBytes).toByteArray(Charsets.US_ASCII))
        return md.digest().toHex()
    }

    private suspend fun loadOrCreateSalt(): ByteArray {
        val existing = context.uidSaltStore.data.map { it[saltKey] }.first()
        if (!existing.isNullOrBlank()) {
            runCatching { return hexToBytes(existing) }
        }
        val fresh = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        context.uidSaltStore.edit { it[saltKey] = fresh.toHex() }
        return fresh
    }

    companion object {
        private const val SALT_BYTES = 32

        fun normalizeHex(uidBytes: ByteArray): String =
            uidBytes.joinToString(separator = "") { "%02X".format(it) }

        private fun ByteArray.toHex(): String =
            joinToString(separator = "") { "%02x".format(it) }

        private fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Invalid hex string" }
            return ByteArray(hex.length / 2) { i ->
                ((Character.digit(hex[i * 2], 16) shl 4) +
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
        }
    }
}
