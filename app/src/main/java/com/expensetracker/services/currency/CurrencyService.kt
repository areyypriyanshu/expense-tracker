package com.expensetracker.services.currency

import com.expensetracker.data.model.CurrencyRate
import com.expensetracker.data.repository.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CurrencyService(private val currencyRepository: CurrencyRepository) {
    
    fun getAllRates(): Flow<List<CurrencyRate>> = currencyRepository.getAllRates()

    suspend fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) return amount
        val rates = currencyRepository.getAllRates().first()
        return currencyRepository.convert(amount, fromCurrency, toCurrency, rates)
    }

    suspend fun getRate(currency: String): Double {
        return currencyRepository.getRateByCode(currency)?.rateToUSD ?: 1.0
    }

    companion object {
        val SUPPORTED_CURRENCIES = listOf(
            CurrencyInfo("USD", "$", "US Dollar"),
            CurrencyInfo("EUR", "€", "Euro"),
            CurrencyInfo("GBP", "£", "British Pound"),
            CurrencyInfo("INR", "₹", "Indian Rupee"),
            CurrencyInfo("JPY", "¥", "Japanese Yen"),
            CurrencyInfo("CAD", "C$", "Canadian Dollar"),
            CurrencyInfo("AUD", "A$", "Australian Dollar"),
            CurrencyInfo("CNY", "¥", "Chinese Yuan"),
            CurrencyInfo("CHF", "Fr", "Swiss Franc"),
            CurrencyInfo("SGD", "S$", "Singapore Dollar"),
            CurrencyInfo("BRL", "R$", "Brazilian Real"),
            CurrencyInfo("KRW", "₩", "South Korean Won"),
            CurrencyInfo("MXN", "$", "Mexican Peso"),
            CurrencyInfo("RUB", "₽", "Russian Ruble"),
            CurrencyInfo("ZAR", "R", "South African Rand")
        )

        fun getSymbol(currencyCode: String): String {
            return SUPPORTED_CURRENCIES.find { it.code == currencyCode }?.symbol ?: currencyCode
        }

        fun formatAmount(amount: Double, currencyCode: String): String {
            val symbol = getSymbol(currencyCode)
            return when (currencyCode) {
                "JPY", "KRW" -> "$symbol${amount.toLong()}"
                else -> "$symbol${String.format(java.util.Locale.US, "%.2f", amount)}"
            }
        }
    }
}

data class CurrencyInfo(val code: String, val symbol: String, val name: String)
