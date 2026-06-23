package com.expensetracker.ui.screens.import

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.UiState
import com.expensetracker.services.import.CsvParser
import com.expensetracker.services.import.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val uiState: UiState<ImportData> = UiState.Loading,
    val isProcessing: Boolean = false,
    val selectedFileName: String? = null,
    val importResult: ImportResult? = null,
    val importedCount: Int = 0,
    val error: String? = null
)

data class ImportData(
    val fileName: String,
    val totalRows: Int,
    val previewTransactions: List<Transaction>,
    val allTransactions: List<Transaction>,
    val errors: List<String>
)

class ImportViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun processFile(uri: Uri, content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            try {
                if (content.length > MAX_IMPORT_CHARS) {
                    _uiState.update {
                        it.copy(
                            uiState = UiState.Error("File is too large"),
                            isProcessing = false,
                            error = "File is too large"
                        )
                    }
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    CsvParser.parse(content)
                }

                val fileName = sanitizeFileName(uri.lastPathSegment ?: "import.csv")
                
                val previewTransactions = result.transactions.take(10)
                
                val importData = ImportData(
                    fileName = fileName,
                    totalRows = result.transactions.size,
                    previewTransactions = previewTransactions,
                    allTransactions = result.transactions,
                    errors = result.errors
                )

                _uiState.update {
                    it.copy(
                        uiState = UiState.Success(importData),
                        isProcessing = false,
                        selectedFileName = fileName,
                        importResult = result
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        uiState = UiState.Error("Failed to parse file", e),
                        isProcessing = false,
                        error = "Failed to parse file"
                    )
                }
            }
        }
    }

    fun rejectFile(message: String) {
        _uiState.update {
            it.copy(
                uiState = UiState.Error(message),
                isProcessing = false,
                error = message
            )
        }
    }

    fun importTransactions(onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            try {
                val transactions = _uiState.value.importResult?.transactions ?: emptyList()
                var importedCount = 0

                withContext(Dispatchers.IO) {
                    transactions.forEach { transaction ->
                        transactionRepository.insertTransaction(transaction)
                        importedCount++
                    }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        importedCount = importedCount
                    )
                }

                onSuccess(importedCount)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        uiState = UiState.Error("Failed to import transactions", e),
                        isProcessing = false,
                        error = "Failed to import transactions"
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.value = ImportUiState()
    }

    private fun sanitizeFileName(fileName: String): String {
        val sanitized = fileName
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100)
        
        return if (sanitized.isBlank()) "import" else sanitized
    }

    class Factory(private val database: ExpenseDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImportViewModel(
                TransactionRepository(database.transactionDao())
            ) as T
        }
    }

    private companion object {
        private const val MAX_IMPORT_CHARS = 2_000_000
    }
}
