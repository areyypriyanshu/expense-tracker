package com.expensetracker.data.repository

import com.expensetracker.data.local.dao.*
import com.expensetracker.data.model.*
import com.expensetracker.domain.engine.CategoryEngine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

class TransactionRepository(private val transactionDao: TransactionDao) {
    
    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionsPaged(page: Int): Flow<List<Transaction>> {
        val safePage = page.coerceAtLeast(0)
        val offset = safePage * MAX_PAGE_SIZE
        return transactionDao.getTransactionsPaged(MAX_PAGE_SIZE, offset)
    }

    suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getTransactionById(id)

    fun getTransactionsByDateRange(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(start, end)

    fun getTransactionsByCategory(category: String): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(category)

    fun searchTransactions(query: String): Flow<List<Transaction>> {
        val sanitized = sanitizeSearchQuery(query)
        return transactionDao.searchTransactions(sanitized)
    }

    private fun sanitizeSearchQuery(query: String): String {
        val trimmed = query.trim().take(100)
        if (trimmed.isEmpty()) return ""
        return trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    suspend fun getTotalByDateRange(start: LocalDateTime, end: LocalDateTime): Double =
        transactionDao.getTotalByDateRange(start, end) ?: 0.0

    suspend fun getCategoryTotals(start: LocalDateTime, end: LocalDateTime): List<CategoryTotal> =
        transactionDao.getCategoryTotals(start, end)

    suspend fun insertTransaction(transaction: Transaction): Long {
        val validatedTransaction = validateTransaction(transaction)
        return transactionDao.insertTransaction(validatedTransaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        val validatedTransaction = validateTransaction(transaction)
        transactionDao.updateTransaction(validatedTransaction)
    }

    private fun validateTransaction(transaction: Transaction): Transaction {
        return transaction.copy(
            note = transaction.note.take(500),
            category = sanitizeCategory(transaction.category)
        )
    }

    private fun sanitizeCategory(category: String): String {
        return if (CategoryEngine.isSupportedCategory(category)) category else CategoryEngine.FALLBACK_CATEGORY
    }

    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.deleteTransaction(transaction)

    suspend fun deleteTransactionById(id: Long) =
        transactionDao.deleteTransactionById(id)

    suspend fun getMonthlyTotal(): Double {
        val now = LocalDateTime.now()
        val start = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
        return getTotalByDateRange(start, now)
    }

    suspend fun getWeeklyTotal(): Double {
        val now = LocalDateTime.now()
        val start = now.minusDays(now.dayOfWeek.value.toLong() - 1)
            .toLocalDate().atStartOfDay()
        return getTotalByDateRange(start, now)
    }

    suspend fun getTransactionCount(): Int = transactionDao.getTransactionCount()
}

class CategoryRepository(private val categoryDao: CategoryDao) {
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    fun getDefaultCategories(): Flow<List<Category>> = categoryDao.getDefaultCategories()

    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)

    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)

    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)
}

class BudgetRepository(private val budgetDao: BudgetDao) {
    fun getAllBudgets(): Flow<List<Budget>> = budgetDao.getAllBudgets()

    suspend fun getBudgetByCategory(category: String): Budget? = budgetDao.getBudgetByCategory(category)

    suspend fun insertBudget(budget: Budget): Long = budgetDao.insertBudget(budget)

    suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)

    suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)
}

class RecurringRuleRepository(private val recurringRuleDao: RecurringRuleDao) {
    fun getActiveRecurringRules(): Flow<List<RecurringRule>> = recurringRuleDao.getActiveRecurringRules()

    fun getAllRecurringRules(): Flow<List<RecurringRule>> = recurringRuleDao.getAllRecurringRules()

    suspend fun getRecurringRuleById(id: Long): RecurringRule? = recurringRuleDao.getRecurringRuleById(id)

    suspend fun getDueRecurringRules(date: LocalDateTime = LocalDateTime.now()): List<RecurringRule> =
        recurringRuleDao.getDueRecurringRules(date)

    suspend fun insertRecurringRule(rule: RecurringRule): Long = recurringRuleDao.insertRecurringRule(rule)

    suspend fun updateRecurringRule(rule: RecurringRule) = recurringRuleDao.updateRecurringRule(rule)

    suspend fun deleteRecurringRule(rule: RecurringRule) = recurringRuleDao.deleteRecurringRule(rule)
}

class CurrencyRepository(private val currencyRateDao: CurrencyRateDao) {
    fun getAllRates(): Flow<List<CurrencyRate>> = currencyRateDao.getAllRates()

    suspend fun getRateByCode(code: String): CurrencyRate? = currencyRateDao.getRateByCode(code)

    suspend fun insertRate(rate: CurrencyRate) = currencyRateDao.insertRate(rate)

    suspend fun insertRates(rates: List<CurrencyRate>) = currencyRateDao.insertRates(rates)

    suspend fun convert(amount: Double, fromCurrency: String, toCurrency: String, rates: List<CurrencyRate>): Double {
        if (fromCurrency == toCurrency) return amount
        val fromRateObj = rates.find { it.currencyCode == fromCurrency }
        val toRateObj = rates.find { it.currencyCode == toCurrency }
        
        val fromRate = fromRateObj?.rateToUSD
        val toRate = toRateObj?.rateToUSD
        
        if (fromRate == null || fromRate <= 0 || toRate == null || toRate <= 0) {
            return amount
        }

        // Warn if rates are stale (older than 7 days)
        val weekAgo = java.time.LocalDateTime.now().minusDays(7)
        if (fromRateObj.lastUpdated.isBefore(weekAgo) || toRateObj.lastUpdated.isBefore(weekAgo)) {
            android.util.Log.w("CurrencyRepo", "Stale exchange rate detected (>7 days old)")
        }
        
        return Math.round(amount / fromRate * toRate * 100.0) / 100.0
    }
}
