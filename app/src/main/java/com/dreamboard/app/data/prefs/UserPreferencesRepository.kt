package com.dreamboard.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemePreference(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

data class UserPreferences(
    val privacyMode: Boolean = false,
    val abbreviateAmounts: Boolean = false,
    /** Blocks screenshots and hides content in the recent-apps switcher. On by default. */
    val screenshotProtection: Boolean = true,
    val theme: ThemePreference = ThemePreference.DARK,
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                privacyMode = p[PRIVACY] ?: false,
                abbreviateAmounts = p[ABBREVIATE] ?: false,
                screenshotProtection = p[SCREENSHOT] ?: true,
                theme = p[THEME]?.let { name -> ThemePreference.entries.firstOrNull { it.name == name } }
                    ?: ThemePreference.DARK,
            )
        }

    suspend fun setPrivacyMode(enabled: Boolean) { dataStore.edit { it[PRIVACY] = enabled } }
    suspend fun setAbbreviateAmounts(enabled: Boolean) { dataStore.edit { it[ABBREVIATE] = enabled } }
    suspend fun setScreenshotProtection(enabled: Boolean) { dataStore.edit { it[SCREENSHOT] = enabled } }
    suspend fun setTheme(theme: ThemePreference) { dataStore.edit { it[THEME] = theme.name } }

    private companion object {
        val PRIVACY = booleanPreferencesKey("privacy_mode")
        val ABBREVIATE = booleanPreferencesKey("abbreviate_amounts")
        val SCREENSHOT = booleanPreferencesKey("screenshot_protection")
        val THEME = stringPreferencesKey("theme")
    }
}
