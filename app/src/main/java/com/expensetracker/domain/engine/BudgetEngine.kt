package com.expensetracker.domain.engine

import com.expensetracker.data.local.dao.CategoryTotal
import com.expensetracker.data.model.Budget
import com.expensetracker.data.model.BudgetPeriod
import com.expensetracker.data.repository.BudgetRepository
import com.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class BudgetStatus(
    val budget: Budget,
    val spent: Double,
    val remaining: Double,
    val percentageUsed: Float,
    val isOverBudget: Boolean,
    val shouldAlert: Boolean
)

class BudgetEngine(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository
) {
    fun getAllBudgets(): Flow<List<Budget>> = budgetRepository.getAllBudgets()

    suspend fun getBudgetByCategory(category: String): Budget? = 
        budgetRepository.getBudgetByCategory(category)

    suspend fun insertBudget(budget: Budget): Long = 
        budgetRepository.insertBudget(budget)

    suspend fun updateBudget(budget: Budget) = 
        budgetRepository.updateBudget(budget)

    suspend fun deleteBudget(budget: Budget) = 
        budgetRepository.deleteBudget(budget)

    suspend fun getBudgetStatus(budget: Budget): BudgetStatus {
        val (start, end) = getDateRange(budget.period)
        val categoryTotals = transactionRepository.getCategoryTotals(start, end)
        val spent = categoryTotals.find { it.category == budget.category }?.total ?: 0.0
        val remaining = (budget.limit - spent).coerceAtLeast(0.0)
        val percentageUsed = if (budget.limit > 0) (spent / budget.limit).toFloat() else 0f
        val shouldAlert = percentageUsed >= budget.alertThreshold

        return BudgetStatus(
            budget = budget,
            spent = spent,
            remaining = remaining,
            percentageUsed = percentageUsed,
            isOverBudget = spent > budget.limit,
            shouldAlert = shouldAlert
        )
    }

    suspend fun getAllBudgetStatuses(): List<BudgetStatus> {
        val budgets = budgetRepository.getAllBudgets().first()
        return budgets.map { getBudgetStatus(it) }
    }

    private fun getDateRange(period: BudgetPeriod): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        return when (period) {
            BudgetPeriod.WEEKLY -> {
                val startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .toLocalDate().atStartOfDay()
                startOfWeek to now
            }
            BudgetPeriod.MONTHLY -> {
                val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
                    .toLocalDate().atStartOfDay()
                startOfMonth to now
            }
        }
    }
}
