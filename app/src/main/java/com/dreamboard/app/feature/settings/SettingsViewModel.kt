package com.dreamboard.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.data.prefs.ThemePreference
import com.dreamboard.app.data.prefs.UserPreferences
import com.dreamboard.app.data.prefs.UserPreferencesRepository
import com.dreamboard.app.domain.model.ImportBatch
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val repo: FinanceRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())
    val importBatches: StateFlow<List<ImportBatch>> = repo.importBatches

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setPrivacy(v: Boolean) = viewModelScope.launch { prefs.setPrivacyMode(v) }
    fun setAbbreviate(v: Boolean) = viewModelScope.launch { prefs.setAbbreviateAmounts(v) }
    fun setScreenshotProtection(v: Boolean) = viewModelScope.launch { prefs.setScreenshotProtection(v) }
    fun setTheme(t: ThemePreference) = viewModelScope.launch { prefs.setTheme(t) }

    fun loadExampleDebts() = viewModelScope.launch {
        val had = repo.debts.value.isNotEmpty()
        repo.loadExampleDebts()
        _message.value = if (had) "You already have debts, so no examples were added." else "Example debts added. Open Debt to edit each one."
    }

    fun eraseAll() = viewModelScope.launch {
        repo.eraseAllData()
        _message.value = "All data erased. Default categories and accounts were restored."
    }
}
