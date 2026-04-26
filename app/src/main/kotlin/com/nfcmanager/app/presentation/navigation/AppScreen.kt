package com.nfcmanager.app.presentation.navigation

/**
 * Primary app surfaces for NFC policy. Sub-routes (create action) map to
 * [OTHER] — Reader Mode is never enabled there.
 */
enum class AppScreen {
    HOME,
    MESSAGE,
    ACTIONS,
    SETTINGS,
    /** Create-action flow or unknown routes. */
    OTHER,
}

fun TopDestination?.toAppScreen(): AppScreen = when (this) {
    TopDestination.Home -> AppScreen.HOME
    TopDestination.Message -> AppScreen.MESSAGE
    TopDestination.Actions -> AppScreen.ACTIONS
    TopDestination.Settings -> AppScreen.SETTINGS
    null -> AppScreen.OTHER
}
