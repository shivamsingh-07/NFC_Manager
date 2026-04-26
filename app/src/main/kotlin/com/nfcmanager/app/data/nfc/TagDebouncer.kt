package com.nfcmanager.app.data.nfc

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Swallow duplicate scans of the same UID that happen within [windowMillis].
 *
 * Reader Mode can re-trigger very quickly if a tag is held near the antenna;
 * without debouncing we would emit notifications multiple times per second.
 */
@Singleton
class TagDebouncer @Inject constructor() {
    /**
     * Wall-clock source. Hilt cannot inject a `() -> Long` default parameter
     * (Kapt generates two constructors); tests assign a controllable lambda.
     */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val lastSeen = ConcurrentHashMap<String, Long>()
    private var windowMillis: Long = DEFAULT_WINDOW_MILLIS

    fun setWindow(millis: Long) {
        windowMillis = millis.coerceAtLeast(0L)
    }

    /** @return true when this UID should be processed; false if suppressed. */
    fun shouldProcess(uid: String): Boolean {
        if (uid.isEmpty()) return true // no UID — can't dedupe; let it through
        val now = clock()
        val last = lastSeen[uid]
        return if (last == null || now - last >= windowMillis) {
            lastSeen[uid] = now
            if (lastSeen.size > MAX_ENTRIES) prune(now)
            true
        } else {
            false
        }
    }

    fun reset() = lastSeen.clear()

    private fun prune(now: Long) {
        val threshold = now - windowMillis
        lastSeen.entries.removeAll { it.value < threshold }
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS: Long = 2500
        private const val MAX_ENTRIES = 128
    }
}
