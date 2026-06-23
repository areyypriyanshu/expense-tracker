package com.expensetracker.domain.engine

import com.expensetracker.data.local.dao.CategoryTotal
import com.expensetracker.data.model.RecurringFrequency
import com.expensetracker.data.model.RecurringRule
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.RecurringRuleRepository
import com.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class ReportData(
    val period: ReportPeriod,
    val total: Double,
    val categoryBreakdown: List<CategoryTotal>,
    val transactionCount: Int,
    val dailyTotals: List<DailyTotal>
)

data class DailyTotal(val date: String, val total: Double)

enum class ReportPeriod {
    WEEKLY, MONTHLY, YEARLY
}

class ReportEngine(
    private val transactionRepository: TransactionRepository
) {
    suspend fun generateReport(period: ReportPeriod): ReportData {
        val (start, end) = getDateRange(period)
        val transactions = transactionRepository.getTransactionsByDateRange(start, end)
        val categoryTotals = transactionRepository.getCategoryTotals(start, end)
        val total = transactionRepository.getTotalByDateRange(start, end)

        val dailyTotals = calculateDailyTotals(start, end, transactions)

        return ReportData(
            period = period,
            total = total,
            categoryBreakdown = categoryTotals,
            transactionCount = transactions.let { flow ->
                var count = 0
                flow.collect { count = it.size }
                count
            },
            dailyTotals = dailyTotals
        )
    }

    suspend fun getWeeklyReport(): ReportData = generateReport(ReportPeriod.WEEKLY)

    suspend fun getMonthlyReport(): ReportData = generateReport(ReportPeriod.MONTHLY)

    private fun getDateRange(period: ReportPeriod): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        return when (period) {
            ReportPeriod.WEEKLY -> {
                val start = now.minusDays(7)
                start to now
            }
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

    private suspend fun calculateDailyTotals(
        start: LocalDateTime,
        end: LocalDateTime,
        transactionsFlow: Flow<List<Transaction>>
    ): List<DailyTotal> {
        val transactions = mutableListOf<Transaction>()
        transactionsFlow.collect { transactions.addAll(it) }
        
        val dailyMap = mutableMapOf<LocalDate, Double>()
        
        transactions.forEach { tx ->
            val date = tx.date.toLocalDate()
            dailyMap[date] = (dailyMap[date] ?: 0.0) + tx.amount
        }
        
        val days = java.time.Duration.between(start, end).toDays().toInt() + 1
        return (0 until days).map { day ->
            val date = start.plusDays(day.toLong()).toLocalDate()
            DailyTotal(
                date = date.toString(),
                total = dailyMap[date] ?: 0.0
            )
        }
    }
}

class RecurringEngine(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository
) {
    fun getActiveRecurringRules(): Flow<List<RecurringRule>> = 
        recurringRuleRepository.getActiveRecurringRules()

    fun getAllRecurringRules(): Flow<List<RecurringRule>> = 
        recurringRuleRepository.getAllRecurringRules()

    suspend fun getRecurringRuleById(id: Long): RecurringRule? = 
        recurringRuleRepository.getRecurringRuleById(id)

    suspend fun insertRecurringRule(rule: RecurringRule): Long = 
        recurringRuleRepository.insertRecurringRule(rule)

    suspend fun updateRecurringRule(rule: RecurringRule) = 
        recurringRuleRepository.updateRecurringRule(rule)

    suspend fun deleteRecurringRule(rule: RecurringRule) = 
        recurringRuleRepository.deleteRecurringRule(rule)

    suspend fun processDueRecurringRules() {
        val dueRules = recurringRuleRepository.getDueRecurringRules()
        val existingTransactions = transactionRepository.getAllTransactions()
        
        val existingKeys = mutableSetOf<String>()
        existingTransactions.collect { txs ->
            txs.filter { it.isRecurring && it.recurringRuleId != null }
                .forEach { tx ->
                    existingKeys.add("${tx.recurringRuleId}_${tx.date.toLocalDate()}")
                }
        }
        
        for (rule in dueRules) {
            val transactionKey = "${rule.id}_${rule.nextDate.toLocalDate()}"
            
            if (existingKeys.contains(transactionKey)) {
                continue
            }
            
            val transaction = Transaction(
                amount = rule.amount,
                currency = rule.currency,
                category = rule.category,
                note = rule.note,
                date = rule.nextDate,
                isRecurring = true,
                recurringRuleId = rule.id
            )
            transactionRepository.insertTransaction(transaction)
            
            val nextDate = calculateNextDate(rule.nextDate, rule.frequency)
            recurringRuleRepository.updateRecurringRule(rule.copy(nextDate = nextDate))
        }
    }

    private fun calculateNextDate(currentDate: LocalDateTime, frequency: RecurringFrequency): LocalDateTime {
        return when (frequency) {
            RecurringFrequency.DAILY -> currentDate.plusDays(1)
            RecurringFrequency.WEEKLY -> currentDate.plusWeeks(1)
            RecurringFrequency.MONTHLY -> currentDate.plusMonths(1)
            RecurringFrequency.YEARLY -> currentDate.plusYears(1)
        }
    }
}
