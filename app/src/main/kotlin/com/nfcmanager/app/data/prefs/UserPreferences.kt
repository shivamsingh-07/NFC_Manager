package com.nfcmanager.app.data.prefs

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * User-visible preferences persisted in DataStore.
 */
data class UserPreferences(
    val debounceMillis: Long = 2_500L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * When false, NFC events received while the app is NOT in the foreground
     * (system NFC dispatch via [com.nfcmanager.app.NfcDispatchActivity]) are
     * ignored completely. No actions execute, no popup appears, the app is NOT
     * brought to the front.
     */
    val backgroundScanningEnabled: Boolean = true,
)
