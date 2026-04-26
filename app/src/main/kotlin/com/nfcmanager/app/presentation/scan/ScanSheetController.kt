package com.nfcmanager.app.presentation.scan

import com.nfcmanager.app.data.nfc.NfcTagCaptureManager
import com.nfcmanager.app.domain.model.NfcTag
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-wide controller for the **capture** bottom sheet (action
 * enrollment) and the [ScanMode] gate so [com.nfcmanager.app.nfc.NfcTagEventHandler]
 * ignores taps while UID capture is active.
 *
 * Passive Home scanning uses [com.nfcmanager.app.presentation.nfc.NfcConfirmController]
 * instead of the manual scan sheet.
 */
@Singleton
class ScanSheetController @Inject constructor() {

    enum class Mode {
        Manual,
        Capture,
    }

    enum class ScanMode {
        Idle,
        Capture,
    }

    data class State(
        val visible: Boolean = false,
        val mode: Mode = Mode.Manual,
        val detected: NfcTag? = null,
        val capturing: Boolean = false,
        val capturedIdentity: NfcTagCaptureManager.TagIdentity? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _scanMode = MutableStateFlow(ScanMode.Idle)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    private val _captureResults = MutableSharedFlow<NfcTagCaptureManager.TagIdentity>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val captureResults: Flow<NfcTagCaptureManager.TagIdentity> = _captureResults.asSharedFlow()

    fun openCapture() {
        _actionInProgress.value = false
        _scanMode.value = ScanMode.Capture
        _state.value = State(visible = true, mode = Mode.Capture, capturing = true)
    }

    fun close() {
        _scanMode.value = ScanMode.Idle
        _state.value = State()
    }

    fun isCaptureMode(): Boolean = _scanMode.value == ScanMode.Capture

    fun currentScanMode(): ScanMode = _scanMode.value

    fun isActionInProgress(): Boolean = _actionInProgress.value

    fun setActionInProgress(value: Boolean) {
        _actionInProgress.value = value
    }

    /** Legacy hook — unused for Home (confirm sheet replaces manual scan). */
    fun onTagDetected(tag: NfcTag) {
        val current = _state.value
        if (!current.visible || current.mode != Mode.Manual) return
        _state.value = current.copy(detected = tag, error = null)
    }

    fun reportCaptureSuccess(identity: NfcTagCaptureManager.TagIdentity) {
        val current = _state.value
        if (!current.visible || current.mode != Mode.Capture) return
        _state.value = current.copy(
            capturing = false,
            capturedIdentity = identity,
            error = null,
        )
        _captureResults.tryEmit(identity)
    }

    fun reportCaptureError(message: String) {
        val current = _state.value
        if (!current.visible) return
        _state.value = current.copy(capturing = false, error = message)
    }
}
