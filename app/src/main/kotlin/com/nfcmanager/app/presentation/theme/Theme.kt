package com.nfcmanager.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.data.prefs.ThemeMode

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Expressive CTA geometry — within the 16–24dp band requested for buttons.
 */
val ButtonShape = RoundedCornerShape(20.dp)

/**
 * Root theme: fixed **Light** / **Dark** brand palettes + **System** (OS night mode).
 * Colours for UI must come from [LocalAppColors]; [MaterialTheme.colorScheme] is a
 * static mirror of the same tokens for Material internals (ripples, defaults only).
 */
@Composable
fun NfcManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val appColors = if (darkTheme) AppColors.Dark else AppColors.Light

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = appColors.asMaterial3ColorScheme(darkTheme),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Public alias for app shell — same implementation as [NfcManagerTheme]. */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    NfcManagerTheme(themeMode = themeMode, content = content)
}
