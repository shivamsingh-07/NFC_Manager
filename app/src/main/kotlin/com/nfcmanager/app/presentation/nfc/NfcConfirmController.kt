package com.nfcmanager.app.presentation.nfc

import com.nfcmanager.app.domain.model.NfcTag
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the in-app "NFC tag detected" info bottom sheet shown over
 * [com.nfcmanager.app.MainActivity] for foreground Reader Mode reads that
 * have **no associated action**.
 *
 * Tags with a mapped action are executed immediately by [NfcTagEventHandler]
 * and never reach this controller.
 */
@Singleton
class NfcConfirmController @Inject constructor() {

    sealed interface Pending {
        data object Hidden : Pending
        /** A non-action tag is waiting for the user to acknowledge the popup. */
        data class Visible(val tag: NfcTag) : Pending
    }

    private val _pending = MutableStateFlow<Pending>(Pending.Hidden)
    val pending: StateFlow<Pending> = _pending.asStateFlow()

    private var lastUid: String? = null
    private var lastShownAtMillis: Long = 0L

    /**
     * Shows the contextual tag info sheet for [tag].
     * Built-in deduplication prevents the same UID from opening the sheet
     * twice within [DEBOUNCE_MS] milliseconds.
     */
    fun show(tag: NfcTag) {
        val now = System.currentTimeMillis()
        if (tag.uid == lastUid && now - lastShownAtMillis < DEBOUNCE_MS) return
        lastUid = tag.uid
        lastShownAtMillis = now
        _pending.value = Pending.Visible(tag)
    }

    fun dismiss() {
        _pending.value = Pending.Hidden
    }

    companion object {
        private const val DEBOUNCE_MS = 2_000L
    }
}
