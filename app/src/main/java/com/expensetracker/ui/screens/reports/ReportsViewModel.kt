package com.expensetracker.ui.screens.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.services.export.ExportService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ReportsUiState(
    val transactions: List<Transaction> = emptyList(),
    val startDate: LocalDate = LocalDate.now().minusMonths(1),
    val endDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportResult: ExportResult? = null
)

sealed class ExportResult {
    data class Success(val fileName: String, val filePath: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val exportService: ExportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            transactionRepository.getAllTransactions().collect { transactions ->
                _uiState.update {
                    it.copy(
                        transactions = transactions,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onStartDateChange(date: LocalDate) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun onEndDateChange(date: LocalDate) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun getFilteredTransactions(): List<Transaction> {
        val state = _uiState.value
        val start = state.startDate.atStartOfDay()
        val end = state.endDate.atTime(LocalTime.MAX)

        return state.transactions.filter { it.date in start..end }
    }

    fun exportToCsv() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }

            val filtered = getFilteredTransactions()
            val fileName = "expenses_${LocalDate.now()}_${System.currentTimeMillis()}"

            val result = exportService.exportToCsv(filtered, fileName)

            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = ExportResult.Success(file.name, file.absolutePath)
                        )
                    }
                },
                onFailure = { _ ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = ExportResult.Error("Export failed")
                        )
                    }
                }
            )
        }
    }

    fun exportToPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }

            val filtered = getFilteredTransactions()
            val fileName = "expenses_${LocalDate.now()}_${System.currentTimeMillis()}"
            val title = "Expense Report (${_uiState.value.startDate} to ${_uiState.value.endDate})"

            val safeTitle = title.take(200).replace(Regex("[<>\"'&;]"), "")
            val result = exportService.exportToPdf(filtered, safeTitle, fileName)

            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = ExportResult.Success(file.name, file.absolutePath)
                        )
                    }
                },
                onFailure = { _ ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = ExportResult.Error("Export failed")
                        )
                    }
                }
            )
        }
    }

    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    fun showExportError(message: String) {
        _uiState.update { it.copy(isExporting = false, exportResult = ExportResult.Error(message)) }
    }

    class Factory(private val database: ExpenseDatabase, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportsViewModel(
                TransactionRepository(database.transactionDao()),
                ExportService(context)
            ) as T
        }
    }
}
