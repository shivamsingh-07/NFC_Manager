package com.nfcmanager.app.presentation.theme

import androidx.compose.runtime.compositionLocalOf

val LocalAppColors = compositionLocalOf<AppColors> {
    error("LocalAppColors not provided — wrap UI in AppTheme / NfcManagerTheme { }")
}
