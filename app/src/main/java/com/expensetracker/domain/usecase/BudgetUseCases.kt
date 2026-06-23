package com.expensetracker.domain.usecase

import com.expensetracker.data.model.Budget
import com.expensetracker.data.model.BudgetPeriod
import com.expensetracker.data.repository.BudgetRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.engine.BudgetEngine
import com.expensetracker.domain.engine.BudgetStatus
import com.expensetracker.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

class AddBudgetUseCase(
    private val budgetRepository: BudgetRepository
) {
    suspend operator fun invoke(
        category: String,
        limit: Double,
        currency: String,
        period: BudgetPeriod,
        threshold: Float
    ): Result<Long> {
        return try {
            if (limit <= 0) {
                return Result.Error("Budget limit must be greater than zero.")
            }
            
            if (threshold < 0 || threshold > 1) {
                return Result.Error("Alert threshold must be between 0 and 1 (0% to 100%).")
            }
            
            val budget = Budget(
                category = category,
                limit = limit,
                currency = currency,
                period = period,
                alertThreshold = threshold
            )
            
            val id = budgetRepository.insertBudget(budget)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error("Failed to add budget", e)
        }
    }
}

class GetBudgetsUseCase(
    private val budgetRepository: BudgetRepository
) {
    operator fun invoke(): Flow<List<Budget>> {
        return budgetRepository.getAllBudgets()
    }
}

class GetBudgetStatusesUseCase(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(): List<BudgetStatus> {
        val budgetEngine = BudgetEngine(budgetRepository, transactionRepository)
        val budgets = budgetRepository.getAllBudgets().first()
        return budgets.map { budget ->
            budgetEngine.getBudgetStatus(budget)
        }
    }
}

class DeleteBudgetUseCase(
    private val budgetRepository: BudgetRepository
) {
    suspend operator fun invoke(budget: Budget): Result<Unit> {
        return try {
            budgetRepository.deleteBudget(budget)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to delete budget", e)
        }
    }
}

class CheckBudgetAlertUseCase(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(): List<BudgetAlert> {
        val budgetEngine = BudgetEngine(budgetRepository, transactionRepository)
        val budgets = budgetRepository.getAllBudgets()
        val alerts = mutableListOf<BudgetAlert>()
        
        budgets.collect { budgetList ->
            for (budget in budgetList) {
                val status = budgetEngine.getBudgetStatus(budget)
                if (status.percentageUsed >= budget.alertThreshold) {
                    alerts.add(
                        BudgetAlert(
                            budget = budget,
                            percentageUsed = status.percentageUsed,
                            isOverBudget = status.isOverBudget,
                            message = if (status.isOverBudget) {
                                "You've exceeded your ${budget.category} budget!"
                            } else {
                                "You've used ${(status.percentageUsed * 100).toInt()}% of your ${budget.category} budget."
                            }
                        )
                    )
                }
            }
        }
        
        return alerts
    }
}

data class BudgetAlert(
    val budget: Budget,
    val percentageUsed: Float,
    val isOverBudget: Boolean,
    val message: String
)
