package com.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val currency: String,
    val convertedAmount: Double? = null,
    val category: String,
    val note: String = "",
    val date: LocalDateTime = LocalDateTime.now(),
    val isRecurring: Boolean = false,
    val recurringRuleId: Long? = null,
    val isIncome: Boolean = false
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,
    val keywords: String = "",
    val isDefault: Boolean = false
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val limit: Double,
    val currency: String,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val alertThreshold: Float = 0.8f
)

enum class BudgetPeriod {
    WEEKLY, MONTHLY
}

@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val currency: String,
    val category: String,
    val note: String,
    val frequency: RecurringFrequency,
    val nextDate: LocalDateTime,
    val isActive: Boolean = true
)

enum class RecurringFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

@Entity(tableName = "currency_rates")
data class CurrencyRate(
    @PrimaryKey
    val currencyCode: String,
    val rateToUSD: Double,
    val lastUpdated: LocalDateTime = LocalDateTime.now()
)

data class UserPreference(
    val baseCurrency: String = "USD",
    val theme: String = "system"
)
