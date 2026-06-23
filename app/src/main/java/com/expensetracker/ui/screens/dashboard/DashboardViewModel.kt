package com.expensetracker.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.data.repository.BudgetRepository
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.engine.BudgetEngine
import com.expensetracker.domain.engine.BudgetStatus
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.services.insights.SpendingInsight
import com.expensetracker.services.insights.SpendingInsightsService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class DashboardUiState(
    val totalBalance: Double = 0.0,
    val monthlySpend: Double = 0.0,
    val weeklySpend: Double = 0.0,
    val todaySpend: Double = 0.0,
    val recentTransactions: List<com.expensetracker.data.model.Transaction> = emptyList(),
    val budgetStatuses: List<BudgetStatus> = emptyList(),
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val baseCurrency: String = "INR",
    val isLoading: Boolean = true,
    val insights: List<SpendingInsight> = emptyList()
)

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    private val insightsService = SpendingInsightsService()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val now = LocalDateTime.now()
            val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
            val startOfWeek = now.minusDays(now.dayOfWeek.value.toLong() - 1).withHour(0).withMinute(0)
            val startOfToday = now.toLocalDate().atStartOfDay()

            val debouncedTransactions = transactionRepository.getAllTransactions()
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

            combine(
                debouncedTransactions,
                budgetRepository.getAllBudgets(),
                preferencesManager.userPreferences
            ) { transactions, budgets, preferences ->
                val expenses = transactions.filter { !it.isIncome }
                val income = transactions.filter { it.isIncome }
                
                val monthlyExpense = expenses
                    .filter { it.date >= startOfMonth }
                    .sumOf { it.amount }

                val weeklyExpense = expenses
                    .filter { it.date >= startOfWeek }
                    .sumOf { it.amount }

                val todayExpense = expenses
                    .filter { it.date >= startOfToday }
                    .sumOf { it.amount }

                val monthlyIncome = income
                    .filter { it.date >= startOfMonth }
                    .sumOf { it.amount }

                val categoryTotals = expenses
                    .filter { it.date >= startOfMonth }
                    .groupBy { it.category }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }

                val recentTx = expenses.take(5)

                val budgetEngine = BudgetEngine(budgetRepository, transactionRepository)
                val budgetStatuses = budgets.map { budget ->
                    budgetEngine.getBudgetStatus(budget)
                }

                val insights = insightsService.generateInsights(expenses)

                DashboardUiState(
                    totalBalance = monthlyIncome - monthlyExpense,
                    monthlySpend = monthlyExpense,
                    weeklySpend = weeklyExpense,
                    todaySpend = todayExpense,
                    recentTransactions = recentTx,
                    budgetStatuses = budgetStatuses,
                    categoryBreakdown = categoryTotals,
                    baseCurrency = preferences.baseCurrency,
                    isLoading = false,
                    insights = insights
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    class Factory(private val database: ExpenseDatabase, private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                TransactionRepository(database.transactionDao()),
                CategoryRepository(database.categoryDao()),
                BudgetRepository(database.budgetDao()),
                preferencesManager
            ) as T
        }
    }
}
