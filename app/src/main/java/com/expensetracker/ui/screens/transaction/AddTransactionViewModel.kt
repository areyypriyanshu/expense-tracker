package com.expensetracker.ui.screens.transaction

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.model.Category
import com.expensetracker.data.model.RecurringFrequency
import com.expensetracker.data.model.RecurringRule
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.RecurringRuleRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.engine.CategoryEngine
import com.expensetracker.domain.model.MonetaryUtil
import com.expensetracker.domain.model.UiState
import com.expensetracker.services.receipt.ReceiptOcrResult
import com.expensetracker.services.receipt.ReceiptOcrService
import com.expensetracker.services.receipt.ReceiptScanResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime

data class AddTransactionUiState(
    val id: Long? = null,
    val amount: String = "",
    val currency: String = "INR",
    val category: String = "Other",
    val note: String = "",
    val date: LocalDateTime = LocalDateTime.now(),
    val categories: List<Category> = emptyList(),
    val isRecurring: Boolean = false,
    val recurringFrequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val isIncome: Boolean = false,
    val uiState: UiState<Unit> = UiState.Success(Unit),
    val error: String? = null,
    val isScanningReceipt: Boolean = false,
    val receiptScanMessage: String? = null,
    val receiptScanIsError: Boolean = false,
    val receiptReviewPending: Boolean = false
) {
    val isLoading: Boolean get() = uiState is UiState.Loading
    val isSaved: Boolean get() = uiState is UiState.Success && error == null
}

private data class ReceiptFormSnapshot(
    val amount: String,
    val currency: String,
    val category: String,
    val note: String,
    val date: LocalDateTime,
    val error: String?
)

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val receiptOcrService: ReceiptOcrService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private val categoryEngine = CategoryEngine()
    private var formVersion = 0L
    private var latestScanRequestId = 0L
    private var latestLoadRequestId = 0L
    private var reviewSnapshot: ReceiptFormSnapshot? = null
    private var pendingCategoryText: String? = null

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { state ->
                    val suggestion = pendingCategoryText
                        ?.takeIf { categories.isNotEmpty() && state.receiptReviewPending && !state.isIncome }
                        ?.let { suggestCategory(it, categories) }
                    if (suggestion != null) pendingCategoryText = null
                    state.copy(categories = categories, category = suggestion ?: state.category)
                }
            }
        }
    }

    fun loadTransaction(transactionId: Long) {
        val requestId = ++latestLoadRequestId
        val versionAtStart = formVersion
        viewModelScope.launch {
            if (requestId != latestLoadRequestId || versionAtStart != formVersion) return@launch
            _uiState.update { it.copy(uiState = UiState.Loading, error = null) }
            try {
                val transaction = transactionRepository.getTransactionById(transactionId)
                if (requestId != latestLoadRequestId || versionAtStart != formVersion) return@launch
                if (transaction != null) {
                    formVersion++
                    _uiState.update {
                        it.copy(
                            id = transaction.id,
                            amount = transaction.amount.toString(),
                            currency = transaction.currency,
                            category = transaction.category,
                            note = transaction.note,
                            date = transaction.date,
                            isIncome = transaction.isIncome,
                            uiState = UiState.Success(Unit)
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            uiState = UiState.Error("Transaction not found"),
                            error = "Transaction not found"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load transaction")
                _uiState.update { 
                    it.copy(
                        uiState = UiState.Error("Failed to load transaction"),
                        error = "Failed to load transaction"
                    )
                }
            }
        }
    }

    fun onAmountChange(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            val parts = filtered.split('.')
            val limited = if (parts.size > 1) {
                "${parts[0]}.${parts[1].take(2)}"
            } else {
                filtered
            }
            
            if (limited.length > 15) {
                return  // Prevent buffer overflow from extremely long input
            }
            
            val value = limited.toDoubleOrNull()
            if (value != null && value > MAX_SAFE_AMOUNT) {
                return  // Prevent scientific notation overflow
            }
            formVersion++
            _uiState.update { it.copy(amount = limited, error = null) }
        }
    }

    fun onCurrencyChange(currency: String) {
        val sanitizedCurrency = currency
            .take(5)
            .filter { it.isLetter() }
            .uppercase()
            .take(5)
        
        if (sanitizedCurrency.length in 3..5) {
            formVersion++
            _uiState.update { it.copy(currency = sanitizedCurrency) }
        }
    }

    fun onCategoryChange(category: String) {
        val sanitizedCategory = category
            .take(50)
            .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '&' }
            .trim()
        
        // Validate against loaded categories to prevent mismatch with repository whitelist
        val validCategories = _uiState.value.categories.map { it.name }
        val resolvedCategory = if (validCategories.isNotEmpty() && sanitizedCategory !in validCategories) {
            validCategories.find { it.equals(sanitizedCategory, ignoreCase = true) } ?: sanitizedCategory
        } else {
            sanitizedCategory
        }
        
        if (resolvedCategory.isNotBlank() && resolvedCategory.length <= 50) {
            formVersion++
            pendingCategoryText = null
            _uiState.update { it.copy(category = resolvedCategory) }
        }
    }

    fun onNoteChange(note: String) {
        val sanitizedNote = sanitizeNote(note)
        formVersion++
        _uiState.update { it.copy(note = sanitizedNote) }
    }
    
    private fun sanitizeNote(input: String): String {
        if (input.isBlank()) return ""
        
        return input
            .take(500)
            .replace(Regex("[\t\r\n]"), " ")
            .replace(Regex("<[^>]*>"), "")  // Remove HTML tags
            .replace(Regex("javascript:|on\\w+=", setOf(RegexOption.IGNORE_CASE)), "")  // Remove JS event handlers
            .replace(Regex("[<>\"'&]"), "")  // Remove dangerous characters
            .replace(Regex("\\p{C}"), "")  // Remove control characters
            .trim()
            .take(500)
    }

    fun onDateChange(date: LocalDateTime) {
        formVersion++
        _uiState.update { it.copy(date = boundDate(date)) }
    }

    fun onRecurringChange(isRecurring: Boolean) {
        formVersion++
        _uiState.update { it.copy(isRecurring = isRecurring) }
    }

    fun onRecurringFrequencyChange(frequency: RecurringFrequency) {
        formVersion++
        _uiState.update { it.copy(recurringFrequency = frequency) }
    }

    fun onIsIncomeChange(isIncome: Boolean) {
        val currentState = _uiState.value
        val newCategory = if (isIncome && !currentState.isIncome) {
            "Income"
        } else if (!isIncome && currentState.isIncome && currentState.category == "Income") {
            "Other"
        } else {
            currentState.category
        }
        formVersion++
        pendingCategoryText = null
        _uiState.update { it.copy(isIncome = isIncome, category = newCategory) }
    }

    fun autoCategorize() {
        val note = _uiState.value.note
        val categories = _uiState.value.categories
        val suggestedCategory = categoryEngine.autoCategorize(note, categories)
        formVersion++
        pendingCategoryText = null
        _uiState.update { it.copy(category = suggestedCategory) }
    }

    fun scanReceipt(uri: Uri, temporaryImage: File? = null) {
        val requestId = ++latestScanRequestId
        val versionAtStart = ++formVersion
        val snapshot = reviewSnapshot ?: _uiState.value.toReceiptSnapshot()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    uiState = UiState.Success(Unit),
                    isScanningReceipt = true,
                    receiptScanMessage = null,
                    receiptScanIsError = false
                )
            }
            try {
                if (requestId != latestScanRequestId || versionAtStart != formVersion) return@launch
                when (val result = receiptOcrService.scan(uri, _uiState.value.currency)) {
                    is ReceiptOcrResult.Success -> {
                        if (requestId == latestScanRequestId && versionAtStart == formVersion) {
                            applyReceiptResult(result.receipt, snapshot)
                        }
                    }
                    ReceiptOcrResult.NoText -> {
                        if (requestId == latestScanRequestId && versionAtStart == formVersion) _uiState.update {
                            it.copy(
                                receiptScanMessage = "No readable text was found. Try a clearer receipt image.",
                                receiptScanIsError = true
                            )
                        }
                    }
                    is ReceiptOcrResult.Failure -> {
                        if (requestId == latestScanRequestId && versionAtStart == formVersion) {
                            _uiState.update { it.copy(receiptScanMessage = result.message, receiptScanIsError = true) }
                        }
                    }
                }
            } finally {
                temporaryImage?.delete()
                if (requestId == latestScanRequestId) {
                    _uiState.update { it.copy(isScanningReceipt = false) }
                }
            }
        }
    }

    fun clearReceiptScanMessage() {
        _uiState.update { it.copy(receiptScanMessage = null, receiptScanIsError = false) }
    }

    fun showReceiptScanError(message: String) {
        _uiState.update { it.copy(receiptScanMessage = message, receiptScanIsError = true) }
    }

    fun rejectReceiptScan() {
        val snapshot = reviewSnapshot ?: return
        latestScanRequestId++
        formVersion++
        reviewSnapshot = null
        pendingCategoryText = null
        _uiState.update {
            it.copy(
                amount = snapshot.amount,
                currency = snapshot.currency,
                category = snapshot.category,
                note = snapshot.note,
                date = snapshot.date,
                error = snapshot.error,
                receiptScanMessage = null,
                receiptScanIsError = false,
                receiptReviewPending = false
            )
        }
    }

    private fun applyReceiptResult(result: ReceiptScanResult, snapshot: ReceiptFormSnapshot) {
        _uiState.update { state ->
            val hasUsefulDetails = result.totalAmount != null || result.merchant != null || result.date != null
            if (!hasUsefulDetails) {
                return@update state.copy(
                    receiptScanMessage = "No useful receipt details were found. Try a clearer image.",
                    receiptScanIsError = true
                )
            }
            reviewSnapshot = snapshot
            pendingCategoryText = result.categoryText.takeIf { state.categories.isEmpty() }
            val suggestedCategory = if (state.isIncome) state.category else {
                suggestCategory(result.categoryText, state.categories) ?: state.category
            }
            val merchantNote = result.merchant?.let(::sanitizeNote).orEmpty()
            val dateIsUsable = result.date?.let(::isReceiptDateInRange) == true

            val formattedOcrDate = result.date?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.ENGLISH))
            val dateReviewMessage = when {
                result.isDateAmbiguous -> " Multiple dates found; existing date was kept."
                result.date != null && !dateIsUsable -> " Receipt date ($formattedOcrDate) is outside the allowed range and was not applied. Please select a valid date manually if needed."
                else -> ""
            }
            val message = if (result.totalAmount == null) {
                "Receipt scanned, but no reliable total was found. Please enter the amount.$dateReviewMessage"
            } else {
                "Receipt details added. Review the highlighted fields, then use Save Expense.$dateReviewMessage"
            }
            state.copy(
                amount = result.totalAmount?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: state.amount,
                currency = result.currency ?: "INR",
                category = suggestedCategory,
                note = if (merchantNote.isNotBlank()) merchantNote else state.note,
                date = result.date?.takeIf(::isReceiptDateInRange) ?: state.date,
                receiptScanMessage = message,
                receiptScanIsError = false,
                receiptReviewPending = true,
                error = null
            )
        }
    }

    private fun suggestCategory(text: String, categories: List<Category>): String? {
        if (categories.isEmpty() || text.isBlank()) return null
        val suggested = categoryEngine.autoCategorize(text, categories)
        if (suggested != "Other") return suggested

        // Fallback heuristics for common receipt terms if autoCategorize returned "Other"
        val lower = text.lowercase()
        val foodKeywords = listOf(
            "biriyani", "biryani", "chicken", "mutton", "roti", "dosa", "thali",
            "paneer", "naan", "curry", "rice", "restaurant", "cafe", "dining",
            "kitchen", "dhaba", "darshini", "bhavan", "tandoor", "hotel", "food"
        )
        if (foodKeywords.any { lower.contains(it) }) {
            val foodCat = categories.firstOrNull { it.name == "Food & Dining" || it.name.lowercase().contains("food") }
            if (foodCat != null) return foodCat.name
        }

        return "Other"
    }

    private fun boundDate(date: LocalDateTime): LocalDateTime {
        val maxDate = LocalDateTime.now().plusDays(1)
        val minDate = LocalDateTime.now().minusYears(2)
        return when {
            date.isAfter(maxDate) -> maxDate
            date.isBefore(minDate) -> minDate
            else -> date
        }
    }

    private fun isReceiptDateInRange(date: LocalDateTime): Boolean {
        val maxDate = LocalDateTime.now().plusDays(1)
        val minDate = LocalDateTime.now().minusYears(2)
        return !date.isAfter(maxDate) && !date.isBefore(minDate)
    }

    fun saveTransaction(onSuccess: () -> Unit) {
        if (_uiState.value.isScanningReceipt) return
        val state = _uiState.value
        val amountValue = state.amount.toDoubleOrNull()

        when {
            state.amount.isBlank() -> {
                _uiState.update { it.copy(error = "Please enter an amount") }
                return
            }
            amountValue == null -> {
                _uiState.update { it.copy(error = "Invalid amount format") }
                return
            }
            amountValue <= 0 -> {
                _uiState.update { it.copy(error = "Amount must be greater than zero") }
                return
            }
            amountValue < MIN_AMOUNT -> {
                _uiState.update { it.copy(error = "Minimum amount is ${MIN_AMOUNT}") }
                return
            }
            amountValue > MAX_AMOUNT -> {
                _uiState.update { it.copy(error = "Maximum amount is ${String.format("%,.0f", MAX_AMOUNT)}") }
                return
            }
            !MonetaryUtil.isValidAmount(amountValue) -> {
                _uiState.update { it.copy(error = "Amount is too large") }
                return
            }
        }

        viewModelScope.launch {
            reviewSnapshot = null
            _uiState.update { it.copy(uiState = UiState.Loading, error = null, receiptReviewPending = false) }

            try {
                val transaction = Transaction(
                    id = state.id ?: 0,
                    amount = amountValue ?: 0.0,
                    currency = state.currency,
                    category = state.category,
                    note = state.note,
                    date = state.date,
                    isRecurring = state.isRecurring,
                    isIncome = state.isIncome
                )

                if (state.id != null && state.id > 0) {
                    transactionRepository.updateTransaction(transaction)
                } else {
                    transactionRepository.insertTransaction(transaction)
                }

                if (state.isRecurring && state.id == null) {
                    val recurringRule = RecurringRule(
                        amount = amountValue ?: 0.0,
                        currency = state.currency,
                        category = state.category,
                        note = state.note,
                        frequency = state.recurringFrequency,
                        nextDate = state.date.plusDays(getDaysForFrequency(state.recurringFrequency))
                    )
                    recurringRuleRepository.insertRecurringRule(recurringRule)
                }

                _uiState.update { it.copy(uiState = UiState.Success(Unit)) }
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transaction")
                _uiState.update { 
                    it.copy(
                        uiState = UiState.Error("Failed to save transaction"),
                        error = "Failed to save. Please try again."
                    )
                }
            }
        }
    }

    private fun getDaysForFrequency(frequency: RecurringFrequency): Long {
        return when (frequency) {
            RecurringFrequency.DAILY -> 1
            RecurringFrequency.WEEKLY -> 7
            RecurringFrequency.MONTHLY -> 30
            RecurringFrequency.YEARLY -> 365
        }
    }

    class Factory(
        private val database: ExpenseDatabase,
        private val context: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddTransactionViewModel(
                TransactionRepository(database.transactionDao()),
                CategoryRepository(database.categoryDao()),
                RecurringRuleRepository(database.recurringRuleDao()),
                ReceiptOcrService(context.applicationContext)
            ) as T
        }
    }

    companion object {
        private const val TAG = "AddTransactionVM"
        private const val MIN_AMOUNT = 0.01
        private const val MAX_AMOUNT = 1000000.0
        private const val MAX_SAFE_AMOUNT = 1e14  // Prevent scientific notation overflow
        private val SUPPORTED_TRANSACTION_CATEGORIES = setOf(
            "Food & Dining", "Transportation", "Shopping", "Entertainment",
            "Bills & Utilities", "Healthcare", "Education", "Groceries",
            "Personal Care", "Travel", "Income", "Investment", "Gift", "Other"
        )
    }
}

private fun AddTransactionUiState.toReceiptSnapshot() = ReceiptFormSnapshot(
    amount = amount,
    currency = currency,
    category = category,
    note = note,
    date = date,
    error = error
)
