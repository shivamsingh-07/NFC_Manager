package com.nfcmanager.app.data.nfc

import android.nfc.NdefMessage
import androidx.annotation.AnyThread
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single, process-wide owner of the payload that [MessageEmulationService] is
 * currently exposing as an NDEF Type 4 tag.
 *
 * Replaces the previous static [MessageEmulationService.ndefMessageBytes] var
 * which had no synchronization, no timeout, and survived process death,
 * leaking the last-emulated payload across screens.
 *
 * The controller is the only thing that touches the service's payload. It is
 * read on a binder thread (HCE callbacks) and written from the main thread
 * (ViewModel), so all access goes through `@Volatile` + an atomic snapshot.
 */
@Singleton
class EmulationController @Inject constructor() {

    sealed interface State {
        data object Idle : State
        data class Ready(val byteSize: Int) : State
        data object Connected : State
        data object PeerRead : State
    }

    @Volatile
    private var armedBytes: ByteArray? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Hot snapshot used by the HCE service from a binder thread. */
    @AnyThread
    fun currentPayload(): ByteArray? = armedBytes

    /** Called by the ViewModel when the user taps "Emulate". */
    fun arm(message: NdefMessage) {
        val bytes = message.toByteArray()
        armedBytes = bytes
        _state.value = State.Ready(bytes.size)
    }

    /** Called by the ViewModel on cancel / timeout / process tear-down. */
    fun disarm() {
        armedBytes = null
        _state.value = State.Idle
    }

    /**
     * Called by [MessageEmulationService.handleSelect] when the peer selects
     * our NDEF AID, indicating a reader is in field and starting the exchange.
     */
    @AnyThread
    fun notifyPeerConnected() {
        if (_state.value is State.Ready) {
            _state.value = State.Connected
        }
    }

    /**
     * Called by [MessageEmulationService.onDeactivated] after a peer
     * successfully read the tag. We surface the event to the UI so it can
     * vibrate / show success, then auto-disarm so the payload is not still
     * shareable to a third device.
     */
    @AnyThread
    fun notifyPeerRead() {
        if (armedBytes != null) {
            _state.value = State.PeerRead
            armedBytes = null
        }
    }

    /**
     * Called by [MessageEmulationService.onDeactivated] when the RF link
     * dropped before the peer fully read the NDEF file. Most common cause:
     * the receiver's stack ran the SELECT AID auto-probe, decided it didn't
     * understand the response, and walked away — leaving the sender stuck on
     * "Peer Connected" forever. Roll the state back to Ready so the user can
     * try again without dismissing the sheet.
     */
    @AnyThread
    fun notifyPeerLost() {
        val bytes = armedBytes ?: return
        if (_state.value is State.Connected) {
            _state.value = State.Ready(bytes.size)
        }
    }
}
