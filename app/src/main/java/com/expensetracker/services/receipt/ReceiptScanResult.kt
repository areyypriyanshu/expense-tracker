package com.expensetracker.services.receipt

import java.time.LocalDateTime

data class ReceiptScanResult(
    val totalAmount: Double? = null,
    val merchant: String? = null,
    val date: LocalDateTime? = null,
    val isDateAmbiguous: Boolean = false,
    val currency: String? = null,
    val categoryText: String = "",
    val rawText: String = ""
)

sealed class ReceiptOcrResult {
    data class Success(val receipt: ReceiptScanResult) : ReceiptOcrResult()
    data object NoText : ReceiptOcrResult()
    data class Failure(val message: String) : ReceiptOcrResult()
}
