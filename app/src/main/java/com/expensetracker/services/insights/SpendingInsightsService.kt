package com.expensetracker.services.insights

import com.expensetracker.data.model.Transaction
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class SpendingInsight(
    val type: InsightType,
    val title: String,
    val message: String,
    val severity: InsightSeverity,
    val category: String? = null,
    val percentage: Double? = null,
    val amount: Double? = null
)

enum class InsightType {
    SPIKE, TREND, BUDGET_ALERT, RECURRING, TIP, COMPARISON
}

enum class InsightSeverity {
    INFO, WARNING, ALERT
}

class SpendingInsightsService {

    fun generateInsights(transactions: List<Transaction>): List<SpendingInsight> {
        if (transactions.isEmpty()) return emptyList()

        val insights = mutableListOf<SpendingInsight>()

        val currentMonth = getCurrentMonthTransactions(transactions)
        val lastMonth = getLastMonthTransactions(transactions)

        if (currentMonth.isNotEmpty() && lastMonth.isNotEmpty()) {
            insights.addAll(analyzeMonthOverMonth(currentMonth, lastMonth))
        }

        insights.addAll(detectSpikes(currentMonth))

        insights.addAll(detectRecurringExpenses(currentMonth))

        insights.addAll(generateTips(currentMonth))

        return insights.take(5)
    }

    private fun getCurrentMonthTransactions(transactions: List<Transaction>): List<Transaction> {
        val now = LocalDateTime.now()
        val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
        return transactions.filter { it.date >= startOfMonth }
    }

    private fun getLastMonthTransactions(transactions: List<Transaction>): List<Transaction> {
        val now = LocalDateTime.now()
        val startOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
        val endOfLastMonth = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
        return transactions.filter { it.date >= startOfLastMonth && it.date < endOfLastMonth }
    }

    private fun analyzeMonthOverMonth(
        currentMonth: List<Transaction>,
        lastMonth: List<Transaction>
    ): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        val currentTotal = currentMonth.sumOf { it.amount }
        val lastTotal = lastMonth.sumOf { it.amount }

        if (lastTotal > 0) {
            val changePercent = ((currentTotal - lastTotal) / lastTotal) * 100

            when {
                changePercent >= 20 -> {
                    insights.add(
                        SpendingInsight(
                            type = InsightType.COMPARISON,
                            title = "Spending Up 📈",
                            message = "You've spent ${String.format("%.0f", kotlin.math.abs(changePercent))}% more this month compared to last month",
                            severity = InsightSeverity.WARNING,
                            percentage = changePercent,
                            amount = currentTotal - lastTotal
                        )
                    )
                }
                changePercent <= -20 -> {
                    insights.add(
                        SpendingInsight(
                            type = InsightType.COMPARISON,
                            title = "Great Job! 🎉",
                            message = "You've spent ${String.format("%.0f", kotlin.math.abs(changePercent))}% less this month. Keep it up!",
                            severity = InsightSeverity.INFO,
                            percentage = changePercent,
                            amount = lastTotal - currentTotal
                        )
                    )
                }
            }
        }

        val currentByCategory = currentMonth.groupBy { it.category }
        val lastByCategory = lastMonth.groupBy { it.category }

        for ((category, transactions) in currentByCategory) {
            val currentSpent = transactions.sumOf { it.amount }
            val lastSpent = lastByCategory[category]?.sumOf { it.amount } ?: 0.0

            if (lastSpent > 0) {
                val categoryChange = ((currentSpent - lastSpent) / lastSpent) * 100

                if (categoryChange >= 50) {
                    insights.add(
                        SpendingInsight(
                            type = InsightType.SPIKE,
                            title = "Spending Spike in $category",
                            message = "You've spent ${String.format("%.0f", kotlin.math.abs(categoryChange))}% more on $category this month",
                            severity = InsightSeverity.ALERT,
                            category = category,
                            percentage = categoryChange,
                            amount = currentSpent - lastSpent
                        )
                    )
                }
            }
        }

        return insights
    }

    private fun detectSpikes(currentMonth: List<Transaction>): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        val weeklySpending = currentMonth.groupBy { weekOfMonth(it.date) }
        
        if (weeklySpending.size >= 2) {
            val sortedWeeks = weeklySpending.entries.sortedBy { it.key }
            val lastWeek = sortedWeeks.lastOrNull()
            val previousWeeks = sortedWeeks.dropLast(1)
            
            if (lastWeek != null && previousWeeks.isNotEmpty()) {
                val avgPrevious = previousWeeks.map { it.value.sumOf { t -> t.amount } }.average()
                val currentWeekTotal = lastWeek.value.sumOf { it.amount }
                
                if (currentWeekTotal > avgPrevious * 1.5) {
                    insights.add(
                        SpendingInsight(
                            type = InsightType.TREND,
                            title = "High Spending Week",
                            message = "This week's spending is 50% higher than your average. Consider tracking carefully.",
                            severity = InsightSeverity.WARNING,
                            amount = currentWeekTotal
                        )
                    )
                }
            }
        }

        val highValueTransactions = currentMonth.filter { it.amount > 5000 }
        if (highValueTransactions.size >= 2) {
            insights.add(
                SpendingInsight(
                    type = InsightType.TREND,
                    title = "Multiple High-Value Purchases",
                    message = "You have ${highValueTransactions.size} transactions over ₹5,000 this month",
                    severity = InsightSeverity.INFO,
                    amount = highValueTransactions.sumOf { it.amount }
                )
            )
        }

        return insights
    }

    private fun detectRecurringExpenses(currentMonth: List<Transaction>): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        val byCategory = currentMonth.groupBy { it.category }
        
        for ((category, transactions) in byCategory) {
            if (transactions.size >= 3) {
                val amounts = transactions.map { it.amount }
                val avgAmount = amounts.average()
                val variance = amounts.map { kotlin.math.abs(it - avgAmount) }.average()
                val coefficient = if (avgAmount > 0) variance / avgAmount else 0.0
                
                if (coefficient < 0.2) {
                    insights.add(
                        SpendingInsight(
                            type = InsightType.RECURRING,
                            title = "Regular $category Expense",
                            message = "Your $category spending is consistent at ~₹${String.format("%.0f", avgAmount)} per transaction",
                            severity = InsightSeverity.INFO,
                            category = category,
                            amount = avgAmount
                        )
                    )
                }
            }
        }

        return insights
    }

    private fun generateTips(currentMonth: List<Transaction>): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        val dailyCount = currentMonth.groupBy { it.date.toLocalDate() }.size
        val avgPerDay = if (dailyCount > 0) currentMonth.sumOf { it.amount } / dailyCount else 0.0

        if (avgPerDay > 1000) {
            insights.add(
                SpendingInsight(
                    type = InsightType.TIP,
                    title = "Daily Spending Tip",
                    message = "You're averaging ₹${String.format("%.0f", avgPerDay)}/day. Consider setting a daily budget.",
                    severity = InsightSeverity.INFO,
                    amount = avgPerDay
                )
            )
        }

        val weekends = currentMonth.filter { 
            it.date.dayOfWeek == java.time.DayOfWeek.SATURDAY || 
            it.date.dayOfWeek == java.time.DayOfWeek.SUNDAY 
        }
        val weekdayTotal = currentMonth.sumOf { it.amount } - weekends.sumOf { it.amount }
        val weekendTotal = weekends.sumOf { it.amount }

        if (weekendTotal > weekdayTotal * 0.5 && weekendTotal > 2000) {
            insights.add(
                SpendingInsight(
                    type = InsightType.TIP,
                    title = "Weekend Spending",
                    message = "You spend more on weekends. Plan ahead to save ₹${String.format("%.0f", weekendTotal * 0.2)}/month",
                    severity = InsightSeverity.INFO,
                    amount = weekendTotal
                )
            )
        }

        val topCategory = currentMonth.groupBy { it.category }
            .maxByOrNull { it.value.sumOf { t -> t.amount } }

        val totalSpend = currentMonth.sumOf { it.amount }
        if (topCategory != null && totalSpend > 0 && topCategory.value.sumOf { it.amount } > totalSpend * 0.4) {
            insights.add(
                SpendingInsight(
                    type = InsightType.TIP,
                    title = "Top Spending Category",
                    message = "${topCategory.key} is ${String.format("%.0f", (topCategory.value.sumOf { it.amount } / totalSpend) * 100)}% of your spending. Review if needed.",
                    severity = InsightSeverity.INFO,
                    category = topCategory.key
                )
            )
        }

        return insights
    }

    private fun weekOfMonth(date: LocalDateTime): Int {
        return (date.dayOfMonth - 1) / 7 + 1
    }
}
