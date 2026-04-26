package com.nfcmanager.app.data.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.util.Log
import com.nfcmanager.app.domain.model.WriteResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Drives one-shot writes in Reader Mode.
 *
 * Usage:
 *   writeManager.armForWrite(activity, message) { result -> ... }
 *   // user taps tag
 *   writeManager.disarm(activity)
 *
 * The manager owns its own Reader Mode session so writes don't race with the
 * normal scan session; the caller disables that one first and re-enables on
 * completion (handled by the ViewModel).
 */
@Singleton
class NfcWriteManager @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    fun isSupported(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    suspend fun awaitAndWrite(
        activity: Activity,
        message: NdefMessage,
        makeReadOnly: Boolean = false,
    ): WriteResult = suspendCancellableCoroutine { cont ->
        val nfc = adapter
        if (nfc == null) {
            cont.resume(WriteResult.Failure(WriteResult.Reason.UNSUPPORTED_TAG)); return@suspendCancellableCoroutine
        }
        if (!nfc.isEnabled) {
            cont.resume(WriteResult.Failure(WriteResult.Reason.IO_ERROR)); return@suspendCancellableCoroutine
        }
        val options = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        val callback = NfcAdapter.ReaderCallback { tag ->
            if (!cont.isActive) return@ReaderCallback
            val result = writeBlocking(tag, message, makeReadOnly)
            runCatching { nfc.disableReaderMode(activity) }
            if (cont.isActive) cont.resume(result)
        }
        nfc.enableReaderMode(activity, callback, NfcReaderManager.READER_FLAGS, options)

        cont.invokeOnCancellation {
            runCatching { nfc.disableReaderMode(activity) }
        }
    }

    private fun writeBlocking(
        tag: Tag,
        message: NdefMessage,
        makeReadOnly: Boolean,
    ): WriteResult {
        val ndef = Ndef.get(tag)
        return try {
            if (ndef != null) writeNdef(ndef, message, makeReadOnly)
            else writeFormatable(tag, message, makeReadOnly)
        } catch (t: Throwable) {
            Log.w(TAG, "Write failed", t)
            WriteResult.Failure(WriteResult.Reason.UNKNOWN, t)
        }
    }

    private fun writeNdef(ndef: Ndef, message: NdefMessage, makeReadOnly: Boolean): WriteResult {
        return try {
            ndef.connect()
            if (!ndef.isWritable) return WriteResult.Failure(WriteResult.Reason.READ_ONLY)

            val size = message.toByteArray().size
            if (ndef.maxSize in 1 until size) {
                return WriteResult.Failure(WriteResult.Reason.INSUFFICIENT_CAPACITY)
            }

            ndef.writeNdefMessage(message)
            if (makeReadOnly && ndef.canMakeReadOnly()) {
                runCatching { ndef.makeReadOnly() }
            }
            WriteResult.Success
        } catch (e: TagLostException) {
            WriteResult.Failure(WriteResult.Reason.TAG_LOST, e)
        } catch (e: android.nfc.FormatException) {
            WriteResult.Failure(WriteResult.Reason.MALFORMED_PAYLOAD, e)
        } catch (e: IOException) {
            WriteResult.Failure(WriteResult.Reason.IO_ERROR, e)
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun writeFormatable(tag: Tag, message: NdefMessage, makeReadOnly: Boolean): WriteResult {
        val formatable = NdefFormatable.get(tag)
            ?: return WriteResult.Failure(WriteResult.Reason.UNSUPPORTED_TAG)
        return try {
            formatable.connect()
            if (makeReadOnly) formatable.formatReadOnly(message) else formatable.format(message)
            WriteResult.Success
        } catch (e: TagLostException) {
            WriteResult.Failure(WriteResult.Reason.TAG_LOST, e)
        } catch (e: android.nfc.FormatException) {
            WriteResult.Failure(WriteResult.Reason.MALFORMED_PAYLOAD, e)
        } catch (e: IOException) {
            WriteResult.Failure(WriteResult.Reason.IO_ERROR, e)
        } finally {
            runCatching { formatable.close() }
        }
    }

    companion object {
        private const val TAG = "NfcWriteManager"
    }
}
