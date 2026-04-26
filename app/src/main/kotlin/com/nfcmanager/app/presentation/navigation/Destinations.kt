package com.nfcmanager.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Create-action enrollment is logically under the Actions tab for NFC gating. */
const val ROUTE_CREATE_ACTION = "create_action"

enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Rounded.Home),
    Message("message", "Message", Icons.AutoMirrored.Rounded.Message),
    Actions("actions", "Actions", Icons.Rounded.Bolt),
    Settings("settings", "Settings", Icons.Rounded.Settings);

    companion object {
        /** Bottom pill destinations. */
        val BottomNavDestinations: List<TopDestination> = listOf(
            Home,
            Message,
            Actions,
            Settings,
        )

        fun fromRoute(route: String?): TopDestination? = entries.firstOrNull { it.route == route }

        /**
         * Resolves the visible root tab for pill UI + NFC policy.
         *
         * **Critical for Reader Mode gating**: any route that is NOT explicitly
         * mapped here defaults to `null` (unknown), and the caller must treat
         * `null` as "not Home" — Reader Mode must NEVER be enabled on an
         * unrecognised route.
         *
         * Sub-routes:
         *  - `create_action` → [Actions] (enrollment flow)
         *  - anything else → `null` (defensive: treat as non-Home)
         */
        fun fromNavRoute(route: String?): TopDestination? = when {
            route == null -> null
            route == ROUTE_CREATE_ACTION -> Actions
            else -> fromRoute(route)
        }
    }
}
