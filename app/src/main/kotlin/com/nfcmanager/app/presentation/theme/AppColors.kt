package com.nfcmanager.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * NFC Manager branded palette — **single source of truth** for all UI colour.
 * Light / dark / system mode resolve to one of the two fixed instances below;
 * there is **no** Material You / Monet / wallpaper extraction.
 *
 * UI code should read colours via [LocalAppColors] — not [androidx.compose.material3.MaterialTheme.colorScheme].
 * A derived [ColorScheme] is still supplied to [androidx.compose.material3.MaterialTheme] so Material widgets
 * that consult the scheme internally (ripples, defaults) stay on-brand.
 */
data class AppColors(
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Chips / small emphasis surfaces */
    val accentContainer: Color,
    val onAccentContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
    val outline: Color,
    val outlineVariant: Color,
    val navBarSurface: Color,
    val navBarSelectedContainer: Color,
    val navBarSelectedContent: Color,
    val navBarUnselectedContent: Color,
    val scrim: Color,
    /** Home / hero vertical gradient (top → bottom); middle uses [background]. */
    val gradientStart: Color,
    val gradientEnd: Color,
) {
    fun asMaterial3ColorScheme(isDark: Boolean): ColorScheme = if (isDark) {
            darkColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = accentContainer,
                onPrimaryContainer = onAccentContainer,
                secondary = textSecondary,
                onSecondary = background,
                secondaryContainer = elevatedSurface,
                onSecondaryContainer = textPrimary,
                tertiary = elevatedSurface,
                onTertiary = textPrimary,
                tertiaryContainer = surface,
                onTertiaryContainer = textPrimary,
                background = background,
                onBackground = textPrimary,
                surface = surface,
                onSurface = textPrimary,
                surfaceVariant = elevatedSurface,
                onSurfaceVariant = textSecondary,
                surfaceContainerLowest = background,
                surfaceContainerLow = background,
                surfaceContainer = surface,
                surfaceContainerHigh = elevatedSurface,
                surfaceContainerHighest = elevatedSurface,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
            )
        } else {
            lightColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = accentContainer,
                onPrimaryContainer = onAccentContainer,
                secondary = textSecondary,
                onSecondary = surface,
                secondaryContainer = elevatedSurface,
                onSecondaryContainer = textPrimary,
                tertiary = elevatedSurface,
                onTertiary = textPrimary,
                tertiaryContainer = surface,
                onTertiaryContainer = textPrimary,
                background = background,
                onBackground = textPrimary,
                surface = surface,
                onSurface = textPrimary,
                surfaceVariant = elevatedSurface,
                onSurfaceVariant = textSecondary,
                surfaceContainerLowest = surface,
                surfaceContainerLow = elevatedSurface,
                surfaceContainer = surface,
                surfaceContainerHigh = elevatedSurface,
                surfaceContainerHighest = elevatedSurface,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
            )
        }

    companion object {
        val Light = AppColors(
            background = Color(0xFFEFF2F6),
            surface = Color(0xFFD1DBE6),
            elevatedSurface = Color(0xFFE2E8EF),
            accent = Color(0xFF2A3B4C),
            onAccent = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF19242E),
            textSecondary = Color(0xFF5C6B7A),
            accentContainer = Color(0xFFC5D0DC),
            onAccentContainer = Color(0xFF19242E),
            error = Color(0xFFC0392B),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFE5E3),
            onErrorContainer = Color(0xFF5C1810),
            success = Color(0xFF188038),
            outline = Color(0xFFAAB8C6),
            outlineVariant = Color(0xFFD1DBE6),
            navBarSurface = Color(0xFFD1DBE6),
            navBarSelectedContainer = Color(0xFF2A3B4C),
            navBarSelectedContent = Color(0xFFFFFFFF),
            navBarUnselectedContent = Color(0xFF5C6B7A),
            scrim = Color(0xCC19242E),
            gradientStart = Color(0xFFF5F7FA),
            gradientEnd = Color(0xFFE5E9F0),
        )

        val Dark = AppColors(
            background = Color(0xFF19242E),
            surface = Color(0xFF2A3B4C),
            elevatedSurface = Color(0xFF33475A),
            accent = Color(0xFFFFFFFF),
            onAccent = Color(0xFF19242E),
            textPrimary = Color(0xFFF0F4F8),
            textSecondary = Color(0xFF9CA8B5),
            accentContainer = Color(0xFF243342),
            onAccentContainer = Color(0xFFE8EEF4),
            error = Color(0xFFFF8A80),
            onError = Color(0xFF3D0A0A),
            errorContainer = Color(0xFF4A2222),
            onErrorContainer = Color(0xFFFFE8E6),
            success = Color(0xFF81C784),
            outline = Color(0xFF5A6B7C),
            outlineVariant = Color(0xFF354555),
            navBarSurface = Color(0xFF222E38),
            navBarSelectedContainer = Color(0xFFFFFFFF),
            navBarSelectedContent = Color(0xFF19242E),
            navBarUnselectedContent = Color(0xFF8B98A8),
            scrim = Color(0xE6000000),
            gradientStart = Color(0xFF1F2C38),
            gradientEnd = Color(0xFF141C24),
        )
    }
}
