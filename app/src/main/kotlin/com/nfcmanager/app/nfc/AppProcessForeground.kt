package com.nfcmanager.app.nfc

import androidx.annotation.MainThread
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Approximates "app process is in foreground" via [androidx.lifecycle.ProcessLifecycleOwner].
 * Android cannot tell which third-party app is on top; this is the standard
 * process-level signal for "at least one of our activities is visible".
 *
 * ## [mainActivityVisible]
 * A stricter flag that is `true` ONLY while [com.nfcmanager.app.MainActivity]
 * (the full nav host) is between `onResume` and `onPause`. Used by
 * [com.nfcmanager.app.NfcDispatchActivity] to decide whether the app was
 * already in the foreground when a system NFC dispatch arrived, or whether
 * the dispatch would hijack focus from another app.
 */
object AppProcessForeground {
    private val _inForeground = AtomicBoolean(false)
    private val _mainActivityVisible = AtomicBoolean(false)
    /** True between [MainActivity] `onStart` and `onStop` (not destroyed). */
    private val _mainActivityAtLeastStarted = AtomicBoolean(false)

    @MainThread
    fun setInForeground(value: Boolean) {
        _inForeground.set(value)
    }

    fun isInForeground(): Boolean = _inForeground.get()

    /**
     * Called from [com.nfcmanager.app.MainActivity] lifecycle callbacks.
     */
    @MainThread
    fun setMainActivityVisible(value: Boolean) {
        _mainActivityVisible.set(value)
    }

    /**
     * `true` when [MainActivity] is resumed (user is looking at the full app).
     * `false` when the app is backgrounded or only [NfcDispatchActivity] is on
     * screen.
     */
    fun isMainActivityVisible(): Boolean = _mainActivityVisible.get()

    @MainThread
    fun setMainActivityAtLeastStarted(value: Boolean) {
        _mainActivityAtLeastStarted.set(value)
    }

    /**
     * `true` while [MainActivity] exists between `onStart` and `onStop`.
     * Used by [com.nfcmanager.app.NfcDispatchActivity] gating: when another
     * task (dispatch) pauses Main, [isMainActivityVisible] is already false,
     * but the main shell is still alive — combined with [NfcMainReaderTabGate]
     * this enforces Home-only scanning while the user stays inside the app.
     */
    fun isMainActivityAtLeastStarted(): Boolean = _mainActivityAtLeastStarted.get()
}
