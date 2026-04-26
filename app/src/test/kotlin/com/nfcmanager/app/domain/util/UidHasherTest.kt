package com.nfcmanager.app.domain.util

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

/**
 * Unit tests for [UidHasher.hashWith], which is the pure function at the core
 * of the hasher. The suspend [UidHasher.hash] path adds async salt loading
 * via DataStore which is out of scope for a pure-JVM unit test.
 */
class UidHasherTest {

    private val hasher = UidHasher(mockContext())

    private val salt1 = ByteArray(32) { i -> i.toByte() }
    private val salt2 = ByteArray(32) { i -> (i + 1).toByte() }
    private val uidA = byteArrayOf(0x04, 0x1A, 0x2B.toByte(), 0x3C, 0x4D, 0x5E, 0x6F)
    private val uidB = byteArrayOf(0x04, 0x1A, 0x2B.toByte(), 0x3C, 0x4D, 0x5E, 0x70)

    @Test fun `same salt and uid produce same hash`() {
        assertThat(hasher.hashWith(salt1, uidA)).isEqualTo(hasher.hashWith(salt1, uidA))
    }

    @Test fun `different uid produces different hash`() {
        assertThat(hasher.hashWith(salt1, uidA)).isNotEqualTo(hasher.hashWith(salt1, uidB))
    }

    @Test fun `different salt produces different hash for same uid`() {
        assertThat(hasher.hashWith(salt1, uidA)).isNotEqualTo(hasher.hashWith(salt2, uidA))
    }

    @Test fun `hash output is hex and 64 chars long for sha-256`() {
        val h = hasher.hashWith(salt1, uidA)
        assertThat(h.length).isEqualTo(64)
        assertThat(h).matches("[0-9a-f]{64}")
    }

    @Test fun `normaliseHex returns uppercase hex without separators`() {
        assertThat(UidHasher.normalizeHex(byteArrayOf(0x0A, 0xFF.toByte(), 0x10)))
            .isEqualTo("0AFF10")
    }

    private fun mockContext(): android.content.Context {
        // hashWith + normalizeHex never touch the context; a relaxed mock is fine.
        return mockk(relaxed = true)
    }
}
