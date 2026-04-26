package com.nfcmanager.app.data.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * NFC Forum Type 4 Tag emulation over HCE.
 *
 * Lets another Android device (running this app or any standard NFC reader)
 * tap-and-pull the NDEF message currently armed in [EmulationController].
 *
 * Compliant with the NFC Forum Type 4 Tag Operation Specification (T4TOP)
 * sections 5.4–5.6:
 *
 *   1. SELECT NDEF AID                  → 9000
 *   2. SELECT FILE  E1 03  (CC)         → 9000
 *   3. READ BINARY (CC)                  → 15-byte CC + 9000
 *   4. SELECT FILE  E1 04  (NDEF)       → 9000
 *   5. READ BINARY (NDEF, NLEN bytes)   → 2-byte length + NDEF + 9000
 *
 * Hardening applied (vs. the previous version):
 *
 *  - All array indexing is bounds-checked. A malformed reader can no longer
 *    crash the binder.
 *  - Le == 0x00 in a short APDU correctly resolves to 256 bytes (ISO 7816-4).
 *  - Payload is fetched from [EmulationController] on every command (not a
 *    captured static), so disarm()-ing on the UI thread takes effect for the
 *    very next reader command.
 *  - [onDeactivated] notifies the controller so it can clear the payload
 *    after a successful peer read — no perpetual sharing.
 */
@AndroidEntryPoint
class MessageEmulationService : HostApduService() {

    @Inject lateinit var controller: EmulationController

    private var selectedFile: SelectedFile = SelectedFile.None
    private var transferred: Boolean = false

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_FILE_NOT_FOUND
        val response = runCatching { dispatch(apdu) }
            .getOrElse { t ->
                Log.w(TAG, "APDU dispatch failed", t)
                SW_FILE_NOT_FOUND
            }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "C-APDU ${apdu.toHex()}  →  R-APDU ${response.toHex()}")
        }
        return response
    }

    private fun dispatch(apdu: ByteArray): ByteArray {
        // Need at least the 4-byte ISO-7816 header.
        if (apdu.size < 4) return SW_FILE_NOT_FOUND

        val cla = apdu[0]
        val ins = apdu[1]
        val p1 = apdu[2]
        val p2 = apdu[3]

        if (cla != CLA_DEFAULT) return SW_FILE_NOT_FOUND

        return when (ins) {
            INS_SELECT -> handleSelect(apdu, p1, p2)
            INS_READ_BINARY -> handleReadBinary(apdu, p1, p2)
            else -> SW_FILE_NOT_FOUND
        }
    }

    private fun handleSelect(apdu: ByteArray, p1: Byte, p2: Byte): ByteArray {
        // SELECT short APDU: [CLA INS P1 P2 Lc data...]
        if (apdu.size < 5) return SW_FILE_NOT_FOUND
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return SW_FILE_NOT_FOUND
        val data = apdu.copyOfRange(5, 5 + lc)

        return when (p1) {
            P1_SELECT_BY_NAME -> if (data.contentEquals(NDEF_AID)) {
                controller.notifyPeerConnected()
                SW_OK
            } else SW_FILE_NOT_FOUND
            P1_SELECT_BY_FILE_ID -> {
                if (data.size != 2) return SW_FILE_NOT_FOUND
                selectedFile = when {
                    data.contentEquals(FILE_ID_CC) -> SelectedFile.CapabilityContainer
                    data.contentEquals(FILE_ID_NDEF) -> SelectedFile.Ndef
                    else -> {
                        selectedFile = SelectedFile.None
                        return SW_FILE_NOT_FOUND
                    }
                }
                SW_OK
            }
            else -> SW_FILE_NOT_FOUND
        }
    }

    private fun handleReadBinary(apdu: ByteArray, p1: Byte, p2: Byte): ByteArray {
        // [CLA INS P1 P2 Le]   — Le==0x00 means 256 in short APDU per ISO 7816-4.
        if (apdu.size < 5) return SW_FILE_NOT_FOUND
        val offset = ((p1.toInt() and 0xFF) shl 8) or (p2.toInt() and 0xFF)
        val leRaw = apdu[4].toInt() and 0xFF
        val le = if (leRaw == 0) 256 else leRaw

        val file = when (selectedFile) {
            SelectedFile.CapabilityContainer -> CAPABILITY_CONTAINER
            SelectedFile.Ndef -> ndefFileBytes() ?: return SW_FILE_NOT_FOUND
            SelectedFile.None -> return SW_FILE_NOT_FOUND
        }

        if (offset > file.size) return SW_FILE_NOT_FOUND
        val end = (offset + le).coerceAtMost(file.size)
        val slice = file.copyOfRange(offset, end)

        // Mark the transfer as having delivered the NDEF body so we know to
        // notify the controller in onDeactivated.
        if (selectedFile == SelectedFile.Ndef && offset + slice.size >= file.size) {
            transferred = true
        }
        return slice + SW_OK
    }

    private fun ndefFileBytes(): ByteArray? {
        val payload = controller.currentPayload() ?: run {
            Log.w(TAG, "ndefFileBytes: controller has no armed payload, returning 6A82")
            return null
        }
        // T4TOP §5.6: NDEF File = 2-byte big-endian NLEN || NDEF message.
        val out = ByteArray(2 + payload.size)
        out[0] = ((payload.size ushr 8) and 0xFF).toByte()
        out[1] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 2, payload.size)
        return out
    }

    override fun onDeactivated(reason: Int) {
        // Reader gone (REASON_LINK_LOSS) or another HCE service was selected
        // (REASON_DESELECTED). Either way, drop session state.
        Log.d(TAG, "onDeactivated reason=$reason transferred=$transferred")
        if (transferred) controller.notifyPeerRead() else controller.notifyPeerLost()
        selectedFile = SelectedFile.None
        transferred = false
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = " ") { "%02X".format(it.toInt() and 0xFF) }

    private enum class SelectedFile { None, CapabilityContainer, Ndef }

    companion object {
        private const val TAG = "MessageEmulationSvc"

        private const val CLA_DEFAULT: Byte = 0x00
        private const val INS_SELECT: Byte = 0xA4.toByte()
        private const val INS_READ_BINARY: Byte = 0xB0.toByte()
        private const val P1_SELECT_BY_NAME: Byte = 0x04
        private const val P1_SELECT_BY_FILE_ID: Byte = 0x00

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        // NFC Forum NDEF AID: D2 76 00 00 85 01 01
        private val NDEF_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )

        private val FILE_ID_CC = byteArrayOf(0xE1.toByte(), 0x03)
        private val FILE_ID_NDEF = byteArrayOf(0xE1.toByte(), 0x04)

        // Capability Container (15 bytes), per T4TOP §5.5.1.
        // Mapping v2.0, Max R-APDU 0x003B, Max C-APDU 0x0034, NDEF file
        // E104 with 0xFFFE max NDEF size, read access free, write access
        // disabled (0xFF).
        private val CAPABILITY_CONTAINER = byteArrayOf(
            0x00, 0x0F,
            0x20,
            0x00, 0x3B,
            0x00, 0x34,
            0x04, 0x06,
            0xE1.toByte(), 0x04,
            0xFF.toByte(), 0xFE.toByte(),
            0x00,
            0xFF.toByte(),
        )
    }
}
