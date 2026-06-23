package com.expensetracker.ui.screens.transaction

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.model.Category
import com.expensetracker.data.model.RecurringFrequency
import com.expensetracker.data.model.RecurringRule
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.RecurringRuleRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.engine.CategoryEngine
import com.expensetracker.domain.model.MonetaryUtil
import com.expensetracker.domain.model.UiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class AddTransactionUiState(
    val id: Long? = null,
    val amount: String = "",
    val currency: String = "INR",
    val category: String = "Other",
    val note: String = "",
    val date: LocalDateTime = LocalDateTime.now(),
    val categories: List<Category> = emptyList(),
    val isRecurring: Boolean = false,
    val recurringFrequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val isIncome: Boolean = false,
    val uiState: UiState<Unit> = UiState.Success(Unit),
    val error: String? = null
) {
    val isLoading: Boolean get() = uiState is UiState.Loading
    val isSaved: Boolean get() = uiState is UiState.Success && error == null
}

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringRuleRepository: RecurringRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private val categoryEngine = CategoryEngine()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(uiState = UiState.Loading, error = null) }
            try {
                val transaction = transactionRepository.getTransactionById(transactionId)
                if (transaction != null) {
                    _uiState.update {
                        it.copy(
                            id = transaction.id,
                            amount = transaction.amount.toString(),
                            currency = transaction.currency,
                            category = transaction.category,
                            note = transaction.note,
                            date = transaction.date,
                            isIncome = transaction.isIncome,
                            uiState = UiState.Success(Unit)
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            uiState = UiState.Error("Transaction not found"),
                            error = "Transaction not found"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load transaction")
                _uiState.update { 
                    it.copy(
                        uiState = UiState.Error("Failed to load transaction"),
                        error = "Failed to load transaction"
                    )
                }
            }
        }
    }

    fun onAmountChange(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            val parts = filtered.split('.')
            val limited = if (parts.size > 1) {
                "${parts[0]}.${parts[1].take(2)}"
            } else {
                filtered
            }
            
            if (limited.length > 15) {
                return  // Prevent buffer overflow from extremely long input
            }
            
            val value = limited.toDoubleOrNull()
            if (value != null && value > MAX_SAFE_AMOUNT) {
                return  // Prevent scientific notation overflow
            }
            
            _uiState.update { it.copy(amount = limited, error = null) }
        }
    }

    fun onCurrencyChange(currency: String) {
        val sanitizedCurrency = currency
            .take(5)
            .filter { it.isLetter() }
            .uppercase()
            .take(5)
        
        if (sanitizedCurrency.length in 3..5) {
            _uiState.update { it.copy(currency = sanitizedCurrency) }
        }
    }

    fun onCategoryChange(category: String) {
        val sanitizedCategory = category
            .take(50)
            .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '&' }
            .trim()
        
        // Validate against loaded categories to prevent mismatch with repository whitelist
        val validCategories = _uiState.value.categories.map { it.name }
        val resolvedCategory = if (validCategories.isNotEmpty() && sanitizedCategory !in validCategories) {
            validCategories.find { it.equals(sanitizedCategory, ignoreCase = true) } ?: sanitizedCategory
        } else {
            sanitizedCategory
        }
        
        if (resolvedCategory.isNotBlank() && resolvedCategory.length <= 50) {
            _uiState.update { it.copy(category = resolvedCategory) }
        }
    }

    fun onNoteChange(note: String) {
        val sanitizedNote = sanitizeNote(note)
        _uiState.update { it.copy(note = sanitizedNote) }
    }
    
    private fun sanitizeNote(input: String): String {
        if (input.isBlank()) return ""
        
        return input
            .take(500)
            .replace(Regex("[\t\r\n]"), " ")
            .replace(Regex("<[^>]*>"), "")  // Remove HTML tags
            .replace(Regex("javascript:|on\\w+=", setOf(RegexOption.IGNORE_CASE)), "")  // Remove JS event handlers
            .replace(Regex("[<>\"'&]"), "")  // Remove dangerous characters
            .replace(Regex("\\p{C}"), "")  // Remove control characters
            .trim()
            .take(500)
    }

    fun onDateChange(date: LocalDateTime) {
        val maxDate = LocalDateTime.now().plusDays(1) // Allow today + 1 day for timezone edge
        val minDate = LocalDateTime.now().minusYears(2) // Reasonable historical limit
        val bounded = when {
            date.isAfter(maxDate) -> maxDate
            date.isBefore(minDate) -> minDate
            else -> date
        }
        _uiState.update { it.copy(date = bounded) }
    }

    fun onRecurringChange(isRecurring: Boolean) {
        _uiState.update { it.copy(isRecurring = isRecurring) }
    }

    fun onRecurringFrequencyChange(frequency: RecurringFrequency) {
        _uiState.update { it.copy(recurringFrequency = frequency) }
    }

    fun onIsIncomeChange(isIncome: Boolean) {
        val currentState = _uiState.value
        val newCategory = if (isIncome && !currentState.isIncome) {
            "Income"
        } else if (!isIncome && currentState.isIncome && currentState.category == "Income") {
            "Other"
        } else {
            currentState.category
        }
        _uiState.update { it.copy(isIncome = isIncome, category = newCategory) }
    }

    fun autoCategorize() {
        val note = _uiState.value.note
        val categories = _uiState.value.categories
        val suggestedCategory = categoryEngine.autoCategorize(note, categories)
        _uiState.update { it.copy(category = suggestedCategory) }
    }

    fun saveTransaction(onSuccess: () -> Unit) {
        val state = _uiState.value
        val amountValue = state.amount.toDoubleOrNull()

        when {
            state.amount.isBlank() -> {
                _uiState.update { it.copy(error = "Please enter an amount") }
                return
            }
            amountValue == null -> {
                _uiState.update { it.copy(error = "Invalid amount format") }
                return
            }
            amountValue <= 0 -> {
                _uiState.update { it.copy(error = "Amount must be greater than zero") }
                return
            }
            amountValue < MIN_AMOUNT -> {
                _uiState.update { it.copy(error = "Minimum amount is ${MIN_AMOUNT}") }
                return
            }
            amountValue > MAX_AMOUNT -> {
                _uiState.update { it.copy(error = "Maximum amount is ${String.format("%,.0f", MAX_AMOUNT)}") }
                return
            }
            !MonetaryUtil.isValidAmount(amountValue) -> {
                _uiState.update { it.copy(error = "Amount is too large") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(uiState = UiState.Loading, error = null) }

            try {
                val transaction = Transaction(
                    id = state.id ?: 0,
                    amount = amountValue!!,
                    currency = state.currency,
                    category = state.category,
                    note = state.note,
                    date = state.date,
                    isRecurring = state.isRecurring,
                    isIncome = state.isIncome
                )

                if (state.id != null && state.id > 0) {
                    transactionRepository.updateTransaction(transaction)
                } else {
                    transactionRepository.insertTransaction(transaction)
                }

                if (state.isRecurring && state.id == null) {
                    val recurringRule = RecurringRule(
                        amount = amountValue!!,
                        currency = state.currency,
                        category = state.category,
                        note = state.note,
                        frequency = state.recurringFrequency,
                        nextDate = state.date.plusDays(getDaysForFrequency(state.recurringFrequency))
                    )
                    recurringRuleRepository.insertRecurringRule(recurringRule)
                }

                _uiState.update { it.copy(uiState = UiState.Success(Unit)) }
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transaction")
                _uiState.update { 
                    it.copy(
                        uiState = UiState.Error("Failed to save transaction"),
                        error = "Failed to save. Please try again."
                    )
                }
            }
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

    class Factory(private val database: ExpenseDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddTransactionViewModel(
                TransactionRepository(database.transactionDao()),
                CategoryRepository(database.categoryDao()),
                RecurringRuleRepository(database.recurringRuleDao())
            ) as T
        }
    }

    companion object {
        private const val TAG = "AddTransactionVM"
        private const val MIN_AMOUNT = 0.01
        private const val MAX_AMOUNT = 1000000.0
        private const val MAX_SAFE_AMOUNT = 1e14  // Prevent scientific notation overflow
    }
}
