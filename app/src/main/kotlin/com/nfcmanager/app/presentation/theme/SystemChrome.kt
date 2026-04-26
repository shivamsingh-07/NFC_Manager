package com.nfcmanager.app.presentation.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Edge-to-edge: transparent status + navigation bars. Icon appearance follows
 * [LocalAppColors.background] luminance (not Material dynamic colour).
 */
@Composable
fun ImmersiveSystemBars() {
    val canvas = LocalAppColors.current.background
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val lightBackdrop = canvas.luminance() > 0.45f
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackdrop
            isAppearanceLightNavigationBars = lightBackdrop
        }
    }
}
