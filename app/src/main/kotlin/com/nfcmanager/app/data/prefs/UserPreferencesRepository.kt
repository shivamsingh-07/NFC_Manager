package com.nfcmanager.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val DEBOUNCE = longPreferencesKey("debounce_millis")
        val THEME = stringPreferencesKey("theme_mode")
        val BACKGROUND_SCANNING = booleanPreferencesKey("background_scanning_enabled")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            debounceMillis = (prefs[Keys.DEBOUNCE] ?: 2500L).coerceIn(500L, 5000L),
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            backgroundScanningEnabled = prefs[Keys.BACKGROUND_SCANNING] ?: true,
        )
    }

    suspend fun setDebounceMillis(value: Long) {
        context.dataStore.edit { it[Keys.DEBOUNCE] = value.coerceIn(500L, 5000L) }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = value.name }
    }

    suspend fun setBackgroundScanningEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_SCANNING] = value }
    }
}
