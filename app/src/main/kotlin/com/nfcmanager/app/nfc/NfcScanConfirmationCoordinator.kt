package com.nfcmanager.app.nfc

import android.app.Activity
import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.presentation.scan.ScanSheetController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the aftermath of a tag read that has no associated action
 * (passive read → popup closed by the user).
 *
 * Action execution is now done inline in [NfcTagEventHandler] (foreground
 * Reader Mode) and [com.nfcmanager.app.presentation.nfc.NfcDispatchViewModel]
 * (system NFC dispatch), so this coordinator only handles cleanup of Reader
 * Mode and scan-sheet state on confirm/dismiss.
 */
@Singleton
class NfcScanConfirmationCoordinator @Inject constructor(
    private val readerManager: NfcReaderManager,
    private val scanSheet: ScanSheetController,
) {

    /**
     * User closed the no-action popup on [com.nfcmanager.app.MainActivity]
     * (Close / swipe away).
     *
     * [disableReader] is false here because the user is still inside
     * MainActivity — Reader Mode stays on so the next tap works immediately.
     */
    fun onPassiveReadAcknowledged(
        @Suppress("UNUSED_PARAMETER") tag: NfcTag,
        activity: Activity,
        disableReader: Boolean,
    ) {
        if (disableReader && !readerManager.isTagCaptureInProgress()) {
            readerManager.disable(activity)
        }
    }

    /**
     * Sheet was dismissed without acknowledgement (swipe / back).
     *
     * @param stopReader Pass `true` for [com.nfcmanager.app.NfcDispatchActivity]
     *   so Reader Mode is torn down with the activity.
     */
    fun onConfirmationDismissed(activity: Activity, stopReader: Boolean) {
        if (stopReader && !readerManager.isTagCaptureInProgress()) {
            readerManager.disable(activity)
        }
        scanSheet.setActionInProgress(false)
    }
}
