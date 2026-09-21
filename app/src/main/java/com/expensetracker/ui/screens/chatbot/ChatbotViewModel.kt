package com.expensetracker.ui.screens.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.BudgetRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.chatbot.ChatbotIntent
import com.expensetracker.domain.chatbot.IntentParser
import com.expensetracker.domain.engine.BudgetEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ChatbotUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false
)

class ChatbotViewModel(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatbotUiState())
    val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

    private val intentParser = IntentParser()

    init {
        // Add greeting message
        val welcomeMessage = ChatMessage(
            text = "Hello! I am your offline Finance Assistant. Ask me about your spending, monthly totals, budgets, or type 'help' to see what I can do.",
            isUser = false
        )
        _uiState.update { it.copy(messages = listOf(welcomeMessage)) }
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        val userMessage = ChatMessage(text = query, isUser = true)
        _uiState.update { state ->
            state.copy(messages = state.messages + userMessage, isLoading = true)
        }

        viewModelScope.launch {
            val intent = intentParser.parse(query)
            val responseText = executeIntent(intent)

            val botMessage = ChatMessage(text = responseText, isUser = false)
            _uiState.update { state ->
                state.copy(messages = state.messages + botMessage, isLoading = false)
            }
        }
    }

    private suspend fun executeIntent(intent: ChatbotIntent): String {
        val prefs = preferencesManager.userPreferences.first()
        val currencySymbol = prefs.baseCurrency

        val transactions = transactionRepository.getAllTransactions().first()
        val expenses = transactions.filter { !it.isIncome }
        val now = LocalDateTime.now()
        val startOfToday = now.toLocalDate().atStartOfDay()
        val startOfYesterday = now.minusDays(1).toLocalDate().atStartOfDay()
        val endOfYesterday = now.toLocalDate().atStartOfDay()
        val startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay()
        val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)

        return when (intent) {
            is ChatbotIntent.Help -> {
                buildString {
                    append("Here are some things you can ask me:\n\n")
                    append("• \"What is my total spending?\"\n")
                    append("• \"How much did I spend today?\"\n")
                    append("• \"How much did I spend this month?\"\n")
                    append("• \"How much spent on food?\"\n")
                    append("• \"Show my highest expense\"\n")
                    append("• \"Show recent transactions\"\n")
                    append("• \"What is my budget status?\"\n")
                    append("• \"Give me a spending summary\"")
                }
            }

            is ChatbotIntent.GetTotalSpending -> {
                val total = expenses.sumOf { it.amount }
                "Your total recorded expense is $currencySymbol %.2f".format(total)
            }

            is ChatbotIntent.GetTodaySpending -> {
                val total = expenses
                    .filter { it.date >= startOfToday }
                    .sumOf { it.amount }
                "Your spending today is $currencySymbol %.2f".format(total)
            }

            is ChatbotIntent.GetYesterdaySpending -> {
                val total = expenses
                    .filter { it.date >= startOfYesterday && it.date < endOfYesterday }
                    .sumOf { it.amount }
                "Your spending yesterday was $currencySymbol %.2f".format(total)
            }

            is ChatbotIntent.GetWeeklySpending -> {
                val total = expenses
                    .filter { it.date >= startOfWeek }
                    .sumOf { it.amount }
                "Your spending this week is $currencySymbol %.2f".format(total)
            }

            is ChatbotIntent.GetMonthlySpending -> {
                val monthlyTotal = expenses
                    .filter { it.date >= startOfMonth }
                    .sumOf { it.amount }
                "Your spending for this month is $currencySymbol %.2f".format(monthlyTotal)
            }

            is ChatbotIntent.GetCategorySpending -> {
                val queryCategory = intent.category.lowercase().replace(Regex("[?!.,]"), "").trim()
                val targetCategory = when {
                    queryCategory.contains("food") || queryCategory.contains("dining") -> "Food & Dining"
                    queryCategory.contains("travel") || queryCategory.contains("transport") -> "Travel"
                    queryCategory.contains("shopping") -> "Shopping"
                    queryCategory.contains("bill") || queryCategory.contains("utility") -> "Bills & Utilities"
                    else -> intent.category.replaceFirstChar { it.uppercase() }
                }

                val categoryExpenses = expenses.filter { 
                    it.category.equals(targetCategory, ignoreCase = true) ||
                    it.category.lowercase().contains(queryCategory) ||
                    queryCategory.contains(it.category.lowercase())
                }
                val total = categoryExpenses.sumOf { it.amount }
                if (categoryExpenses.isEmpty()) {
                    "You haven't recorded any expenses for category '$targetCategory' yet."
                } else {
                    "You have spent $currencySymbol %.2f on $targetCategory.".format(total)
                }
            }

            is ChatbotIntent.GetHighestExpense -> {
                val highest = expenses.maxByOrNull { it.amount }
                if (highest == null) {
                    "You don't have any expenses recorded yet."
                } else {
                    "Your highest expense is $currencySymbol %.2f for '${highest.note.ifBlank { highest.category }}' on ${highest.date.toLocalDate()}.".format(highest.amount)
                }
            }

            is ChatbotIntent.GetRecentTransactions -> {
                val recent = expenses.take(intent.limit)
                if (recent.isEmpty()) {
                    "No recent transactions found."
                } else {
                    buildString {
                        append("Here are your ${recent.size} most recent expenses:\n")
                        recent.forEach { tx ->
                            append("\n• ${tx.date.toLocalDate()}: $currencySymbol %.2f (${tx.category}) - ${tx.note.ifBlank { "No note" }}".format(tx.amount))
                        }
                    }
                }
            }

            is ChatbotIntent.GetBudgetStatus -> {
                val budgets = budgetRepository.getAllBudgets().first()
                if (budgets.isEmpty()) {
                    "You haven't set up any budgets yet."
                } else {
                    val budgetEngine = BudgetEngine(budgetRepository, transactionRepository)
                    buildString {
                        append("Here is your budget status:\n")
                        for (budget in budgets) {
                            val status = budgetEngine.getBudgetStatus(budget)
                            append("\n• ${status.budget.category}: Spent $currencySymbol %.2f of $currencySymbol %.2f (%.0f%%)".format(
                                status.spent, status.budget.limit, status.percentageUsed * 100f
                            ))
                        }
                    }
                }
            }

            is ChatbotIntent.GetSpendingSummary -> {
                val monthlyTotal = expenses
                    .filter { it.date >= startOfMonth }
                    .sumOf { it.amount }
                
                val categoryTotals = expenses
                    .filter { it.date >= startOfMonth }
                    .groupBy { it.category }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount} }
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)

                buildString {
                    append("📊 Spending Summary (This Month):\n\n")
                    append("• Total Spent: $currencySymbol %.2f\n".format(monthlyTotal))
                    if (categoryTotals.isNotEmpty()) {
                        append("\nTop Categories:\n")
                        categoryTotals.forEach { (cat, amt) ->
                            append("• $cat: $currencySymbol %.2f\n".format(amt))
                        }
                    }
                }
            }

            is ChatbotIntent.Unknown -> {
                "I'm sorry, I didn't quite understand that. Try asking about your spending, monthly expenses, budget status, or type 'help'."
            }
        }
    }

    class Factory(
        private val database: ExpenseDatabase,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatbotViewModel(
                TransactionRepository(database.transactionDao()),
                BudgetRepository(database.budgetDao()),
                preferencesManager
            ) as T
        }
    }
}
