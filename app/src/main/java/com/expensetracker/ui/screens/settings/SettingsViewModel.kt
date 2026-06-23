package com.expensetracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.services.currency.CurrencyInfo
import com.expensetracker.services.currency.CurrencyService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseCurrency: String = "USD",
    val currencies: List<CurrencyInfo> = CurrencyService.SUPPORTED_CURRENCIES,
    val isLoading: Boolean = true
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            preferencesManager.userPreferences.collect { preferences ->
                _uiState.update {
                    it.copy(
                        baseCurrency = preferences.baseCurrency,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateBaseCurrency(currency: String) {
        val sanitizedCurrency = currency
            .take(5)
            .filter { it.isLetter() }
            .uppercase()
        
        if (sanitizedCurrency.length in 3..5) {
            viewModelScope.launch {
                preferencesManager.updateBaseCurrency(sanitizedCurrency)
            }
        }
    }

    class Factory(private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager) as T
        }
    }
}
