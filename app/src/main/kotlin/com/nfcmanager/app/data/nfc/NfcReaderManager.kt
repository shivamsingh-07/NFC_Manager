package com.nfcmanager.app.data.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.presentation.util.HapticFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns Reader Mode enable/disable and surfaces discovered tags via a flow.
 *
 * Reader Mode (API 19+) is the preferred way to read tags while the app is in
 * the foreground. It gives us:
 *   - predictable flag-driven tech filtering,
 *   - zero-intent dispatch (no LAUNCH on tag discovery),
 *   - the ability to disable the system "Beam"/sounds for a silent UX.
 *
 * The caller is responsible for binding/unbinding to an [Activity] lifecycle.
 */
@Singleton
class NfcReaderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ndefParser: NdefParser,
    private val debouncer: TagDebouncer,
    private val haptics: HapticFeedback,
    private val type4NdefReader: Type4NdefReader,
) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    private val _state = MutableStateFlow(State.Idle as State)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Most recently parsed tag (Reader Mode or system NFC dispatch). Exposed so
     * UI can show the latest tap without relying on SharedFlow replay (we keep
     * [tags] replay at 0 to avoid duplicate side-effects when lifecycle
     * collectors restart).
     */
    private val _lastTag = MutableStateFlow<NfcTag?>(null)
    val lastTag: StateFlow<NfcTag?> = _lastTag.asStateFlow()

    /**
     * While `true`, [enable] is a no-op so lifecycle `onResume` cannot replace
     * the Reader Mode session owned by [NfcTagCaptureManager] during "Create
     * action" tag enrollment.
     */
    private val tagCaptureInProgress = AtomicBoolean(false)

    /**
     * Identity hash of the activity Reader Mode is currently bound to, or
     * 0 if not bound. Used to skip redundant `enableReaderMode` calls
     * triggered by Compose recompositions — every redundant call cancels
     * and re-arms the polling loop, opening a microsecond-scale gap that
     * the system dispatcher (chooser) can slip into.
     */
    @Volatile private var boundActivityId: Int = 0

    fun setTagCaptureInProgress(inProgress: Boolean) {
        tagCaptureInProgress.set(inProgress)
    }

    fun isTagCaptureInProgress(): Boolean = tagCaptureInProgress.get()

    /**
     * Hot stream of parsed tags. No replay — consumers that need the latest tag
     * should read [lastTag].
     */
    private val _tags = MutableSharedFlow<NfcTag>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val tags: Flow<NfcTag> = _tags.asSharedFlow()

    val hardwareStatus: HardwareStatus
        get() = when {
            adapter == null -> HardwareStatus.Unsupported
            !adapter.isEnabled -> HardwareStatus.Disabled
            else -> HardwareStatus.Enabled
        }

    fun enable(activity: Activity) {
        if (tagCaptureInProgress.get()) return
        val nfc = adapter ?: run {
            _state.value = State.Unsupported
            return
        }
        if (!nfc.isEnabled) {
            _state.value = State.Disabled
            return
        }
        val id = System.identityHashCode(activity)
        if (boundActivityId == id) {
            // Already polling on behalf of this activity — skip. Every
            // redundant enableReaderMode call tears down + re-arms the
            // polling loop and opens a tiny window for the system NFC
            // dispatcher to handle a tap (and show a chooser).
            return
        }
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MS)
        }
        nfc.enableReaderMode(activity, ::onTagDiscovered, READER_FLAGS, options)
        boundActivityId = id
        _state.value = State.Scanning
        Log.d(TAG, "Reader Mode enabled (activity=$id)")
    }

    fun disable(activity: Activity) {
        val id = System.identityHashCode(activity)
        if (boundActivityId != id && boundActivityId != 0) {
            // We're bound to a different activity (rotation, etc.). Still
            // call disable to be defensive; the system tolerates it.
            adapter?.disableReaderMode(activity)
            return
        }
        adapter?.disableReaderMode(activity)
        if (boundActivityId == id) {
            boundActivityId = 0
            Log.d(TAG, "Reader Mode disabled (activity=$id)")
        }
        if (_state.value is State.Scanning) _state.value = State.Idle
    }

    /**
     * Handles tags delivered by the system NFC dispatch (i.e. when the app was
     * backgrounded or killed and the user tapped a tag). This path is
     * complementary to Reader Mode:
     *
     *   - Reader Mode wins while the Activity is resumed (zero system UI).
     *   - The system dispatch wins when the Activity isn't — it cold-starts /
     *     resurfaces the app and hands us the [Tag] via the Intent extras.
     *
     * Suspending because if the system did not pre-populate `EXTRA_NDEF_MESSAGES`
     * (cached read miss, or `TAG_DISCOVERED`-only tag) we have to `connect()`
     * to read NDEF, which is forbidden on the main thread. The whole call is
     * wrapped in [Dispatchers.IO] + a hard timeout so a stuck tag can't hang
     * the dispatch activity's `onCreate`.
     *
     * Updates [lastTag] and returns the parsed tag. Does **not** emit to
     * [tags] — the Activity calls [com.nfcmanager.app.nfc.NfcTagEventHandler]
     * directly so side-effects are not lost to collector timing and are not
     * duplicated when [tags] is also collected.
     *
     * Returns `null` if the intent was not an NFC dispatch, the tag extra was
     * missing, debounce dropped the read, parsing failed, or the read timed out.
     */
    suspend fun processDispatchedIntent(intent: Intent, source: TagSource = TagSource.Dispatch): NfcTag? {
        if (!isNfcDispatch(intent.action)) return null
        if (tagCaptureInProgress.get()) return null
        val tag: Tag = extractTag(intent) ?: return null
        val prefetched = extractNdefMessages(intent)
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(DISPATCH_READ_TIMEOUT_MS) {
                parseTag(tag, prefetched, emitToTagStream = false, source = source)
            }
        }
    }

    private fun onTagDiscovered(tag: Tag, prefetchedMessages: List<NdefMessage> = emptyList()) {
        parseTag(tag, prefetchedMessages, emitToTagStream = true, source = TagSource.ReaderMode)
    }

    private fun parseTag(
        tag: Tag,
        prefetchedMessages: List<NdefMessage>,
        emitToTagStream: Boolean,
        source: TagSource,
    ): NfcTag? {
        return try {
            if (tagCaptureInProgress.get()) return null
            val uid = tag.id.toHex()
            if (!debouncer.shouldProcess(uid)) return null

            val techs = tag.techList.orEmpty().toList()
            Log.d(TAG, "parseTag source=$source uid=$uid techs=$techs prefetched=${prefetchedMessages.size}")
            val ndef = Ndef.get(tag)
            var messages: List<NdefMessage>
            val maxSize: Int
            val writable: Boolean
            val canMakeReadOnly: Boolean

            if (ndef != null) {
                // Prefer messages the system dispatched alongside the tag (zero I/O),
                // then the cached message (Reader Mode eagerly populates this),
                // finally fall back to a live connect-and-read.
                val cached = ndef.cachedNdefMessage
                messages = when {
                    prefetchedMessages.isNotEmpty() -> prefetchedMessages
                    cached != null -> listOf(cached)
                    else -> runCatching {
                        ndef.connect()
                        val live = ndef.ndefMessage
                        if (live != null) listOf(live) else emptyList()
                    }.getOrDefault(emptyList()).also {
                        runCatching { ndef.close() }
                    }
                }
                maxSize = ndef.maxSize
                writable = ndef.isWritable
                canMakeReadOnly = ndef.canMakeReadOnly()
            } else {
                messages = prefetchedMessages
                val formatable = NdefFormatable.get(tag)
                maxSize = 0
                writable = formatable != null
                canMakeReadOnly = false
            }

            // HCE-peer fallback. Many OEM NFC stacks won't run the Type 4
            // NDEF probe against another phone presenting as IsoDep, so the
            // system delivers an IsoDep-only tag and `Ndef.get(tag)` is null.
            // Run the protocol ourselves over IsoDep before declaring the
            // tag empty. If the manual read also fails we fall through and
            // build an NfcTag with empty payloads — surfacing the "Empty
            // Tag" sheet is still better UX than dropping the tap on the
            // floor with zero feedback (and the FGD-era multi-discovery
            // race that justified suppressing this can't happen anymore:
            // Reader Mode is disabled the moment the sheet opens, and
            // NfcDispatchActivity self-finishes when the sheet is already
            // up, so parseTag is called at most once per physical hold).
            if (messages.isEmpty() && techs.contains(ISO_DEP_TECH)) {
                Log.d(TAG, "parseTag: Ndef path empty, attempting manual Type 4 read over IsoDep")
                val manual = type4NdefReader.read(tag)
                if (manual != null) {
                    Log.d(TAG, "parseTag: manual Type 4 read succeeded, ${manual.toByteArray().size} bytes")
                    messages = listOf(manual)
                } else {
                    Log.d(TAG, "parseTag: manual Type 4 read failed on IsoDep peer (uid=$uid source=$source); falling through with empty payloads")
                }
            }

            val payloads = ndefParser.parse(messages)
            val parsedTag = NfcTag(
                uid = uid,
                technologies = techs,
                maxSize = maxSize,
                isWritable = writable,
                canMakeReadOnly = canMakeReadOnly,
                payloads = payloads,
                discoveredAtEpochMillis = System.currentTimeMillis(),
            )
            _lastTag.value = parsedTag
            if (emitToTagStream) {
                _tags.tryEmit(parsedTag)
            }
            parsedTag
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse tag", t)
            null
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { "%02X".format(it.toInt() and 0xFF) }

    private fun isNfcDispatch(action: String?): Boolean = action in NFC_DISPATCH_ACTIONS

    @Suppress("DEPRECATION")
    private fun extractTag(intent: Intent): Tag? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as Tag?
        }

    @Suppress("DEPRECATION")
    private fun extractNdefMessages(intent: Intent): List<NdefMessage> {
        val rawArray: Array<Parcelable>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(
                    NfcAdapter.EXTRA_NDEF_MESSAGES,
                    Parcelable::class.java,
                )
            } else {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            }
        return rawArray.orEmpty().mapNotNull { it as? NdefMessage }
    }

    enum class HardwareStatus { Unsupported, Disabled, Enabled }

    sealed interface State {
        data object Idle : State
        data object Scanning : State
        data object Disabled : State
        data object Unsupported : State
    }

    /**
     * Tells [parseTag]/log readers which dispatch path delivered the tag.
     *
     *   - [ReaderMode] → we won the routing race, no chooser was shown.
     *   - [ManifestDispatch] → the OS dispatcher routed to us via the
     *     manifest filters; if multiple candidates existed the user picked
     *     us from the OS "Open with…" chooser.
     *   - [Dispatch] → generic, used when the caller doesn't know.
     */
    enum class TagSource { ReaderMode, ManifestDispatch, Dispatch }

    companion object {
        private const val TAG = "NfcReaderManager"
        private const val PRESENCE_CHECK_DELAY_MS = 250
        // Bumped from 1.5s — manual Type 4 read over IsoDep can need
        // ~1s on its own when the peer is another phone.
        private const val DISPATCH_READ_TIMEOUT_MS = 4_000L
        private const val ISO_DEP_TECH = "android.nfc.tech.IsoDep"

        private val NFC_DISPATCH_ACTIONS = setOf(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
        )

        /**
         * Poll the four ISO/IEC 14443 / FeliCa / V tech families. Phone-to-phone
         * peers expose themselves over NfcA + ISO-DEP (HCE), so A/B/F/V is the
         * superset we need. `FLAG_READER_NFC_BARCODE` is intentionally not
         * included — it's only meaningful for thermometer / NFC barcode tags
         * that no one in this app's flow touches.
         *
         * `FLAG_READER_NO_PLATFORM_SOUNDS` suppresses the OS tap sound so our
         * own UI (sheet + haptic) is the single feedback channel.
         */
        const val READER_FLAGS: Int =
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
