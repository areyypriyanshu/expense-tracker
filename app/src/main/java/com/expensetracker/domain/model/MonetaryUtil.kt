package com.expensetracker.domain.model

import com.expensetracker.services.currency.CurrencyService

object MonetaryUtil {
    private const val ROUNDING_SCALE = 2
    
    fun formatAmount(amount: Double, currencyCode: String): String {
        return CurrencyService.formatAmount(amount, currencyCode)
    }
    
    fun getSymbol(currencyCode: String): String {
        return CurrencyService.getSymbol(currencyCode)
    }
    
    fun add(vararg amounts: Double): Double = amounts.sum()
    
    fun percentage(amount: Double, total: Double): Double {
        return if (total == 0.0) 0.0 else (amount / total) * 100
    }
    
    fun isValidAmount(amount: Double): Boolean {
        return amount > 0 && amount < 1_000_000_000
    }
}
