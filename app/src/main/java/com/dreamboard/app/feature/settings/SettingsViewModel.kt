package com.dreamboard.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.data.prefs.ThemePreference
import com.dreamboard.app.data.prefs.UserPreferences
import com.dreamboard.app.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setPrivacy(v: Boolean) = viewModelScope.launch { prefs.setPrivacyMode(v) }
    fun setAbbreviate(v: Boolean) = viewModelScope.launch { prefs.setAbbreviateAmounts(v) }
    fun setScreenshotProtection(v: Boolean) = viewModelScope.launch { prefs.setScreenshotProtection(v) }
    fun setTheme(t: ThemePreference) = viewModelScope.launch { prefs.setTheme(t) }
}
