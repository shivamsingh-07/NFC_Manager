package com.nfcmanager.app.nfc

import com.nfcmanager.app.presentation.navigation.AppScreen
import com.nfcmanager.app.presentation.navigation.TopDestination
import com.nfcmanager.app.presentation.navigation.toAppScreen
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the active tab / [AppScreen], consumed by
 * [NfcTagEventHandler] and [com.nfcmanager.app.MainActivity] to gate Reader
 * Mode to **Home only** (no exceptions).
 */
@Singleton
class NfcMainReaderTabGate @Inject constructor() {

    private val _currentTab = MutableStateFlow<TopDestination?>(TopDestination.Home)
    val currentTab: StateFlow<TopDestination?> = _currentTab.asStateFlow()

    fun setCurrentTab(tab: TopDestination?) {
        _currentTab.value = tab
    }

    fun currentAppScreen(): AppScreen = _currentTab.value.toAppScreen()

    /** Strict rule: Reader Mode only on the Home tab. */
    fun isHomeTab(): Boolean = _currentTab.value == TopDestination.Home

    fun shouldEnableScanning(): Boolean = isHomeTab()
}
