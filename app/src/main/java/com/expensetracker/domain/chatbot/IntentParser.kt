package com.expensetracker.domain.chatbot

import com.expensetracker.domain.engine.CategoryEngine

sealed class ChatbotIntent {
    object GetTotalSpending : ChatbotIntent()
    object GetTodaySpending : ChatbotIntent()
    object GetYesterdaySpending : ChatbotIntent()
    object GetWeeklySpending : ChatbotIntent()
    object GetLastWeekSpending : ChatbotIntent()
    data class GetMonthlySpending(val month: Int? = null, val year: Int? = null) : ChatbotIntent()
    object GetLastMonthSpending : ChatbotIntent()
    data class GetCategorySpending(val category: String) : ChatbotIntent()
    object GetHighestExpense : ChatbotIntent()
    object GetLowestExpense : ChatbotIntent()
    data class GetRecentTransactions(val limit: Int = 5) : ChatbotIntent()
    data class GetCategoryTransactions(val category: String, val limit: Int = 5) : ChatbotIntent()
    data class GetExpensesAboveAmount(val amount: Double) : ChatbotIntent()
    object GetBudgetStatus : ChatbotIntent()
    data class GetCategoryBudgetStatus(val category: String) : ChatbotIntent()
    object GetSpendingSummary : ChatbotIntent()
    object GetAverageDailySpending : ChatbotIntent()
    object GetTotalIncome : ChatbotIntent()
    object GetNetBalance : ChatbotIntent()
    object CompareMonths : ChatbotIntent()
    object CompareWeeks : ChatbotIntent()
    object CompareYears : ChatbotIntent()
    object CompareSamePeriodLastMonth : ChatbotIntent()
    object CompareSamePeriodLastYear : ChatbotIntent()
    object CompareLast7Days : ChatbotIntent()
    object CompareLast30Days : ChatbotIntent()
    data class CompareCategories(val category1: String, val category2: String) : ChatbotIntent()
    object GetHighestExpenseCategory : ChatbotIntent()
    object GetCategoryWithMostIncrease : ChatbotIntent()
    object GetSpendingTrendInsight : ChatbotIntent()
    data class FilteredTransactions(
        val category: String? = null,
        val startDate: java.time.LocalDateTime? = null,
        val endDate: java.time.LocalDateTime? = null,
        val minAmount: Double? = null,
        val maxAmount: Double? = null,
        val limit: Int = 10
    ) : ChatbotIntent()
    object Help : ChatbotIntent()
    object Unknown : ChatbotIntent()
}

class IntentParser {

    fun parse(input: String): ChatbotIntent {
        val cleanInput = input.trim().lowercase().replace(Regex("[?!,]"), "")

        return when {
            // Help
            cleanInput.matches(Regex(".*\\b(help|commands|what can you do|assist me|what do you do)\\b.*")) -> {
                ChatbotIntent.Help
            }

            // Lowest Expense
            cleanInput.matches(Regex(".*\\b(lowest|smallest|min|minimum)\\b.*(expense|spending|transaction)?.*")) -> {
                ChatbotIntent.GetLowestExpense
            }

            // Expenses above amount
            cleanInput.matches(Regex(".*\\b(above|over|more than|greater than|exceeds?)\\b.*\\d+.*")) ||
            cleanInput.matches(Regex(".*\\b(above|over|more than|greater than)\\b.*(amount|dollars?|usd?)\\b.*")) ||
            cleanInput.matches(Regex(".*\\b(expenses?|spending)\\b.*\\d+.*")) -> {
                val amountMatch = Regex("\\b(\\d+(?:\\.\\d+)?)\\b").find(cleanInput)
                val amount = amountMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                if (amount > 0) ChatbotIntent.GetExpensesAboveAmount(amount) else ChatbotIntent.Unknown
            }

            // Category transactions
            cleanInput.contains("transaction") && (cleanInput.contains("on ") || cleanInput.contains("for ") || cleanInput.contains("in ") || cleanInput.contains("category") || cleanInput.contains("food") || cleanInput.contains("travel") || cleanInput.contains("shopping") || cleanInput.contains("bill")) -> {
                val category = extractCategory(cleanInput)
                if (category.isNotBlank()) {
                    ChatbotIntent.GetCategoryTransactions(category)
                } else {
                    ChatbotIntent.Unknown
                }
            }

            // Category budget status
            cleanInput.contains("budget") && (extractCategory(cleanInput).isNotBlank() || cleanInput.contains("food") || cleanInput.contains("travel") || cleanInput.contains("shopping") || cleanInput.contains("bill")) -> {
                val category = extractCategory(cleanInput)
                if (category.isNotBlank()) ChatbotIntent.GetCategoryBudgetStatus(category) else ChatbotIntent.GetBudgetStatus
            }

            // Budget Status
            cleanInput.matches(Regex(".*\\b(budget|budgets)\\b.*(status|overview|remaining|left|over)?.*")) ||
            cleanInput.matches(Regex(".*\\bhow is my budget\\b.*")) ||
            cleanInput.matches(Regex(".*\\bbudget\\b.*\\b(status|left|remaining)\\b.*")) -> {
                ChatbotIntent.GetBudgetStatus
            }

            // Category spending (more natural phrases)
            (cleanInput.contains("on ") || cleanInput.contains("for ") || cleanInput.contains("in ") || cleanInput.contains("category") ||
             cleanInput.contains("food") || cleanInput.contains("travel") || cleanInput.contains("shopping") || cleanInput.contains("bill") || cleanInput.contains("transport")) -> {
                val category = extractCategory(cleanInput)
                if (category.isNotBlank()) {
                    ChatbotIntent.GetCategorySpending(category)
                } else {
                    ChatbotIntent.Unknown
                }
            }

            // Compare this week vs last week
            cleanInput.contains("this week") && (cleanInput.contains("last week") || cleanInput.contains("compare week")) ||
            cleanInput.contains("week") && cleanInput.contains("compare") && !cleanInput.contains("month") -> {
                ChatbotIntent.CompareWeeks
            }

            // Compare this year vs last year
            cleanInput.contains("this year") && (cleanInput.contains("last year") || cleanInput.contains("compare year")) ||
            cleanInput.contains("year") && cleanInput.contains("compare") && !cleanInput.contains("month") && !cleanInput.contains("week") -> {
                ChatbotIntent.CompareYears
            }

            // Same period last month (elapsed days)
            cleanInput.contains("same period") && cleanInput.contains("last month") ||
            cleanInput.contains("same days last month") ||
            cleanInput.contains("same period") && cleanInput.contains("month") -> {
                ChatbotIntent.CompareSamePeriodLastMonth
            }

            // Same period last year
            cleanInput.contains("same period") && cleanInput.contains("last year") ||
            cleanInput.contains("same dates last year") ||
            cleanInput.contains("same period") && cleanInput.contains("year") -> {
                ChatbotIntent.CompareSamePeriodLastYear
            }

            // Last 7 days vs previous 7 days
            cleanInput.contains("last 7") || cleanInput.contains("7 days") || cleanInput.contains("previous 7") -> {
                ChatbotIntent.CompareLast7Days
            }

            // Last 30 days vs previous 30 days
            cleanInput.contains("last 30") || cleanInput.contains("30 days") || cleanInput.contains("previous 30") -> {
                ChatbotIntent.CompareLast30Days
            }

            // Compare months
            cleanInput.matches(Regex(".*\\b(compare|vs|versus)\\b.*\\b(this month|last month|monthly)\\b.*")) ||
            cleanInput.contains("more this month") || cleanInput.contains("more than last month") ||
            cleanInput.contains("compare month") || cleanInput.contains("spend more") -> {
                ChatbotIntent.CompareMonths
            }

            // Compare categories
            cleanInput.contains("compare") && (cleanInput.contains("food") || cleanInput.contains("travel") || cleanInput.contains("shopping") || cleanInput.contains("bill")) -> {
                val c1 = when {
                    cleanInput.contains("food") -> "Food & Dining"
                    cleanInput.contains("travel") -> "Travel"
                    cleanInput.contains("shopping") -> "Shopping"
                    cleanInput.contains("bill") -> "Bills & Utilities"
                    else -> ""
                }
                val c2 = when {
                    cleanInput.contains("food") && cleanInput.contains("travel") -> "Travel"
                    cleanInput.contains("food") && cleanInput.contains("shopping") -> "Shopping"
                    cleanInput.contains("travel") && cleanInput.contains("shopping") -> "Shopping"
                    else -> if (c1.isNotBlank()) "Food & Dining" else ""
                }
                if (c1.isNotBlank() && c2.isNotBlank()) ChatbotIntent.CompareCategories(c1, c2) else ChatbotIntent.Unknown
            }

            // Highest spending category
            cleanInput.matches(Regex(".*\\b(highest|most|top|largest)\\b.*\\b(category|categories|spending)\\b.*")) ||
            cleanInput.contains("which category") || cleanInput.contains("where am i spending the most") -> {
                ChatbotIntent.GetHighestExpenseCategory
            }

            // Category with most increase
            cleanInput.contains("increased") || cleanInput.contains("increase") ||
            cleanInput.contains("which category costs more") || cleanInput.contains("which category increased") -> {
                ChatbotIntent.GetCategoryWithMostIncrease
            }

            // Spending trend insight
            cleanInput.contains("trend") || cleanInput.contains("usual") || cleanInput.contains("more than usual") || cleanInput.contains("spending more") -> {
                ChatbotIntent.GetSpendingTrendInsight
            }

            // Average daily spending
            cleanInput.matches(Regex(".*\\b(average|avg)\\b.*\\b(daily|per day|day)\\b.*")) ||
            cleanInput.matches(Regex(".*\\b(daily|per day)\\b.*\\b(average|avg|mean)\\b.*")) ||
            cleanInput.matches(Regex(".*\\b(how much)\\b.*\\b(average)\\b.*\\b(spend)\\b.*")) -> {
                ChatbotIntent.GetAverageDailySpending
            }

            // Net balance
            cleanInput.matches(Regex(".*\\b(net|balance|remaining)\\b.*")) ||
            cleanInput.matches(Regex(".*\\b(how much)\\b.*\\b(left|balance)\\b.*")) -> {
                ChatbotIntent.GetNetBalance
            }

            // Total income
            cleanInput.matches(Regex(".*\\b(total|all|summarize)\\b.*\\b(income|earnings?|salary|earn)\\b.*")) ||
            cleanInput.matches(Regex(".*\\b(income|earnings?|earn)\\b.*\\b(total|amount)\\b.*")) ||
            cleanInput.matches(Regex(".*\\b(how much)\\b.*\\b(income|earn)\\b.*")) -> {
                ChatbotIntent.GetTotalIncome
            }

            // Highest Expense
            cleanInput.matches(Regex(".*\\b(highest|biggest|most expensive|largest|max)\\b.*(expense|spending|transaction)?.*")) -> {
                ChatbotIntent.GetHighestExpense
            }

            // Last week spending
            cleanInput.matches(Regex(".*\\b(last week|previous week|last 7 days)\\b.*")) -> {
                ChatbotIntent.GetLastWeekSpending
            }

            // Last month spending
            cleanInput.matches(Regex(".*\\b(last month|previous month)\\b.*")) -> {
                ChatbotIntent.GetLastMonthSpending
            }

            // Recent Transactions
            cleanInput.matches(Regex(".*\\b(recent|latest|last)\\b.*(transactions|expenses|spendings|purchases)?.*")) -> {
                val match = Regex("\\b(\\d+)\\b").find(cleanInput)
                val limit = match?.value?.toIntOrNull() ?: 5
                ChatbotIntent.GetRecentTransactions(limit = limit)
            }

            // Spending Summary / Overview
            cleanInput.matches(Regex(".*\\b(summary|overview|breakdown|analytics)\\b.*")) -> {
                ChatbotIntent.GetSpendingSummary
            }

            // This week spending
            cleanInput.matches(Regex(".*\\b(this week|weekly|week)\\b.*")) -> {
                ChatbotIntent.GetWeeklySpending
            }

            // This month spending
            cleanInput.matches(Regex(".*\\b(this month|monthly|month)\\b.*")) -> {
                ChatbotIntent.GetMonthlySpending()
            }

            // Yesterday spending
            cleanInput.matches(Regex(".*\\byesterday\\b.*")) -> {
                ChatbotIntent.GetYesterdaySpending
            }

            // Today spending
            cleanInput.matches(Regex(".*\\btoday\\b.*")) -> {
                ChatbotIntent.GetTodaySpending
            }

            // Total Spending
            cleanInput.matches(Regex(".*\\b(total|all)\\b.*(spent|spending|expense|expenses)?.*")) ||
            cleanInput.matches(Regex(".*\\bhow much.*spend.*")) -> {
                ChatbotIntent.GetTotalSpending
            }

            // Filter Transactions
            cleanInput.matches(Regex(".*\\b(show)\\b.*(transaction|expense|spending)?.*")) ||
            cleanInput.contains("between") || cleanInput.contains("above") || cleanInput.contains("largest") || cleanInput.contains("top") -> {
                val category = extractCategory(cleanInput).ifBlank { null }
                val amounts = Regex("\\b(\\d+(?:\\.\\d+)?)\\b").findAll(cleanInput).mapNotNull { it.value.toDoubleOrNull() }.toList()
                val min = if (amounts.size >= 2) amounts.minOrNull() else (amounts.getOrNull(0)?.takeIf { cleanInput.contains("between") || cleanInput.contains("above") })
                val max = if (amounts.size >= 2) amounts.maxOrNull() else null
                ChatbotIntent.FilteredTransactions(category = category, minAmount = min, maxAmount = max, limit = if (cleanInput.contains("largest") || cleanInput.contains("top") || cleanInput.contains("5")) 5 else 10)
            }

            else -> ChatbotIntent.Unknown
        }
    }

    private fun extractCategory(input: String): String {
        val patterns = listOf(
            Regex("\\bon\\s+([a-z\\s&]+?)(?:\\s+(?:spend|expense|transaction|budget|amount|today|yesterday|this|last|week|month|show|give|what|how|is))"),
            Regex("\\bfor\\s+([a-z\\s&]+?)(?:\\s+(?:spend|expense|transaction|budget))"),
            Regex("\\bin\\s+([a-z\\s&]+?)(?:\\s+(?:category|spending|expenses))"),
            Regex("\\bcategory\\s+([a-z\\s&]+?)\\b")
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                val raw = match.groupValues[1].trim()
                val cleaned = raw.replace(Regex("[?!]+"), "").trim()
                return normalizeCategory(cleaned)
            }
        }
        // Keyword-based fallback for phrases like "Food expenses?" or "Travel spending".
        // Returns "" (not "Other") so callers can keep their own fallbacks.
        val guess = CategoryEngine.categorizeText(input)
        return if (guess == CategoryEngine.FALLBACK_CATEGORY) "" else guess
    }

    private fun normalizeCategory(value: String): String =
        CategoryEngine.resolveCategoryName(value)
}
