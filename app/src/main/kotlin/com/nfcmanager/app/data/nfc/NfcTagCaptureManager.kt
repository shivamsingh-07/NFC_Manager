package com.nfcmanager.app.data.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import com.nfcmanager.app.domain.util.UidHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One-shot NFC tag identity capture used by the "Create action" flow.
 *
 * Runs its own Reader Mode session so it doesn't interfere with the main
 * [NfcReaderManager] subscription — callers must disable the main reader
 * before calling [awaitNextTag] and re-enable it after.
 *
 * Returns the hashed UID + tech signature (sorted, comma-joined tech classes).
 * The raw UID is deliberately never surfaced to UI code.
 */
@Singleton
class NfcTagCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uidHasher: UidHasher,
) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    data class TagIdentity(
        val uidHash: String,
        val techSignature: String,
    )

    /**
     * Suspends until a tag is presented to the reader, or the caller
     * cancels the coroutine. Reader Mode is disabled before returning.
     */
    suspend fun awaitNextTag(activity: Activity): TagIdentity {
        val nfc = adapter ?: throw IllegalStateException("NFC hardware not available")
        if (!nfc.isEnabled) throw IllegalStateException("NFC is disabled")

        val workerScope = CoroutineScope(Dispatchers.IO)
        return try {
            suspendCancellableCoroutine { cont ->
                val callback = NfcAdapter.ReaderCallback { tag: Tag ->
                    if (!cont.isActive) return@ReaderCallback
                    val uidBytes = tag.id
                    val techSig = tag.techList.orEmpty().sorted().joinToString(",")
                    // Stop the reader immediately — one tag is all we want.
                    runCatching { nfc.disableReaderMode(activity) }
                    // Hop to a coroutine to run the suspending hash (needs DataStore access).
                    workerScope.launch {
                        val hash = runCatching { uidHasher.hash(uidBytes) }.getOrNull()
                        if (cont.isActive) {
                            if (hash.isNullOrBlank()) {
                                cont.resumeWith(
                                    Result.failure(IllegalStateException("Failed to hash UID")),
                                )
                            } else {
                                cont.resume(TagIdentity(hash, techSig))
                            }
                        }
                    }
                }
                nfc.enableReaderMode(activity, callback, NfcReaderManager.READER_FLAGS, null)
                cont.invokeOnCancellation {
                    runCatching { nfc.disableReaderMode(activity) }
                }
            }
        } finally {
            workerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
