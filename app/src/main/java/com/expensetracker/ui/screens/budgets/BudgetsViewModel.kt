package com.expensetracker.ui.screens.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.data.model.Budget
import com.expensetracker.data.model.BudgetPeriod
import com.expensetracker.data.repository.BudgetRepository
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.engine.BudgetEngine
import com.expensetracker.domain.engine.BudgetStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class BudgetsUiState(
    val budgetStatuses: List<BudgetStatus> = emptyList(),
    val categories: List<String> = emptyList(),
    val baseCurrency: String = "INR",
    val todaySpend: Double = 0.0,
    val weeklySpend: Double = 0.0,
    val monthlySpend: Double = 0.0,
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false
)

class BudgetsViewModel(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetsUiState())
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

    private val budgetEngine = BudgetEngine(budgetRepository, transactionRepository)

    init {
        loadBudgets()
    }

    private fun loadBudgets() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
            val startOfWeek = now.minusDays(now.dayOfWeek.value.toLong() - 1).withHour(0).withMinute(0)
            val startOfToday = now.toLocalDate().atStartOfDay()

            combine(
                budgetRepository.getAllBudgets(),
                categoryRepository.getAllCategories(),
                transactionRepository.getAllTransactions(),
                preferencesManager.userPreferences
            ) { budgets, categories, transactions, preferences ->
                val budgetStatuses = budgets.map { budget ->
                    budgetEngine.getBudgetStatus(budget)
                }

                val monthlyTotal = transactions.filter { it.date >= startOfMonth }.sumOf { it.amount }
                val weeklyTotal = transactions.filter { it.date >= startOfWeek }.sumOf { it.amount }
                val todayTotal = transactions.filter { it.date >= startOfToday }.sumOf { it.amount }

                BudgetsUiState(
                    budgetStatuses = budgetStatuses,
                    categories = categories.map { it.name },
                    baseCurrency = preferences.baseCurrency,
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

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun addBudget(category: String, limit: Double, period: BudgetPeriod, threshold: Float) {
        val sanitizedCategory = category.take(50).filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
        val validatedLimit = limit.coerceIn(0.01, 1_000_000_000.0)
        val validatedThreshold = threshold.coerceIn(0f, 1f)
        
        if (sanitizedCategory.isBlank() || validatedLimit <= 0) {
            return
        }
        
        viewModelScope.launch {
            val budget = Budget(
                category = sanitizedCategory,
                limit = validatedLimit,
                currency = _uiState.value.baseCurrency,
                period = period,
                alertThreshold = validatedThreshold
            )
            budgetRepository.insertBudget(budget)
            hideAddDialog()
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(budget)
        }
    }

    class Factory(private val database: ExpenseDatabase, private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BudgetsViewModel(
                BudgetRepository(database.budgetDao()),
                CategoryRepository(database.categoryDao()),
                TransactionRepository(database.transactionDao()),
                preferencesManager
            ) as T
        }
    }
}
