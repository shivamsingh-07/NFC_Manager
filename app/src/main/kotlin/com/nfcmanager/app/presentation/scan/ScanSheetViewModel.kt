package com.nfcmanager.app.presentation.scan

import androidx.lifecycle.ViewModel
import com.nfcmanager.app.data.nfc.NfcReaderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin facade over [ScanSheetController] for Compose. Lives in the Activity
 * scope so the sheet survives configuration changes; the underlying
 * controller is a [javax.inject.Singleton] so multiple observers (host,
 * Create-action flow) see the same state.
 */
@HiltViewModel
class ScanSheetViewModel @Inject constructor(
    private val controller: ScanSheetController,
    private val readerManager: NfcReaderManager,
) : ViewModel() {

    val state: StateFlow<ScanSheetController.State> = controller.state

    fun dismiss() {
        // Capture mode used the dedicated [NfcTagCaptureManager] reader; if it
        // is somehow still flagged when the user cancels, clear the gate so
        // the foreground reader can resume.
        if (readerManager.isTagCaptureInProgress()) {
            readerManager.setTagCaptureInProgress(false)
        }
        controller.close()
    }
}
