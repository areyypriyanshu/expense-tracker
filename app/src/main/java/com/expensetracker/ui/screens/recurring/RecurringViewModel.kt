package com.expensetracker.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.data.model.RecurringFrequency
import com.expensetracker.data.model.RecurringRule
import com.expensetracker.data.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecurringUiState(
    val recurringRules: List<RecurringRule> = emptyList(),
    val baseCurrency: String = "USD",
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false
)

class RecurringViewModel(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _uiState.asStateFlow()

    init {
        loadRecurringRules()
    }

    private fun loadRecurringRules() {
        viewModelScope.launch {
            combine(
                recurringRuleRepository.getAllRecurringRules(),
                preferencesManager.userPreferences
            ) { rules, preferences ->
                RecurringUiState(
                    recurringRules = rules,
                    baseCurrency = preferences.baseCurrency,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun addRule(amount: Double, currency: String, category: String, note: String, frequency: RecurringFrequency) {
        val sanitizedCategory = category.take(50).filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
        val sanitizedNote = note.take(200).replace(Regex("[<>\"'&;\\p{C}]"), "")
        val sanitizedCurrency = currency.take(5).filter { it.isLetter() }.uppercase()
        val validatedAmount = amount.coerceIn(0.01, 1_000_000_000.0)
        
        if (sanitizedCategory.isBlank() || validatedAmount <= 0) {
            return
        }
        
        viewModelScope.launch {
            val rule = RecurringRule(
                amount = validatedAmount,
                currency = sanitizedCurrency.ifBlank { "INR" },
                category = sanitizedCategory,
                note = sanitizedNote,
                frequency = frequency,
                nextDate = java.time.LocalDateTime.now().plusDays(getDaysForFrequency(frequency))
            )
            recurringRuleRepository.insertRecurringRule(rule)
            hideAddDialog()
        }
    }

    fun toggleRule(rule: RecurringRule) {
        viewModelScope.launch {
            recurringRuleRepository.updateRecurringRule(rule.copy(isActive = !rule.isActive))
        }
    }

    fun deleteRule(rule: RecurringRule) {
        viewModelScope.launch {
            recurringRuleRepository.deleteRecurringRule(rule)
        }
    }

    private fun getDaysForFrequency(frequency: RecurringFrequency): Long {
        return when (frequency) {
            RecurringFrequency.DAILY -> 1
            RecurringFrequency.WEEKLY -> 7
            RecurringFrequency.MONTHLY -> 30
            RecurringFrequency.YEARLY -> 365
        }
    }

    class Factory(private val database: ExpenseDatabase, private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecurringViewModel(
                RecurringRuleRepository(database.recurringRuleDao()),
                preferencesManager
            ) as T
        }
    }
}
