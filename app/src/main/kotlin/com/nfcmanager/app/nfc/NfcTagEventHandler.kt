package com.nfcmanager.app.nfc

import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.domain.action.ActionExecutor
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.domain.repository.NfcActionRepository
import com.nfcmanager.app.domain.util.UidHasher
import com.nfcmanager.app.presentation.nfc.NfcConfirmController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foreground Reader Mode pipeline (MainActivity only).
 *
 * ## Decision flow (checked in order, fail-fast)
 *
 * 1. `isTagCaptureInProgress()` → drop (action registration owns the reader)
 * 2. `isHomeTab() == false`     → drop (Reader Mode must be off — defensive guard)
 * 3. `isInForeground() == false`→ drop (process backgrounded)
 * 4. Tag has mapped action      → execute immediately, NO popup
 * 5. No action                  → show contextual popup
 */
@Singleton
class NfcTagEventHandler @Inject constructor(
    private val readerManager: NfcReaderManager,
    private val actionRepository: NfcActionRepository,
    private val executor: ActionExecutor,
    private val uidHasher: UidHasher,
    private val confirmController: NfcConfirmController,
    private val mainReaderTabGate: NfcMainReaderTabGate,
) {

    /**
     * Called for every tag the main Reader Mode session detects.
     *
     * @return `true` when the event was consumed, `false` when dropped.
     */
    suspend fun onTagDetected(tag: NfcTag): Boolean {
        if (readerManager.isTagCaptureInProgress()) return false
        if (!mainReaderTabGate.isHomeTab()) return false
        if (!AppProcessForeground.isInForeground()) return false

        val matched = runCatching {
            val hash = uidHasher.hashHex(tag.uid)
            actionRepository.findByUidHash(hash)
        }.getOrNull()

        return if (matched != null) {
            executor.execute(matched)
            true
        } else {
            confirmController.show(tag)
            true
        }
    }
}
