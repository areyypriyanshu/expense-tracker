package com.expensetracker.domain.usecase

import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Result
import com.expensetracker.domain.model.MonetaryUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AddTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction): Result<Long> {
        return try {
            if (!MonetaryUtil.isValidAmount(transaction.amount)) {
                return Result.Error("Invalid amount. Please enter a valid amount.")
            }
            
            if (transaction.amount < 0) {
                return Result.Error("Amount cannot be negative.")
            }
            
            if (transaction.amount == 0.0) {
                return Result.Error("Amount must be greater than zero.")
            }
            
            val id = transactionRepository.insertTransaction(transaction)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error("Failed to add transaction", e)
        }
    }
}

class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction): Result<Unit> {
        return try {
            if (!MonetaryUtil.isValidAmount(transaction.amount)) {
                return Result.Error("Invalid amount. Please enter a valid amount.")
            }
            
            transactionRepository.updateTransaction(transaction)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to update transaction", e)
        }
    }
}

class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction): Result<Unit> {
        return try {
            transactionRepository.deleteTransaction(transaction)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to delete transaction", e)
        }
    }
}

class GetTransactionsUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(): Flow<List<Transaction>> {
        return transactionRepository.getAllTransactions()
    }
    
    fun getFiltered(
        transactions: List<Transaction>,
        searchQuery: String,
        category: String?,
        startDate: java.time.LocalDateTime?,
        endDate: java.time.LocalDateTime?
    ): List<Transaction> {
        return transactions.filter { tx ->
            val matchesQuery = searchQuery.isBlank() ||
                    tx.note.contains(searchQuery, ignoreCase = true) ||
                    tx.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = category == null || tx.category == category
            
            val matchesDateRange = (startDate == null || tx.date >= startDate) &&
                    (endDate == null || tx.date <= endDate)
            
            matchesQuery && matchesCategory && matchesDateRange
        }
    }
}

class GetMonthlyTransactionsUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(year: Int, month: Int): Flow<List<Transaction>> {
        return transactionRepository.getAllTransactions().map { transactions ->
            transactions.filter { tx ->
                tx.date.year == year && tx.date.monthValue == month
            }
        }
    }
}
