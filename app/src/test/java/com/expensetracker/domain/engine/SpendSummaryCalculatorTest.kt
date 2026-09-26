package com.expensetracker.domain.engine

import com.expensetracker.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Guards the Today / This Week / This Month summary cards.
 *
 * The regression these lock down: the cards summed the raw transaction list
 * instead of expenses-only. Income rows carry a positive `amount` exactly like
 * expenses do, so a credit landing in the same window was added into "spend".
 * A ₹9,786 week with a ₹9,000 credit read as ₹18,786.35 -- the "roughly 2x"
 * report, which is really "real spend + the credit in my account".
 */
class SpendSummaryCalculatorTest {

    // Wednesday, so the week window has rows on both sides of it.
    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 16, 14, 30)
    private val startOfToday = now.toLocalDate().atStartOfDay()
    private val startOfWeek = now.toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay()
    private val startOfMonth = now.toLocalDate().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay()

    private fun expense(
        amount: Double,
        at: LocalDateTime,
        category: String = "Food"
    ) = Transaction(
        amount = amount,
        currency = "INR",
        category = category,
        date = at
    )

    private fun income(amount: Double, at: LocalDateTime) = Transaction(
        amount = amount,
        currency = "INR",
        category = "Income",
        date = at,
        isIncome = true
    )

    @Test
    fun `income is not counted as spend`() {
        val summary = SpendSummaryCalculator.calculate(
            listOf(
                expense(786.35, startOfToday.plusHours(9)),
                income(9000.00, startOfToday.plusHours(10))
            ),
            now
        )

        // 786.35 spend, not 786.35 + 9000.00.
        assertEquals(786.35, summary.todaySpend, 0.001)
        assertEquals(1, summary.todayCount)
    }

    @Test
    fun `credit in the same window does not inflate the week card`() {
        val weekExpenses = listOf(
            expense(300.00, startOfWeek),
            expense(486.00, startOfToday.plusHours(9)),
            expense(9000.00, startOfWeek.plusDays(2))
        )

        val summary = SpendSummaryCalculator.calculate(
            weekExpenses + income(9000.00, startOfToday.plusHours(11)),
            now
        )

        assertEquals(9786.00, summary.weeklySpend, 0.001)
        assertEquals(3, summary.weeklyCount)
    }

    @Test
    fun `each window is computed independently and today counts in all three`() {
        val transactions = listOf(
            expense(100.00, startOfToday.plusHours(8)),
            expense(200.00, startOfToday.plusHours(9)),
            expense(400.00, startOfWeek.plusDays(1)),
            expense(800.00, startOfMonth.plusDays(2))
        )

        val summary = SpendSummaryCalculator.calculate(transactions, now)

        assertEquals(300.00, summary.todaySpend, 0.001)
        assertEquals(700.00, summary.weeklySpend, 0.001)
        assertEquals(1500.00, summary.monthlySpend, 0.001)

        // The overlap is intentional, not double counting: today is 2 rows, the
        // week is those same 2 rows plus 1 from earlier in the week, the month
        // is all 4. Each total is its own sum over its own window.
        assertEquals(2, summary.todayCount)
        assertEquals(3, summary.weeklyCount)
        assertEquals(4, summary.monthlyCount)
    }

    @Test
    fun `rows before the window are excluded`() {
        val summary = SpendSummaryCalculator.calculate(
            listOf(
                // One second before the week starts: out of week, still in month.
                expense(999.00, startOfWeek.minusSeconds(1)),
                // One second before the month starts: out of every window.
                expense(999.00, startOfMonth.minusSeconds(1)),
                expense(50.00, startOfToday)
            ),
            now
        )

        assertEquals(50.00, summary.todaySpend, 0.001)
        assertEquals(50.00, summary.weeklySpend, 0.001)
        // Only the row that predates the week survives, the other is out of all three.
        assertEquals(1049.00, summary.monthlySpend, 0.001)
    }

    @Test
    fun `a transaction exactly on the window boundary is included`() {
        // Guards the off-by-one: the old boundaries kept seconds/nanos
        // (.withMinute(0) without .withSecond(0)), which dropped the first 59
        // seconds of each period.
        val summary = SpendSummaryCalculator.calculate(
            listOf(
                expense(10.00, startOfToday),
                expense(20.00, startOfWeek),
                expense(30.00, startOfMonth)
            ),
            now
        )

        assertEquals(60.00, summary.monthlySpend, 0.001)
        // startOfMonth is Sep 1, well before the week window opens on Sep 14,
        // so the month-opening row is not part of the week.
        assertEquals(30.00, summary.weeklySpend, 0.001)
        assertEquals(10.00, summary.todaySpend, 0.001)
    }

    @Test
    fun `empty input yields zero rather than throwing`() {
        val summary = SpendSummaryCalculator.calculate(emptyList(), now)

        assertEquals(0.0, summary.todaySpend, 0.001)
        assertEquals(0.0, summary.weeklySpend, 0.001)
        assertEquals(0.0, summary.monthlySpend, 0.001)
    }

    @Test
    fun `income-only period reports zero spend`() {
        val summary = SpendSummaryCalculator.calculate(
            listOf(income(45000.00, startOfMonth.plusDays(1))),
            now
        )

        assertEquals(0.0, summary.todaySpend, 0.001)
        assertEquals(0.0, summary.weeklySpend, 0.001)
        assertEquals(0.0, summary.monthlySpend, 0.001)
    }
}
