package com.expensetracker.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.engine.ReportPeriod
import com.expensetracker.services.insights.SpendingInsight
import com.expensetracker.services.insights.SpendingInsightsService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class SpendTrend { UP, DOWN, FLAT }

data class AnalyticsUiState(
    val totalSpend: Double = 0.0,
    val previousPeriodSpend: Double = 0.0,
    val spendTrend: SpendTrend = SpendTrend.FLAT,
    val trendPercentage: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val dailySpending: List<DailySpending> = emptyList(),
    val selectedPeriod: ReportPeriod = ReportPeriod.MONTHLY,
    val baseCurrency: String = "INR",
    val todaySpend: Double = 0.0,
    val weeklySpend: Double = 0.0,
    val monthlySpend: Double = 0.0,
    val topCategory: String = "",
    val topCategoryAmount: Double = 0.0,
    val transactionCount: Int = 0,
    val dailyAverage: Double = 0.0,
    val insights: List<SpendingInsight> = emptyList(),
    val categoryTransactions: Map<String, List<Transaction>> = emptyMap(),
    val isLoading: Boolean = true
)

data class DailySpending(
    val date: String,
    val amount: Double
)

class AnalyticsViewModel(
    private val transactionRepository: TransactionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.MONTHLY)
    private val insightsService = SpendingInsightsService()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
            val startOfWeek = now.minusDays(now.dayOfWeek.value.toLong() - 1).withHour(0).withMinute(0)
            val startOfToday = now.toLocalDate().atStartOfDay()

            combine(
                transactionRepository.getAllTransactions(),
                preferencesManager.userPreferences,
                _selectedPeriod
            ) { transactions, preferences, period ->
                val expenses = transactions.filter { !it.isIncome }
                val (startDate, endDate) = getDateRange(period)
                val (prevStart, prevEnd) = getPreviousDateRange(period)

                val filteredTransactions = expenses.filter { it.date in startDate..endDate }
                val previousTransactions = expenses.filter { it.date in prevStart..prevEnd }

                val groupedByCategory = filteredTransactions
                    .groupBy { it.category }

                val categoryTotals = groupedByCategory
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                    .toMap()

                val categoryTransactions = categoryTotals.keys.associateWith { cat ->
                    (groupedByCategory[cat] ?: emptyList()).sortedByDescending { it.date }
                }

                val dailyTotals = filteredTransactions
                    .groupBy { it.date.toLocalDate().toString() }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                    .map { DailySpending(it.key, it.value) }
                    .sortedBy { it.date }

                val totalSpend = filteredTransactions.sumOf { it.amount }
                val previousTotal = previousTransactions.sumOf { it.amount }
                val monthlyTotal = expenses.filter { it.date >= startOfMonth }.sumOf { it.amount }
                val weeklyTotal = expenses.filter { it.date >= startOfWeek }.sumOf { it.amount }
                val todayTotal = expenses.filter { it.date >= startOfToday }.sumOf { it.amount }

                val trendPercentage = if (previousTotal > 0) {
                    ((totalSpend - previousTotal) / previousTotal) * 100
                } else 0.0

                val spendTrend = when {
                    trendPercentage > 2 -> SpendTrend.UP
                    trendPercentage < -2 -> SpendTrend.DOWN
                    else -> SpendTrend.FLAT
                }

                val topEntry = categoryTotals.entries.maxByOrNull { it.value }
                // Counted the same way the chart counts its columns, so "Daily
                // Avg" is the average of the bars above it rather than of some
                // other span.
                val daysInPeriod = elapsedDays(startDate, endDate)

                val insights = insightsService.generateInsights(transactions)

                AnalyticsUiState(
                    totalSpend = totalSpend,
                    previousPeriodSpend = previousTotal,
                    spendTrend = spendTrend,
                    trendPercentage = trendPercentage,
                    categoryBreakdown = categoryTotals,
                    dailySpending = dailyTotals,
                    selectedPeriod = period,
                    baseCurrency = preferences.baseCurrency,
                    todaySpend = todayTotal,
                    weeklySpend = weeklyTotal,
                    monthlySpend = monthlyTotal,
                    topCategory = topEntry?.key ?: "",
                    topCategoryAmount = topEntry?.value ?: 0.0,
                    transactionCount = filteredTransactions.size,
                    dailyAverage = totalSpend / daysInPeriod,
                    insights = insights,
                    categoryTransactions = categoryTransactions,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onPeriodChange(period: ReportPeriod) {
        _selectedPeriod.value = period
    }

    /**
     * The window the screen reports on, always starting at midnight and ending
     * at the current moment.
     *
     * Weekly is exactly seven calendar days. It used to be "seven days ago at
     * midnight until now", which straddles eight dates — the total at the top
     * of the screen therefore included a day the chart had no column for.
     */
    private fun getDateRange(period: ReportPeriod): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        return when (period) {
            ReportPeriod.WEEKLY -> now.toLocalDate().minusDays(6).atStartOfDay() to now
            ReportPeriod.MONTHLY -> {
                val start = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                start to now
            }
            ReportPeriod.YEARLY -> {
                val start = now.with(TemporalAdjusters.firstDayOfYear()).withHour(0).withMinute(0)
                start to now
            }
        }
    }

    /**
     * The window the trend arrow compares against: the same number of days,
     * ending the day before the current window starts.
     *
     * Comparing a whole previous month against three days of this one is what
     * makes the trend chip read "-87%" on the 3rd of every month. Matching the
     * lengths keeps the percentage a statement about the same amount of time.
     */
    private fun getPreviousDateRange(period: ReportPeriod): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        val (start, end) = getDateRange(period)
        val days = elapsedDays(start, end)
        val prevStart = when (period) {
            ReportPeriod.WEEKLY -> start.minusDays(days.toLong())
            ReportPeriod.MONTHLY -> start.minusMonths(1)
            ReportPeriod.YEARLY -> start.minusYears(1)
        }
        return prevStart to prevStart.plusDays((days - 1).toLong())
    }

    /** Whole calendar days touched by the window, counting both ends. */
    private fun elapsedDays(start: LocalDateTime, end: LocalDateTime): Int =
        (ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) + 1).coerceAtLeast(1L).toInt()

    class Factory(private val database: ExpenseDatabase, private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyticsViewModel(
                TransactionRepository(database.transactionDao()),
                preferencesManager
            ) as T
        }
    }
}
