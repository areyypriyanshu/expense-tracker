package com.expensetracker.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.data.model.Category
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class TransactionListUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val baseCurrency: String = "INR",
    val todaySpend: Double = 0.0,
    val weeklySpend: Double = 0.0,
    val monthlySpend: Double = 0.0,
    val isLoading: Boolean = true
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
            val startOfWeek = now.minusDays(now.dayOfWeek.value.toLong() - 1).withHour(0).withMinute(0)
            val startOfToday = now.toLocalDate().atStartOfDay()

            val debouncedSearchQuery = _searchQuery
                .debounce(300)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

            combine(
                transactionRepository.getAllTransactions(),
                categoryRepository.getAllCategories(),
                debouncedSearchQuery,
                _selectedCategory,
                preferencesManager.userPreferences
            ) { transactions, categories, query, selectedCategory, prefs ->
                val expenses = transactions.filter { !it.isIncome }
                
                val filtered = transactions.filter { tx ->
                    val matchesQuery = query.isEmpty() ||
                            tx.note.contains(query, ignoreCase = true) ||
                            tx.category.contains(query, ignoreCase = true)
                    val matchesCategory = selectedCategory == null || tx.category == selectedCategory
                    matchesQuery && matchesCategory
                }

                val monthlyTotal = expenses.filter { it.date >= startOfMonth }.sumOf { it.amount }
                val weeklyTotal = expenses.filter { it.date >= startOfWeek }.sumOf { it.amount }
                val todayTotal = expenses.filter { it.date >= startOfToday }.sumOf { it.amount }

                TransactionListUiState(
                    transactions = transactions,
                    filteredTransactions = filtered,
                    categories = categories,
                    searchQuery = query,
                    selectedCategory = selectedCategory,
                    baseCurrency = prefs.baseCurrency,
                    todaySpend = todayTotal,
                    weeklySpend = weeklyTotal,
                    monthlySpend = monthlyTotal,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        val sanitizedQuery = query.take(100).replace(Regex("[<>\"'&;\\p{C}]"), "")
        _searchQuery.value = sanitizedQuery
        _uiState.update { it.copy(searchQuery = sanitizedQuery) }
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
        }
    }

    class Factory(
        private val database: ExpenseDatabase,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionsViewModel(
                TransactionRepository(database.transactionDao()),
                CategoryRepository(database.categoryDao()),
                preferencesManager
            ) as T
        }
    }
}
