package com.expensetracker.domain.model

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object MonetaryUtil {
    private const val ROUNDING_SCALE = 2
    
    fun formatAmount(amount: Double, currencyCode: String): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            format.currency = Currency.getInstance(currencyCode)
            format.format(amount)
        } catch (e: Exception) {
            "$currencyCode %.2f".format(amount)
        }
    }
    
    fun formatAmountCompact(amount: Double, currencyCode: String): String {
        val symbol = getSymbol(currencyCode)
        return when {
            currencyCode == "INR" && amount >= 1_00_000 -> "${symbol}%.1fL".format(amount / 1_00_000)
            currencyCode != "INR" && amount >= 1_000_000 -> "${symbol}%.1fM".format(amount / 1_000_000)
            amount >= 1_000 -> "${symbol}%.1fK".format(amount / 1_000)
            else -> "${symbol}%.0f".format(amount)
        }
    }
    
    fun getSymbol(currencyCode: String): String {
        return when (currencyCode) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "CNY" -> "¥"
            "AUD" -> "A$"
            "CAD" -> "C$"
            "CHF" -> "CHF"
            "SGD" -> "S$"
            "AED" -> "AED"
            "SAR" -> "SAR"
            "BRL" -> "R$"
            "MXN" -> "MX$"
            "KRW" -> "₩"
            else -> currencyCode
        }
    }
    
    fun add(vararg amounts: Double): Double = amounts.sum()
    
    fun subtract(a: Double, b: Double): Double = a - b
    
    fun percentage(amount: Double, total: Double): Double {
        return if (total == 0.0) 0.0 else (amount / total) * 100
    }
    
    fun safeParse(input: String): Double? {
        return input
            .replace(Regex("[^\\d.-]"), "")
            .toDoubleOrNull()
    }
    
    fun isValidAmount(amount: Double): Boolean {
        return amount > 0 && amount < 1_000_000_000
    }
}
