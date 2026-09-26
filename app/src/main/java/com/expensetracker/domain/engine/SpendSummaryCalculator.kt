package com.expensetracker.domain.engine

import com.expensetracker.data.model.Transaction
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Spend totals for the Today / This Week / This Month summary cards.
 *
 * Two rules that the cards depend on, both of which are easy to break by
 * summing the raw transaction list:
 *
 *  1. **Income is never spend.** `Transaction.amount` is a positive magnitude
 *     for credits as well as debits; the direction lives in `isIncome`. Summing
 *     a list that still contains credits adds that income into the spend total,
 *     which reads as a multiple of real spend whenever a credit lands in the
 *     same window. Only `!isIncome` rows count.
 *  2. **The three windows are independent.** They overlap by design -- today is
 *     part of both the week and the month -- but each is its own total, never a
 *     sum of the others.
 *
 * `now` is a parameter rather than an implicit `LocalDateTime.now()` so the
 * boundaries are deterministic under test.
 */
object SpendSummaryCalculator {

    fun calculate(transactions: List<Transaction>, now: LocalDateTime = LocalDateTime.now()): SpendSummary {
        val startOfToday = now.toLocalDate().atStartOfDay()
        val startOfWeek = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay()
        val startOfMonth = now.toLocalDate()
            .with(TemporalAdjusters.firstDayOfMonth())
            .atStartOfDay()

        val expenses = transactions.filter { !it.isIncome }

        return SpendSummary(
            todaySpend = expenses.filter { it.date >= startOfToday }.sumOf { it.amount },
            weeklySpend = expenses.filter { it.date >= startOfWeek }.sumOf { it.amount },
            monthlySpend = expenses.filter { it.date >= startOfMonth }.sumOf { it.amount },
            todayCount = expenses.count { it.date >= startOfToday },
            weeklyCount = expenses.count { it.date >= startOfWeek },
            monthlyCount = expenses.count { it.date >= startOfMonth }
        )
    }
}

data class SpendSummary(
    val todaySpend: Double = 0.0,
    val weeklySpend: Double = 0.0,
    val monthlySpend: Double = 0.0,
    val todayCount: Int = 0,
    val weeklyCount: Int = 0,
    val monthlyCount: Int = 0
)
