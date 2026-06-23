package com.expensetracker.data.local.dao

import androidx.room.*
import com.expensetracker.data.model.Category
import com.expensetracker.data.model.RecurringRule
import com.expensetracker.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit OFFSET :offset")
    fun getTransactionsPaged(limit: Int, offset: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getTransactionsByDateRange(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY date DESC")
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions 
        WHERE note LIKE '%' || :escapedQuery || '%' ESCAPE '\\' 
           OR category LIKE '%' || :escapedQuery || '%' ESCAPE '\\'
        ORDER BY date DESC
        LIMIT 100
    """)
    fun searchTransactions(escapedQuery: String): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE date BETWEEN :start AND :end")
    suspend fun getTotalByDateRange(start: LocalDateTime, end: LocalDateTime): Double?

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE date BETWEEN :start AND :end AND isIncome = 0 GROUP BY category")
    suspend fun getCategoryTotals(start: LocalDateTime, end: LocalDateTime): List<CategoryTotal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int
}

data class CategoryTotal(val category: String, val total: Double)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isDefault = 1")
    fun getDefaultCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<com.expensetracker.data.model.Budget>>

    @Query("SELECT * FROM budgets WHERE category = :category")
    suspend fun getBudgetByCategory(category: String): com.expensetracker.data.model.Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: com.expensetracker.data.model.Budget): Long

    @Update
    suspend fun updateBudget(budget: com.expensetracker.data.model.Budget)

    @Delete
    suspend fun deleteBudget(budget: com.expensetracker.data.model.Budget)
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 ORDER BY nextDate ASC")
    fun getActiveRecurringRules(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules")
    fun getAllRecurringRules(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getRecurringRuleById(id: Long): RecurringRule?

    @Query("SELECT * FROM recurring_rules WHERE nextDate <= :date AND isActive = 1")
    suspend fun getDueRecurringRules(date: LocalDateTime): List<RecurringRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringRule(rule: RecurringRule): Long

    @Update
    suspend fun updateRecurringRule(rule: RecurringRule)

    @Delete
    suspend fun deleteRecurringRule(rule: RecurringRule)
}

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates")
    fun getAllRates(): Flow<List<com.expensetracker.data.model.CurrencyRate>>

    @Query("SELECT * FROM currency_rates WHERE currencyCode = :code")
    suspend fun getRateByCode(code: String): com.expensetracker.data.model.CurrencyRate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: com.expensetracker.data.model.CurrencyRate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRates(rates: List<com.expensetracker.data.model.CurrencyRate>)
}
