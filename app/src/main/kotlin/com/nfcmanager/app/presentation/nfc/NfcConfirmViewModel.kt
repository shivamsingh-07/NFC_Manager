package com.nfcmanager.app.presentation.nfc

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.nfcmanager.app.nfc.NfcScanConfirmationCoordinator
import com.nfcmanager.app.presentation.nfc.NfcConfirmController.Pending
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Backs the in-app NFC tag info sheet (non-action tags only).
 *
 * Actions are now executed immediately in [com.nfcmanager.app.nfc.NfcTagEventHandler]
 * and never reach this ViewModel — the `onContinue` (action-confirmation) path
 * has been removed accordingly.
 */
@HiltViewModel
class NfcConfirmViewModel @Inject constructor(
    private val controller: NfcConfirmController,
    private val coordinator: NfcScanConfirmationCoordinator,
) : ViewModel() {

    val pending: StateFlow<Pending> = controller.pending

    /**
     * User tapped "Close" on the no-action info sheet.
     */
    fun onClose(activity: Activity) {
        val state = controller.pending.value
        if (state !is Pending.Visible) return
        coordinator.onPassiveReadAcknowledged(state.tag, activity, disableReader = false)
        controller.dismiss()
    }

    /**
     * Sheet was dismissed by swipe / back without tapping Close.
     */
    fun onDismiss(activity: Activity) {
        controller.dismiss()
        coordinator.onConfirmationDismissed(activity, stopReader = false)
    }
}
