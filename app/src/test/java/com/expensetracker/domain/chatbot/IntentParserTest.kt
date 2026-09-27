package com.expensetracker.domain.chatbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IntentParserTest {

    private val parser = IntentParser()

    @Test
    fun testParseTodaySpending() {
        val intent1 = parser.parse("How much did I spend today?")
        assertEquals(ChatbotIntent.GetTodaySpending, intent1)

        val intent2 = parser.parse("What did I spend today?")
        assertEquals(ChatbotIntent.GetTodaySpending, intent2)

        // Verify today is not mapped to total spending
        assertNotEquals(ChatbotIntent.GetTotalSpending, intent1)
    }

    @Test
    fun testParseYesterdaySpending() {
        val intent = parser.parse("How much did I spend yesterday?")
        assertEquals(ChatbotIntent.GetYesterdaySpending, intent)
    }

    @Test
    fun testParseMonthlySpending() {
        val intent = parser.parse("How much did I spend this month?")
        assertEquals(ChatbotIntent.GetMonthlySpending(), intent)
    }

    @Test
    fun testParseCategorySpendingWithPunctuation() {
        val intent1 = parser.parse("How much did I spend on Food?")
        assertEquals(ChatbotIntent.GetCategorySpending("Food & Dining"), intent1)

        val intent2 = parser.parse("how much did i spend on travel.")
        assertEquals(ChatbotIntent.GetCategorySpending("Travel"), intent2)
    }

    @Test
    fun testParseTotalSpending() {
        val intent = parser.parse("What is my total spending?")
        assertEquals(ChatbotIntent.GetTotalSpending, intent)
    }

    @Test
    fun testParseHighestExpense() {
        val intent = parser.parse("What was my biggest expense?")
        assertEquals(ChatbotIntent.GetHighestExpense, intent)
    }

    @Test
    fun testParseRecentTransactions() {
        val intent = parser.parse("Show my recent transactions")
        assertEquals(ChatbotIntent.GetRecentTransactions(5), intent)
    }

    @Test
    fun testParseBudgetStatus() {
        val intent = parser.parse("How much budget is left?")
        assertEquals(ChatbotIntent.GetBudgetStatus, intent)
    }

    @Test
    fun testParseSpendingSummary() {
        val intent = parser.parse("Give me a spending summary")
        assertEquals(ChatbotIntent.GetSpendingSummary, intent)
    }

    @Test
    fun testParseHelp() {
        val intent = parser.parse("help")
        assertEquals(ChatbotIntent.Help, intent)
    }

    @Test
    fun testParseUnknown() {
        val intent = parser.parse("random query xyz")
        assertEquals(ChatbotIntent.Unknown, intent)
    }

    @Test
    fun testParseCategorySpendingNaturalPhrasings() {
        assertEquals(ChatbotIntent.GetCategorySpending("Food & Dining"), parser.parse("How much did I spend on food?"))
        assertEquals(ChatbotIntent.GetCategorySpending("Food & Dining"), parser.parse("What did I spend on food?"))
        assertEquals(ChatbotIntent.GetCategorySpending("Food & Dining"), parser.parse("Food expenses?"))
        assertEquals(ChatbotIntent.GetCategorySpending("Food & Dining"), parser.parse("Show my food spending"))
        assertEquals(ChatbotIntent.GetCategorySpending("Travel"), parser.parse("Travel expenses?"))
    }

    @Test
    fun testParseYesterdaySpendingNaturalPhrasings() {
        assertEquals(ChatbotIntent.GetYesterdaySpending, parser.parse("How much did I spend yesterday?"))
        assertEquals(ChatbotIntent.GetYesterdaySpending, parser.parse("Yesterday spending?"))
    }

    @Test
    fun testParseThisWeekSpending() {
        assertEquals(ChatbotIntent.GetWeeklySpending, parser.parse("How much did I spend this week?"))
        assertEquals(ChatbotIntent.GetWeeklySpending, parser.parse("Weekly spending"))
    }

    @Test
    fun testParseLastWeekSpending() {
        assertEquals(ChatbotIntent.GetLastWeekSpending, parser.parse("How much did I spend last week?"))
        assertEquals(ChatbotIntent.GetLastWeekSpending, parser.parse("Last week expenses"))
    }

    @Test
    fun testParseThisMonthSpending() {
        assertEquals(ChatbotIntent.GetMonthlySpending(), parser.parse("How much did I spend this month?"))
        assertEquals(ChatbotIntent.GetMonthlySpending(), parser.parse("This month spending"))
    }

    @Test
    fun testParseLastMonthSpending() {
        assertEquals(ChatbotIntent.GetLastMonthSpending, parser.parse("How much did I spend last month?"))
        assertEquals(ChatbotIntent.GetLastMonthSpending, parser.parse("Last month expenses"))
    }

    @Test
    fun testParseLowestExpense() {
        assertEquals(ChatbotIntent.GetLowestExpense, parser.parse("What is my lowest expense?"))
        assertEquals(ChatbotIntent.GetLowestExpense, parser.parse("Smallest expense"))
        assertEquals(ChatbotIntent.GetLowestExpense, parser.parse("Minimum spending"))
    }

    @Test
    fun testParseRecentTransactionsNaturalPhrasings() {
        assertEquals(ChatbotIntent.GetRecentTransactions(5), parser.parse("Show my recent transactions"))
        assertEquals(ChatbotIntent.GetRecentTransactions(5), parser.parse("Latest expenses"))
        assertEquals(ChatbotIntent.GetRecentTransactions(3), parser.parse("Show last 3 transactions"))
    }

    @Test
    fun testParseCategoryTransactions() {
        assertEquals(ChatbotIntent.GetCategoryTransactions("Food & Dining"), parser.parse("Show transactions for food"))
        assertEquals(ChatbotIntent.GetCategoryTransactions("Travel"), parser.parse("Travel transactions?"))
    }

    @Test
    fun testParseExpensesAboveAmount() {
        assertEquals(ChatbotIntent.GetExpensesAboveAmount(100.0), parser.parse("Expenses above 100"))
        assertEquals(ChatbotIntent.GetExpensesAboveAmount(50.5), parser.parse("Spending over 50.5"))
        assertEquals(ChatbotIntent.GetExpensesAboveAmount(25.0), parser.parse("What did I spend above 25?"))
    }

    @Test
    fun testParseBudgetStatusNaturalPhrasings() {
        assertEquals(ChatbotIntent.GetBudgetStatus, parser.parse("What is my budget status?"))
        assertEquals(ChatbotIntent.GetBudgetStatus, parser.parse("How is my budget?"))
        assertEquals(ChatbotIntent.GetBudgetStatus, parser.parse("Budget overview"))
    }

    @Test
    fun testParseCategoryBudgetStatus() {
        assertEquals(ChatbotIntent.GetCategoryBudgetStatus("Food & Dining"), parser.parse("Food budget status"))
        assertEquals(ChatbotIntent.GetCategoryBudgetStatus("Travel"), parser.parse("How is my travel budget?"))
    }

    @Test
    fun testParseSpendingSummaryNaturalPhrasings() {
        assertEquals(ChatbotIntent.GetSpendingSummary, parser.parse("Give me a spending summary"))
        assertEquals(ChatbotIntent.GetSpendingSummary, parser.parse("Overview of my spending"))
        assertEquals(ChatbotIntent.GetSpendingSummary, parser.parse("Spending breakdown"))
    }

    @Test
    fun testParseAverageDailySpending() {
        assertEquals(ChatbotIntent.GetAverageDailySpending, parser.parse("Average daily spending"))
        assertEquals(ChatbotIntent.GetAverageDailySpending, parser.parse("Daily average spend"))
        assertEquals(ChatbotIntent.GetAverageDailySpending, parser.parse("What is my average daily spending?"))
    }

    @Test
    fun testParseTotalIncome() {
        assertEquals(ChatbotIntent.GetTotalIncome, parser.parse("What is my total income?"))
        assertEquals(ChatbotIntent.GetTotalIncome, parser.parse("Total earnings"))
        assertEquals(ChatbotIntent.GetTotalIncome, parser.parse("How much did I earn?"))
    }

    @Test
    fun testParseNetBalance() {
        assertEquals(ChatbotIntent.GetNetBalance, parser.parse("What is my net balance?"))
        assertEquals(ChatbotIntent.GetNetBalance, parser.parse("How much is left?"))
        assertEquals(ChatbotIntent.GetNetBalance, parser.parse("Balance"))
    }

    @Test
    fun testParseCompareMonths() {
        assertEquals(ChatbotIntent.CompareMonths, parser.parse("Did I spend more this month than last month?"))
        assertEquals(ChatbotIntent.CompareMonths, parser.parse("Compare this month with last month"))
    }

    @Test
    fun testCompareThisWeekVsLastWeek() {
        assertEquals(ChatbotIntent.CompareWeeks, parser.parse("Compare this week with last week."))
    }

    @Test
    fun testCompareThisYearVsLastYear() {
        assertEquals(ChatbotIntent.CompareYears, parser.parse("Compare this year with last year"))
    }

    @Test
    fun testCompareSamePeriodLastMonth() {
        assertEquals(ChatbotIntent.CompareSamePeriodLastMonth, parser.parse("Same period last month"))
    }

    @Test
    fun testCompareSamePeriodLastYear() {
        assertEquals(ChatbotIntent.CompareSamePeriodLastYear, parser.parse("Same period last year"))
    }

    @Test
    fun testCompareLast7Days() {
        assertEquals(ChatbotIntent.CompareLast7Days, parser.parse("Last 7 days"))
    }

    @Test
    fun testCompareLast30Days() {
        assertEquals(ChatbotIntent.CompareLast30Days, parser.parse("Compare last 30 days"))
    }

    @Test
    fun testParseSpendingTrendInsight() {
        assertEquals(ChatbotIntent.GetSpendingTrendInsight, parser.parse("Am I spending more than usual?"))
        assertEquals(ChatbotIntent.GetSpendingTrendInsight, parser.parse("Give me a spending trend"))
    }

    @Test
    fun testFollowUpLastMonthCategory() {
        // Context-based follow-up resolved by ViewModel, not parser alone
        assertEquals(ChatbotIntent.GetCategorySpending("Food & Dining"), parser.parse("Food spending"))
    }

    @Test
    fun testParseHelpNaturalPhrasings() {
        assertEquals(ChatbotIntent.Help, parser.parse("help"))
        assertEquals(ChatbotIntent.Help, parser.parse("What can you do?"))
        assertEquals(ChatbotIntent.Help, parser.parse("commands"))
    }

    // ── Investment ──────────────────────────────────────────────────────

    /**
     * The phrasings people actually use.
     *
     * Each of these is worth a line of its own because every one of them is
     * claimed by a later rule if the investment rule is not asked first:
     * "total investment" matches total-spending on "total", "recent
     * investments" matches recent-transactions on "recent", "show my
     * investments" matches show-transactions on "show", and "investments this
     * month" matches monthly-spending on "month". Those rules are not wrong
     * about their own wording — they are just about a different subject, and
     * the one that knows the subject has to come first.
     */
    @Test
    fun testParseInvestmentSummary() {
        listOf(
            "how much have i invested",
            "what is my total investment",
            "show my recent investments",
            "investments this month",
            "how much did I put into mutual fund",
            "what about my SIP",
            "tell me about my portfolio",
            "how much is in stocks"
        ).forEach { query ->
            assertEquals(
                "\"$query\" is an investment question",
                ChatbotIntent.GetInvestmentSummary,
                parser.parse(query)
            )
        }
    }

    /**
     * "Interest" is interest on a balance — income, not an investment. The
     * whole difference is the word boundary: "interest" contains the letters of
     * "invest", and a substring match would silently answer a question about
     * bank interest with a savings-rate report.
     */
    @Test
    fun testInterestIsIncomeNotInvestment() {
        assertNotEquals(ChatbotIntent.GetInvestmentSummary, parser.parse("how much interest did I earn"))
        assertEquals(ChatbotIntent.GetTotalIncome, parser.parse("how much interest did I earn"))
    }

    /** The new rule must not have cost any existing intent its own phrasings. */
    @Test
    fun testInvestmentRuleDidNotStealExistingIntents() {
        assertEquals(ChatbotIntent.GetTodaySpending, parser.parse("how much did I spend today"))
        assertEquals(ChatbotIntent.GetTotalSpending, parser.parse("what is my total spending"))
        assertEquals(ChatbotIntent.GetRecentTransactions(5), parser.parse("show recent transactions"))
        assertEquals(ChatbotIntent.GetMonthlySpending(), parser.parse("how much did I spend this month"))
        assertEquals(
            ChatbotIntent.GetCategorySpending("Food & Dining"),
            parser.parse("how much spent on food")
        )
    }

    /**
     * "Compare Food and Shopping" is an example in the help text, so it has to
     * reach the branch that actually compares categories. The category-spending
     * rule claims any sentence containing "food", and it used to be asked
     * first, so this arrived as "what did you spend on Food & Dining".
     */
    @Test
    fun testCompareTwoCategories() {
        val intent = parser.parse("compare Food and Shopping")
        assertEquals(ChatbotIntent.CompareCategories("Food & Dining", "Shopping"), intent)
    }

    /** Moving that rule up must not cost the plain category question. */
    @Test
    fun testPlainCategorySpendingStillResolves() {
        assertEquals(
            ChatbotIntent.GetCategorySpending("Food & Dining"),
            parser.parse("how much did I spend on Food")
        )
        assertEquals(
            ChatbotIntent.GetCategorySpending("Shopping"),
            parser.parse("how much spent on shopping")
        )
    }
}
