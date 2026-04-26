package com.nfcmanager.app.data.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual NFC Forum Type 4 Tag NDEF reader, executed over [IsoDep].
 *
 * Why this exists
 * ----------------
 * When you tap two phones together using the app's HCE-NDEF send path, the
 * receiver's stack reports the peer as an `IsoDep` tag. Stock Android *should*
 * then automatically run the Type 4 NDEF probe (SELECT AID → SELECT CC → READ
 * → SELECT NDEF → READ) and surface a populated [android.nfc.tech.Ndef]
 * tech with `cachedNdefMessage`. In practice the auto-probe is unreliable
 * across OEMs:
 *
 *   - Pixels usually do it correctly.
 *   - Many Samsung, OnePlus, Xiaomi builds either skip the probe entirely
 *     for HCE peers or use a timeout so tight that real-world phone-to-phone
 *     emulation, which is slower than a dedicated card, fails the probe.
 *
 * The visible bug is exactly the one being audited: the receiver reports an
 * "Empty Tag" (no NDEF tech, no cached message), and the sender's HCE service
 * sees only a `SELECT AID` followed by deselect — never the follow-up reads
 * that would let it confirm a successful transfer.
 *
 * The fix is to stop trusting the system probe and run the protocol ourselves
 * whenever the peer is IsoDep and the system gave us no NDEF.
 *
 * What this implements
 * --------------------
 * NFC Forum T4TOP §5.4–§5.6 read flow:
 *
 *   1. SELECT NDEF Application by AID `D2 76 00 00 85 01 01` (P1=0x04).
 *   2. SELECT EF by File ID for the Capability Container `E1 03` (P1=0x00, P2=0x0C).
 *   3. READ BINARY (15 bytes) → parse CC for the NDEF File ID and max NDEF size.
 *   4. SELECT EF for the NDEF file (typically `E1 04`, but the CC is canonical).
 *   5. READ BINARY (2 bytes) → NLEN.
 *   6. READ BINARY in chunks (≤ `IsoDep.maxTransceiveLength` − response overhead)
 *      until NLEN bytes of NDEF have been pulled.
 *
 * All transceives run on the calling thread, which must NOT be the main
 * thread — IsoDep I/O is documented as forbidden there. Callers in this
 * codebase invoke it from `Dispatchers.IO`.
 */
@Singleton
class Type4NdefReader @Inject constructor() {

    /**
     * Attempts to read an NDEF message from [tag] using the Type 4 protocol
     * over IsoDep. Returns `null` if the tag does not expose IsoDep, the AID
     * is not present, the tag does not behave as a Type 4 NDEF tag, or any
     * step fails.
     *
     * Never throws — IO errors and protocol-level rejections are swallowed
     * and reported as `null` (with a logcat warning), so callers can treat
     * this purely as a best-effort fallback.
     */
    fun read(tag: Tag): NdefMessage? {
        val isoDep = IsoDep.get(tag) ?: run {
            Log.d(TAG, "read: tag has no IsoDep tech, skipping")
            Log.d(READER_TAG, "Type4 read: no IsoDep tech")
            return null
        }

        val started = System.nanoTime()
        return runCatching {
            isoDep.timeout = ISO_DEP_TIMEOUT_MS
            connectWithRetry(isoDep)
            doRead(isoDep)
        }.onFailure { t ->
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            Log.w(TAG, "Type 4 NDEF read failed after ${elapsedMs}ms", t)
            // Mirror the failure under the NfcReaderManager tag too, so it
            // appears in the standard `-s NfcReaderManager:V` capture without
            // needing to also pass `Type4NdefReader:V`.
            Log.w(
                READER_TAG,
                "Type4 read failed after ${elapsedMs}ms (${t.javaClass.simpleName}: ${t.message})",
            )
        }.also {
            runCatching { isoDep.close() }
        }.getOrNull()
    }

    /**
     * Establish the IsoDep link, with explicit retry.
     *
     * Why: the system's own NDEF auto-probe — which fires before our
     * [NfcAdapter.ReaderCallback] is invoked unless `FLAG_READER_SKIP_NDEF_CHECK`
     * is set — opens IsoDep against the HCE peer, runs through SELECT AID,
     * and closes it again. On Samsung / OnePlus / Xiaomi stacks the IsoDep
     * session can briefly be in a "still tearing down" state when our callback
     * runs, and a same-cycle [IsoDep.connect] returns failure in 1–5 ms.
     *
     * Sleeping 100 ms and retrying is enough to clear that race in practice.
     * The retry is deliberately short and bounded — if the peer is really gone
     * we want to fail fast and let the user re-tap rather than hold the sheet
     * open for seconds.
     */
    private fun connectWithRetry(isoDep: IsoDep) {
        var lastFailure: IOException? = null
        for (attempt in 1..CONNECT_MAX_ATTEMPTS) {
            try {
                val started = System.nanoTime()
                isoDep.connect()
                val tookMs = (System.nanoTime() - started) / 1_000_000
                if (attempt > 1) {
                    Log.i(TAG, "IsoDep connect OK on attempt $attempt (${tookMs}ms)")
                    Log.i(READER_TAG, "Type4 connect OK on retry $attempt (${tookMs}ms)")
                } else {
                    Log.d(TAG, "IsoDep connect OK on attempt 1 (${tookMs}ms)")
                }
                return
            } catch (e: IOException) {
                lastFailure = e
                Log.w(
                    TAG,
                    "IsoDep connect attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}",
                )
                Log.w(
                    READER_TAG,
                    "Type4 connect attempt $attempt failed (${e.javaClass.simpleName})",
                )
                if (attempt < CONNECT_MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(CONNECT_RETRY_DELAY_MS)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ie
                    }
                }
            }
        }
        throw lastFailure ?: IOException("IsoDep connect failed (no exception captured)")
    }

    private fun doRead(isoDep: IsoDep): NdefMessage? {
        // 1) SELECT NDEF AID. Le=0x00 → up to 256 response bytes; standard form.
        val selectAid = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, NDEF_AID.size.toByte(),
        ) + NDEF_AID + byteArrayOf(0x00)
        if (!isOk(isoDep.transceive(selectAid).also { logResp("SELECT AID", it) })) {
            failStep("SELECT AID rejected; not a Type 4 NDEF peer")
            return null
        }

        // 2) SELECT CC by File ID (E1 03). P2=0x0C → no FCI.
        val selectCc = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03,
        )
        if (!isOk(isoDep.transceive(selectCc).also { logResp("SELECT CC", it) })) {
            failStep("SELECT CC rejected; peer is not Type 4 NDEF compliant")
            return null
        }

        // 3) READ BINARY 15 bytes (CC fixed length per T4TOP §5.5.1).
        val readCc = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, CC_LENGTH.toByte())
        val ccResp = isoDep.transceive(readCc).also { logResp("READ CC", it) }
        if (!isOk(ccResp) || ccResp.size < CC_LENGTH + SW_LENGTH) {
            failStep("READ CC failed; size=${ccResp.size}")
            return null
        }
        val cc = ccResp.copyOfRange(0, ccResp.size - SW_LENGTH)
        val ndefDescriptor = parseCapabilityContainer(cc) ?: run {
            failStep("CC parse failed")
            return null
        }

        // 4) SELECT NDEF EF using the file ID advertised by the CC, not a
        //    hard-coded E1 04 — that's what makes us spec-compliant.
        val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02) +
            ndefDescriptor.fileId
        if (!isOk(isoDep.transceive(selectNdef).also { logResp("SELECT NDEF", it) })) {
            failStep("SELECT NDEF rejected")
            return null
        }

        // 5) READ NLEN (first 2 bytes of the NDEF file are big-endian length).
        val readNlen = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, NLEN_LENGTH.toByte())
        val nlenResp = isoDep.transceive(readNlen).also { logResp("READ NLEN", it) }
        if (!isOk(nlenResp) || nlenResp.size < NLEN_LENGTH + SW_LENGTH) {
            failStep("READ NLEN failed; size=${nlenResp.size}")
            return null
        }
        val nlen = ((nlenResp[0].toInt() and 0xFF) shl 8) or (nlenResp[1].toInt() and 0xFF)
        if (nlen <= 0 || nlen > ndefDescriptor.maxNdefSize) {
            failStep("NLEN=$nlen out of range (max=${ndefDescriptor.maxNdefSize})")
            return null
        }

        // 6) READ BINARY in chunks. Cap chunk size to whatever the remote
        //    R-APDU accepts (CC.MaxResponseSize), and to what our IsoDep
        //    transceive can carry, leaving 2 bytes for SW.
        val perRead = chunkSize(isoDep.maxTransceiveLength, ndefDescriptor.maxResponseSize)
        if (perRead <= 0) {
            failStep("computed chunk size <= 0; aborting")
            return null
        }
        val ndefBytes = ByteArray(nlen)
        var read = 0
        var offset = NLEN_LENGTH
        while (read < nlen) {
            val want = (nlen - read).coerceAtMost(perRead)
            val cmd = byteArrayOf(
                0x00, 0xB0.toByte(),
                ((offset ushr 8) and 0xFF).toByte(),
                (offset and 0xFF).toByte(),
                want.toByte(),
            )
            val resp = isoDep.transceive(cmd).also { logResp("READ NDEF[$offset:+$want]", it) }
            if (!isOk(resp) || resp.size < want + SW_LENGTH) {
                failStep("READ NDEF chunk failed at offset=$offset want=$want got=${resp.size}")
                return null
            }
            System.arraycopy(resp, 0, ndefBytes, read, want)
            read += want
            offset += want
        }

        return try {
            NdefMessage(ndefBytes)
        } catch (e: FormatException) {
            Log.w(TAG, "Manually-read bytes are not valid NDEF", e)
            Log.w(READER_TAG, "Type4 read produced invalid NDEF (${ndefBytes.size}B)")
            null
        }
    }

    /** Log a protocol-step rejection on both this tag and the NfcReaderManager tag. */
    private fun failStep(message: String) {
        Log.w(TAG, message)
        Log.w(READER_TAG, "Type4 step failed: $message")
    }

    private data class NdefFile(
        val fileId: ByteArray,
        val maxNdefSize: Int,
        val maxResponseSize: Int,
    )

    /**
     * Parses the 15-byte Capability Container per T4TOP §5.5.1.
     * Layout:
     *   00 0F                  CCLEN (always 15 for v2.0/3.0)
     *   20                     mapping version 2.0
     *   00 3B                  Max R-APDU size
     *   00 34                  Max C-APDU size
     *   04 06                  NDEF File Control TLV (T=04, L=06)
     *   FF FF                  NDEF File ID
     *   FF FF                  Max NDEF File size
     *   00                     Read access
     *   00 / FF                Write access
     */
    private fun parseCapabilityContainer(cc: ByteArray): NdefFile? {
        if (cc.size < 15) {
            Log.w(TAG, "CC too short: ${cc.size}")
            return null
        }
        val maxRApdu = ((cc[3].toInt() and 0xFF) shl 8) or (cc[4].toInt() and 0xFF)
        val tlvType = cc[7]
        val tlvLen = cc[8].toInt() and 0xFF
        if (tlvType != 0x04.toByte() || tlvLen != 0x06) {
            Log.w(TAG, "Unexpected NDEF File Control TLV: T=$tlvType L=$tlvLen")
            return null
        }
        val fileId = byteArrayOf(cc[9], cc[10])
        val maxNdef = ((cc[11].toInt() and 0xFF) shl 8) or (cc[12].toInt() and 0xFF)
        val readAccess = cc[13]
        if (readAccess != 0x00.toByte()) {
            Log.w(TAG, "NDEF file is not freely readable: access=$readAccess")
            return null
        }
        return NdefFile(fileId = fileId, maxNdefSize = maxNdef, maxResponseSize = maxRApdu)
    }

    private fun chunkSize(transceiveCap: Int, ccCap: Int): Int {
        // Some buggy stacks return 0 for maxTransceiveLength. Pick the most
        // conservative non-zero of the two budgets, minus SW overhead, capped
        // at 0xFD so we always fit Le in a single byte (255 max, and we want
        // headroom for the SW).
        val budgets = listOf(transceiveCap, ccCap).filter { it > SW_LENGTH }
        val raw = (budgets.minOrNull() ?: DEFAULT_CHUNK) - SW_LENGTH
        return raw.coerceIn(1, 0xFD)
    }

    private fun isOk(response: ByteArray?): Boolean {
        if (response == null || response.size < 2) return false
        return response[response.size - 2] == 0x90.toByte() &&
            response[response.size - 1] == 0x00.toByte()
    }

    private fun logResp(label: String, resp: ByteArray) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        Log.d(TAG, "$label ← ${resp.toHex()}")
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = " ") { "%02X".format(it.toInt() and 0xFF) }

    companion object {
        private const val TAG = "Type4NdefReader"
        // We mirror critical failure logs under this tag too. Most diagnostic
        // captures already filter on `NfcReaderManager:V`, and forcing the user
        // to remember a second tag for every NFC bug report is a footgun.
        private const val READER_TAG = "NfcReaderManager"
        private const val ISO_DEP_TIMEOUT_MS = 3_000
        private const val CC_LENGTH = 15
        private const val NLEN_LENGTH = 2
        private const val SW_LENGTH = 2
        private const val DEFAULT_CHUNK = 0x3B
        // 1 try + 2 retries = 3 attempts. Total worst case = 2 * delay before
        // we give up and let the user re-tap (~200ms with default delay).
        private const val CONNECT_MAX_ATTEMPTS = 3
        private const val CONNECT_RETRY_DELAY_MS = 100L

        private val NDEF_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
    }
}
