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
        assertEquals(ChatbotIntent.GetCategorySpending("food"), intent1)

        val intent2 = parser.parse("how much did i spend on travel.")
        assertEquals(ChatbotIntent.GetCategorySpending("travel"), intent2)
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
}
