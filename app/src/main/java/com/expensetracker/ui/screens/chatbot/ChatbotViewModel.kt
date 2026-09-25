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
import com.expensetracker.services.insights.SpendingInsightsService
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
            text = "Hello! I am your Finance Assistant. Ask me about your spending, monthly totals, budgets, or type 'help' to see what I can do.",
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
                    append("• \"Give me a spending summary\"\n")
                    append("• \"What is my net balance?\"\n")
                    append("• \"How is my food budget?\"\n")
                    append("• \"Show my lowest expense\"\n")
                    append("• \"Expenses above 100?\"\n")
                    append("• \"Average daily spending\"\n")
                    append("• \"Total income\"\n")
                    append("• \"Compare this month with last month\"\n")
                    append("• \"Which category costs me the most?\"\n")
                    append("• \"Compare Food and Shopping\"\n")
                    append("• \"Where am I spending the most?\"\n")
                    append("• \"Did I spend more than usual?\"")
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

            is ChatbotIntent.GetLastWeekSpending -> {
                val startOfLastWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(7).toLocalDate().atStartOfDay()
                val endOfLastWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay()
                val total = expenses
                    .filter { it.date >= startOfLastWeek && it.date < endOfLastWeek }
                    .sumOf { it.amount }
                "Your spending last week was $currencySymbol %.2f".format(total)
            }

            is ChatbotIntent.GetWeeklySpending -> {
                val total = expenses
                    .filter { it.date >= startOfWeek }
                    .sumOf { it.amount }
                "Your spending this week is $currencySymbol %.2f".format(total)
            }

            is ChatbotIntent.GetLastMonthSpending -> {
                val startOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                val endOfLastMonth = now.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1).withHour(23).withMinute(59)
                val total = expenses
                    .filter { it.date >= startOfLastMonth && it.date <= endOfLastMonth }
                    .sumOf { it.amount }
                "Your spending last month was $currencySymbol %.2f".format(total)
            }

            is ChatbotIntent.GetMonthlySpending -> {
                val monthlyTotal = expenses
                    .filter { it.date >= startOfMonth }
                    .sumOf { it.amount }
                "Your spending for this month is $currencySymbol %.2f".format(monthlyTotal)
            }

            is ChatbotIntent.GetCategorySpending -> {
                val queryCategory = intent.category.lowercase().replace(Regex("[?!.]"), "").trim()
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

            is ChatbotIntent.GetLowestExpense -> {
                val lowest = expenses.minByOrNull { it.amount }
                if (lowest == null) {
                    "You don't have any expenses recorded yet."
                } else {
                    "Your lowest expense is $currencySymbol %.2f for '${lowest.note.ifBlank { lowest.category }}' on ${lowest.date.toLocalDate()}.".format(lowest.amount)
                }
            }

            is ChatbotIntent.GetCategoryTransactions -> {
                val queryCategory = intent.category.lowercase().replace(Regex("[?!.,]"), "").trim()
                val targetCategory = when {
                    queryCategory.contains("food") || queryCategory.contains("dining") -> "Food & Dining"
                    queryCategory.contains("travel") || queryCategory.contains("transport") -> "Travel"
                    queryCategory.contains("shopping") -> "Shopping"
                    queryCategory.contains("bill") || queryCategory.contains("utility") -> "Bills & Utilities"
                    else -> intent.category.replaceFirstChar { it.uppercase() }
                }
                val catTxs = expenses.filter {
                    it.category.equals(targetCategory, ignoreCase = true) ||
                    it.category.lowercase().contains(queryCategory) ||
                    queryCategory.contains(it.category.lowercase())
                }.take(intent.limit)
                if (catTxs.isEmpty()) {
                    "No transactions found for category '$targetCategory'."
                } else {
                    buildString {
                        append("Here are your ${catTxs.size} recent transactions for $targetCategory:\n")
                        catTxs.forEach { tx ->
                            append("\n• ${tx.date.toLocalDate()}: $currencySymbol %.2f (${tx.category}) - ${tx.note.ifBlank { "No note" }}".format(tx.amount))
                        }
                    }
                }
            }

            is ChatbotIntent.GetExpensesAboveAmount -> {
                val above = expenses.filter { it.amount > intent.amount }.sortedByDescending { it.amount }
                if (above.isEmpty()) {
                    "No expenses above $currencySymbol %.2f found.".format(intent.amount)
                } else {
                    buildString {
                        append("Expenses above $currencySymbol %.2f:\n".format(intent.amount))
                        above.take(5).forEach { tx ->
                            append("\n• ${tx.date.toLocalDate()}: $currencySymbol %.2f (${tx.category}) - ${tx.note.ifBlank { "No note" }}".format(tx.amount))
                        }
                        if (above.size > 5) append("\n... and ${above.size - 5} more.")
                    }
                }
            }

            is ChatbotIntent.GetCategoryBudgetStatus -> {
                val queryCategory = intent.category.lowercase().replace(Regex("[?!.,]"), "").trim()
                val targetCategory = when {
                    queryCategory.contains("food") || queryCategory.contains("dining") -> "Food & Dining"
                    queryCategory.contains("travel") || queryCategory.contains("transport") -> "Travel"
                    queryCategory.contains("shopping") -> "Shopping"
                    queryCategory.contains("bill") || queryCategory.contains("utility") -> "Bills & Utilities"
                    else -> intent.category.replaceFirstChar { it.uppercase() }
                }
                val budgets = budgetRepository.getAllBudgets().first()
                val budget = budgets.find { it.category.equals(targetCategory, ignoreCase = true) || it.category.contains(targetCategory, ignoreCase = true) }
                if (budget == null) {
                    "No budget set for category '$targetCategory'."
                } else {
                    val budgetEngine = BudgetEngine(budgetRepository, transactionRepository)
                    val status = budgetEngine.getBudgetStatus(budget)
                    "Category budget for ${status.budget.category}: Spent $currencySymbol %.2f of $currencySymbol %.2f (%.0f%%). ${if (status.isOverBudget) "Over budget!" else if (status.shouldAlert) "Approaching limit." else "Within budget."}".format(
                        status.spent, status.budget.limit, status.percentageUsed * 100f
                    )
                }
            }

            is ChatbotIntent.GetAverageDailySpending -> {
                val total = expenses.sumOf { it.amount }
                val earliest = expenses.minOfOrNull { it.date.toLocalDate() } ?: now.toLocalDate()
                val days = maxOf(1, (now.toLocalDate().toEpochDay() - earliest.toEpochDay() + 1).toInt())
                val avgDaily = total / days
                "Your average daily spending is $currencySymbol %.2f over $days days.".format(avgDaily)
            }

            is ChatbotIntent.GetTotalIncome -> {
                val income = transactions.filter { it.isIncome }.sumOf { it.amount }
                "Your total recorded income is $currencySymbol %.2f".format(income)
            }

            is ChatbotIntent.GetNetBalance -> {
                val totalIncome = transactions.filter { it.isIncome }.sumOf { it.amount }
                val totalExpense = expenses.sumOf { it.amount }
                val net = totalIncome - totalExpense
                "Your net balance is $currencySymbol %.2f (Income: $currencySymbol %.2f - Expenses: $currencySymbol %.2f).".format(net, totalIncome, totalExpense)
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

            is ChatbotIntent.CompareMonths -> {
                val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                val thisMonth = expenses.filter { it.date >= startOfMonth }.sumOf { it.amount }
                val startOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                val endOfLastMonth = now.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1).withHour(23).withMinute(59)
                val lastMonth = expenses.filter { it.date >= startOfLastMonth && it.date <= endOfLastMonth }.sumOf { it.amount }
                val diff = thisMonth - lastMonth
                "This month (day 1 through today) vs complete previous calendar month: This month $currencySymbol %.2f | Last month $currencySymbol %.2f. Difference: $currencySymbol %.2f %s.".format(thisMonth, lastMonth, kotlin.math.abs(diff), if (diff > 0) "more this month" else if (diff < 0) "less this month" else "same")
            }

            is ChatbotIntent.CompareWeeks -> {
                val startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay()
                val thisWeek = expenses.filter { it.date >= startOfWeek }.sumOf { it.amount }
                val startOfLastWeek = startOfWeek.minusDays(7)
                val endOfLastWeek = startOfWeek.minusDays(1).withHour(23).withMinute(59)
                val lastWeek = expenses.filter { it.date >= startOfLastWeek && it.date <= endOfLastWeek }.sumOf { it.amount }
                val diff = thisWeek - lastWeek
                "This week (Monday through today) vs previous Monday–Sunday: This week $currencySymbol %.2f | Last week $currencySymbol %.2f. Difference: $currencySymbol %.2f %s.".format(thisWeek, lastWeek, kotlin.math.abs(diff), if (diff > 0) "more this week" else if (diff < 0) "less this week" else "same")
            }

            is ChatbotIntent.CompareYears -> {
                val startOfYear = now.with(TemporalAdjusters.firstDayOfYear()).withHour(0).withMinute(0)
                val thisYear = expenses.filter { it.date >= startOfYear }.sumOf { it.amount }
                val startOfLastYear = now.minusYears(1).with(TemporalAdjusters.firstDayOfYear()).withHour(0).withMinute(0)
                val endOfLastYear = now.with(TemporalAdjusters.firstDayOfYear()).minusDays(1).withHour(23).withMinute(59)
                val lastYear = expenses.filter { it.date >= startOfLastYear && it.date <= endOfLastYear }.sumOf { it.amount }
                val diff = thisYear - lastYear
                "This year (Jan 1 through today) vs complete previous calendar year: This year $currencySymbol %.2f | Last year $currencySymbol %.2f. Difference: $currencySymbol %.2f %s.".format(thisYear, lastYear, kotlin.math.abs(diff), if (diff > 0) "more this year" else if (diff < 0) "less this year" else "same")
            }

            is ChatbotIntent.CompareSamePeriodLastMonth -> {
                val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                val elapsedDays = (now.toLocalDate().toEpochDay() - startOfMonth.toLocalDate().toEpochDay() + 1).toInt()
                val samePeriodThis = expenses.filter { it.date >= startOfMonth }.sumOf { it.amount }
                val startOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                val endOfSamePeriodLast = startOfLastMonth.plusDays(elapsedDays.toLong() - 1).withHour(23).withMinute(59)
                val samePeriodLast = expenses.filter { it.date >= startOfLastMonth && it.date <= endOfSamePeriodLast }.sumOf { it.amount }
                val diff = samePeriodThis - samePeriodLast
                "Same period last month (current month day 1 through today, $elapsedDays days) vs same elapsed days of previous month: $currencySymbol %.2f vs $currencySymbol %.2f (diff $currencySymbol %.2f %s).".format(samePeriodThis, samePeriodLast, kotlin.math.abs(diff), if (diff > 0) "more" else if (diff < 0) "less" else "same")
            }

            is ChatbotIntent.CompareSamePeriodLastYear -> {
                val startOfYear = now.with(TemporalAdjusters.firstDayOfYear()).withHour(0).withMinute(0)
                val elapsedDays = (now.toLocalDate().toEpochDay() - startOfYear.toLocalDate().toEpochDay() + 1).toInt()
                val samePeriodThis = expenses.filter { it.date >= startOfYear }.sumOf { it.amount }
                val startOfLastYear = now.minusYears(1).with(TemporalAdjusters.firstDayOfYear()).withHour(0).withMinute(0)
                val endOfSamePeriodLast = startOfLastYear.plusDays(elapsedDays.toLong() - 1).withHour(23).withMinute(59)
                val samePeriodLast = expenses.filter { it.date >= startOfLastYear && it.date <= endOfSamePeriodLast }.sumOf { it.amount }
                val diff = samePeriodThis - samePeriodLast
                "Same period last year (Jan 1 through today, $elapsedDays days) vs same dates of previous year: $currencySymbol %.2f vs $currencySymbol %.2f (diff $currencySymbol %.2f %s).".format(samePeriodThis, samePeriodLast, kotlin.math.abs(diff), if (diff > 0) "more" else if (diff < 0) "less" else "same")
            }

            is ChatbotIntent.CompareLast7Days -> {
                val end = now.toLocalDate().atStartOfDay()
                val start = end.toLocalDate().minusDays(6).atStartOfDay()
                val prevStart = start.toLocalDate().minusDays(7).atStartOfDay()
                val prevEnd = start.toLocalDate().minusDays(1).atStartOfDay()
                val last7 = expenses.filter { it.date >= start && it.date < end.plusDays(1) }.sumOf { it.amount }
                val prev7 = expenses.filter { it.date >= prevStart && it.date < prevEnd.plusDays(1) }.sumOf { it.amount }
                val diff = last7 - prev7
                "Last 7 days vs previous 7 days: Latest $currencySymbol %.2f | Previous $currencySymbol %.2f (diff $currencySymbol %.2f %s).".format(last7, prev7, kotlin.math.abs(diff), if (diff > 0) "more" else if (diff < 0) "less" else "same")
            }

            is ChatbotIntent.CompareLast30Days -> {
                val end = now.toLocalDate().atStartOfDay()
                val start = end.toLocalDate().minusDays(29).atStartOfDay()
                val prevStart = start.toLocalDate().minusDays(30).atStartOfDay()
                val prevEnd = start.toLocalDate().minusDays(1).atStartOfDay()
                val last30 = expenses.filter { it.date >= start && it.date < end.plusDays(1) }.sumOf { it.amount }
                val prev30 = expenses.filter { it.date >= prevStart && it.date < prevEnd.plusDays(1) }.sumOf { it.amount }
                val diff = last30 - prev30
                "Last 30 days vs previous 30 days: Latest $currencySymbol %.2f | Previous $currencySymbol %.2f (diff $currencySymbol %.2f %s).".format(last30, prev30, kotlin.math.abs(diff), if (diff > 0) "more" else if (diff < 0) "less" else "same")
            }

            is ChatbotIntent.CompareCategories -> {
                val thisMonth = expenses.filter { it.date >= startOfMonth }.sumOf { it.amount }
                val startOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                val endOfLastMonth = now.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1).withHour(23).withMinute(59)
                val lastMonth = expenses.filter { it.date >= startOfLastMonth && it.date <= endOfLastMonth }.sumOf { it.amount }
                val diff = thisMonth - lastMonth
                if (diff > 0) "You spent $currencySymbol %.2f more this month than last month.".format(diff)
                else if (diff < 0) "You spent $currencySymbol %.2f less this month than last month.".format(-diff)
                else "Your spending is the same this month as last month ($currencySymbol %.2f).".format(thisMonth)
            }

            is ChatbotIntent.CompareCategories -> {
                val q1 = intent.category1.lowercase().replace(Regex("[?!.,]"), "").trim()
                val q2 = intent.category2.lowercase().replace(Regex("[?!.,]"), "").trim()
                val target1 = when { q1.contains("food") || q1.contains("dining") -> "Food & Dining"; q1.contains("travel") -> "Travel"; q1.contains("shopping") -> "Shopping"; q1.contains("bill") || q1.contains("utility") -> "Bills & Utilities"; else -> intent.category1 }
                val target2 = when { q2.contains("food") || q2.contains("dining") -> "Food & Dining"; q2.contains("travel") -> "Travel"; q2.contains("shopping") -> "Shopping"; q2.contains("bill") || q2.contains("utility") -> "Bills & Utilities"; else -> intent.category2 }
                val sum1 = expenses.filter { it.category.equals(target1, ignoreCase = true) || it.category.lowercase().contains(q1) }.sumOf { it.amount }
                val sum2 = expenses.filter { it.category.equals(target2, ignoreCase = true) || it.category.lowercase().contains(q2) }.sumOf { it.amount }
                val diff = sum1 - sum2
                "$target1: $currencySymbol %.2f | $target2: $currencySymbol %.2f. Difference: $currencySymbol %.2f %s.".format(sum1, sum2, kotlin.math.abs(diff), if (diff > 0) "$target1 is higher" else if (diff < 0) "$target2 is higher" else "Equal")
            }

            is ChatbotIntent.GetHighestExpenseCategory -> {
                val byCat = expenses.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
                val top = byCat.maxByOrNull { it.value }
                if (top == null) "No spending recorded yet."
                else "Your highest spending category is ${top.key} at $currencySymbol %.2f.".format(top.value)
            }

            is ChatbotIntent.GetCategoryWithMostIncrease -> {
                val currentMonth = expenses.filter { it.date >= startOfMonth }
                val startOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                val endOfLastMonth = now.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1).withHour(23).withMinute(59)
                val lastMonth = expenses.filter { it.date >= startOfLastMonth && it.date <= endOfLastMonth }
                val currentByCat = currentMonth.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
                val lastByCat = lastMonth.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
                val increases = currentByCat.mapNotNull { (cat, cur) ->
                    val last = lastByCat[cat] ?: 0.0
                    if (last > 0) {
                        val pct = ((cur - last) / last) * 100
                        if (pct > 0) Triple(cat, pct, cur - last) else null
                    } else if (cur > 0) Triple(cat, 100.0, cur) else null
                }
                val max = increases.maxByOrNull { it.second }
                if (max == null) "No category showed an increase from last month."
                else "${max.first} increased the most: +%.0f%% ($currencySymbol %.2f).".format(max.second, max.third)
            }

            is ChatbotIntent.GetSpendingTrendInsight -> {
                val insightService = SpendingInsightsService()
                val insights = insightService.generateInsights(expenses)
                if (insights.isEmpty()) "No major trends detected this month."
                else buildString {
                    append("Insights:\n")
                    insights.forEach { i ->
                        append("\n• ${i.title}: ${i.message}\n")
                    }
                }
            }

            is ChatbotIntent.FilteredTransactions -> {
                val filtered = expenses.filter { tx ->
                    (intent.category == null || tx.category.equals(intent.category, ignoreCase = true)) &&
                    (intent.minAmount == null || tx.amount >= intent.minAmount) &&
                    (intent.maxAmount == null || tx.amount <= intent.maxAmount)
                }.sortedByDescending { it.date }.take(intent.limit)
                if (filtered.isEmpty()) "No transactions found matching those criteria."
                else buildString {
                    append("Found ${filtered.size} matching transactions:\n")
                    filtered.forEach { append("\n• ${it.date.toLocalDate()}: ${it.category} - $currencySymbol %.2f".format(it.amount)) }
                }
            }
            is ChatbotIntent.Unknown -> {
                "I'm sorry, I didn't quite understand that. Try asking about your spending, budgets, comparisons, or type 'help'."
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
