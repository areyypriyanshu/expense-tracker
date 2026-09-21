package com.expensetracker.domain.chatbot

sealed class ChatbotIntent {
    object GetTotalSpending : ChatbotIntent()
    object GetTodaySpending : ChatbotIntent()
    object GetYesterdaySpending : ChatbotIntent()
    object GetWeeklySpending : ChatbotIntent()
    data class GetMonthlySpending(val month: Int? = null, val year: Int? = null) : ChatbotIntent()
    data class GetCategorySpending(val category: String) : ChatbotIntent()
    object GetHighestExpense : ChatbotIntent()
    data class GetRecentTransactions(val limit: Int = 5) : ChatbotIntent()
    object GetBudgetStatus : ChatbotIntent()
    object GetSpendingSummary : ChatbotIntent()
    object Help : ChatbotIntent()
    object Unknown : ChatbotIntent()
}

class IntentParser {

    fun parse(input: String): ChatbotIntent {
        // Normalize input by removing punctuation such as ?, !, ., and ,
        val cleanInput = input.trim().lowercase().replace(Regex("[?!.,]"), "")

        return when {
            // Help
            cleanInput.matches(Regex(".*\\b(help|commands|what can you do)\\b.*")) -> {
                ChatbotIntent.Help
            }

            // Highest Expense
            cleanInput.matches(Regex(".*\\b(highest|biggest|most expensive|largest)\\b.*(expense|spending|transaction)?.*")) -> {
                ChatbotIntent.GetHighestExpense
            }

            // Recent Transactions
            cleanInput.matches(Regex(".*\\b(recent|latest|last)\\b.*(transactions|expenses|spendings|purchases)?.*")) -> {
                val match = Regex("\\b(\\d+)\\b").find(cleanInput)
                val limit = match?.value?.toIntOrNull() ?: 5
                ChatbotIntent.GetRecentTransactions(limit = limit)
            }

            // Budget Status
            cleanInput.matches(Regex(".*\\b(budget|budgets)\\b.*(status|overview|remaining|left|over)?.*")) ||
            cleanInput.matches(Regex(".*\\bhow is my budget\\b.*")) -> {
                ChatbotIntent.GetBudgetStatus
            }

            // Spending Summary / Overview
            cleanInput.matches(Regex(".*\\b(summary|overview|breakdown|analytics)\\b.*")) -> {
                ChatbotIntent.GetSpendingSummary
            }

            // Category Spending
            cleanInput.contains("on ") -> {
                val rawCategory = cleanInput.substringAfter("on ").trim().split(" ").firstOrNull() ?: ""
                val category = rawCategory.replace(Regex("[?!.,]+"), "").trim()
                if (category.isNotBlank()) {
                    ChatbotIntent.GetCategorySpending(category)
                } else {
                    ChatbotIntent.Unknown
                }
            }

            // Today Spending
            cleanInput.matches(Regex(".*\\btoday\\b.*")) -> {
                ChatbotIntent.GetTodaySpending
            }

            // Yesterday Spending
            cleanInput.matches(Regex(".*\\byesterday\\b.*")) -> {
                ChatbotIntent.GetYesterdaySpending
            }

            // Weekly Spending
            cleanInput.matches(Regex(".*\\b(this week|weekly|week)\\b.*")) -> {
                ChatbotIntent.GetWeeklySpending
            }

            // Monthly Spending
            cleanInput.matches(Regex(".*\\b(this month|monthly|month)\\b.*")) -> {
                ChatbotIntent.GetMonthlySpending()
            }

            // Total Spending
            cleanInput.matches(Regex(".*\\b(total|all)\\b.*(spent|spending|expense|expenses)?.*")) ||
            cleanInput.matches(Regex(".*\\bhow much.*spend.*")) -> {
                ChatbotIntent.GetTotalSpending
            }

            else -> ChatbotIntent.Unknown
        }
    }
}
