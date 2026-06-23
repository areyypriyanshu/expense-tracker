package com.expensetracker.services.import

import com.expensetracker.data.model.Transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ImportResult(
    val transactions: List<Transaction>,
    val errors: List<String>,
    val skippedRows: Int
)

data class ParsedRow(
    val date: LocalDateTime?,
    val amount: Double?,
    val description: String,
    val type: TransactionType,
    val originalLine: String
)

enum class TransactionType {
    DEBIT, CREDIT, UNKNOWN
}

object CsvParser {
    
    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    )
    
    fun parse(content: String): ImportResult {
        if (content.length > MAX_INPUT_CHARS) {
            return ImportResult(emptyList(), listOf("File is too large"), 0)
        }

        val lines = content.lineSequence()
            .filter { it.isNotBlank() }
            .take(MAX_ROWS + 1)
            .toList()
        val transactions = mutableListOf<Transaction>()
        val errors = mutableListOf<String>()
        var skippedRows = 0
        
        if (lines.isEmpty()) {
            return ImportResult(emptyList(), listOf("File is empty"), 0)
        }
        
        val headerLine = lines.first()
        val dataLines = lines.drop(1).take(MAX_ROWS)
        
        val delimiter = detectDelimiter(headerLine)
        val headers = parseCSVLine(headerLine, delimiter)
        
        val dateCol = findColumnIndex(headers, listOf("date", "transaction date", "posting date", "value date", "trans date"))
        val amountCol = findColumnIndex(headers, listOf("amount", "debit", "credit", "withdrawal", "deposit"))
        val descCol = findColumnIndex(headers, listOf("description", "narration", "particulars", "transaction details", "details", "memo"))
        val debitCol = findColumnIndex(headers, listOf("debit", "withdrawal", "debit amount"))
        val creditCol = findColumnIndex(headers, listOf("credit", "deposit", "credit amount"))
        
        dataLines.forEachIndexed { index, rawLine ->
            try {
                val line = rawLine.take(MAX_LINE_LENGTH)
                val values = parseCSVLine(line, delimiter)
                
                if (values.isEmpty() || values.all { it.isBlank() }) {
                    skippedRows++
                    return@forEachIndexed
                }
                
                val date = if (dateCol >= 0 && dateCol < values.size) {
                    parseDate(values[dateCol])
                } else null
                
                val (amount, type) = when {
                    debitCol >= 0 && creditCol >= 0 -> {
                        val debit = parseAmount(values.getOrNull(debitCol) ?: "")
                        val credit = parseAmount(values.getOrNull(creditCol) ?: "")
                        if (credit > 0) Pair(credit, TransactionType.CREDIT)
                        else if (debit > 0) Pair(debit, TransactionType.DEBIT)
                        else Pair(0.0, TransactionType.UNKNOWN)
                    }
                    amountCol >= 0 -> {
                        val amtStr = values.getOrNull(amountCol) ?: ""
                        parseAmountWithType(amtStr)
                    }
                    else -> Pair(0.0, TransactionType.UNKNOWN)
                }
                
                val description = if (descCol >= 0 && descCol < values.size) {
                    values[descCol].trim().take(MAX_DESCRIPTION_LENGTH)
                } else {
                    values.filterIndexed { i, _ -> i != dateCol && i != amountCol }
                        .joinToString(" ")
                        .trim()
                        .take(MAX_DESCRIPTION_LENGTH)
                }
                
                if (amount > 0 && description.isNotBlank()) {
                    val sanitizedNote = sanitizeForStorage(description.take(100))
                    
                    val (category, isIncome) = when (type) {
                        TransactionType.CREDIT -> {
                            val cat = if (description.contains(Regex("(?i)(refund|cashback|reversal)"))) {
                                "Refund"
                            } else {
                                "Income"
                            }
                            Pair(cat, true)
                        }
                        TransactionType.DEBIT -> {
                            val cat = categorizeTransaction(description)
                            // Expenses can't be 'Income' category — reclassify
                            Pair(if (cat == "Income") "Other" else cat, false)
                        }
                        TransactionType.UNKNOWN -> {
                            val cat = categorizeTransaction(description)
                            // Sync isIncome flag with the category
                            val isInc = cat == "Income"
                            Pair(cat, isInc)
                        }
                    }
                    
                    val transaction = Transaction(
                        amount = amount,
                        currency = "INR",
                        category = category,
                        note = sanitizedNote,
                        date = date ?: LocalDateTime.now(),
                        isRecurring = false,
                        isIncome = isIncome
                    )
                    transactions.add(transaction)
                } else {
                    skippedRows++
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: Failed to parse row")
                skippedRows++
            }
        }
        
        return ImportResult(transactions, errors, skippedRows)
    }
    
    private fun detectDelimiter(line: String): Char {
        val delimiters = listOf(',', ';', '\t', '|')
        return delimiters.maxByOrNull { delimiter ->
            line.count { it == delimiter }
        } ?: ','
    }
    
    private fun parseCSVLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        var index = 0
        while (index < line.length && result.size < MAX_COLUMNS) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim().take(MAX_FIELD_LENGTH))
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            index++
        }
        result.add(current.toString().trim().take(MAX_FIELD_LENGTH))
        
        return result
    }
    
    private fun findColumnIndex(headers: List<String>, possibleNames: List<String>): Int {
        headers.forEachIndexed { index, header ->
            val lowerHeader = header.lowercase().trim()
            if (possibleNames.any { lowerHeader.contains(it) }) {
                return index
            }
        }
        return -1
    }
    
    private fun parseDate(dateStr: String): LocalDateTime? {
        val cleanDate = dateStr.trim()
        
        for (formatter in dateFormats) {
            try {
                val date = java.time.LocalDate.parse(cleanDate, formatter)
                return date.atStartOfDay()
            } catch (_: Exception) {
                // Try next format
            }
        }
        
        return null
    }
    
    private fun parseAmount(amountStr: String): Double {
        val clean = amountStr.take(MAX_FIELD_LENGTH).trim()
            .replace(",", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace("₹", "")
            .replace(" ", "")
            .replace(Regex("""[A-Za-z]"""), "")
            .trim()
        
        return clean.toDoubleOrNull() ?: 0.0
    }
    
    private fun parseAmountWithType(amountStr: String): Pair<Double, TransactionType> {
        val hasDebit = amountStr.contains(Regex("""(DR|debit|withdrawal)""", RegexOption.IGNORE_CASE))
        val hasCredit = amountStr.contains(Regex("""(CR|credit|deposit)""", RegexOption.IGNORE_CASE))
        
        val amount = parseAmount(amountStr)
        
        return when {
            hasDebit -> Pair(amount, TransactionType.DEBIT)
            hasCredit -> Pair(amount, TransactionType.CREDIT)
            else -> Pair(amount, TransactionType.UNKNOWN)
        }
    }
    
    private fun categorizeTransaction(description: String): String {
        val sanitizedDesc = description.take(200).lowercase()
        
        return when {
            sanitizedDesc.containsAny("swiggy", "zomato", "dominos", "pizza", "restaurant", "cafe", "coffee", "food", "meal", "lunch", "dinner") -> "Food & Dining"
            sanitizedDesc.containsAny("uber", "ola", "auto", "taxi", "metro", "rail", "bus", "fuel", "petrol") -> "Transportation"
            sanitizedDesc.containsAny("amazon", "flipkart", "myntra", "shopping", "store", "mall") -> "Shopping"
            sanitizedDesc.containsAny("netflix", "hotstar", "prime", "spotify", "movie", "youtube", "game") -> "Entertainment"
            sanitizedDesc.containsAny("electricity", "water", "gas", "bill", "recharge", "broadband", "rent") -> "Bills & Utilities"
            sanitizedDesc.containsAny("pharmacy", "hospital", "doctor", "medical", "medicine", "health") -> "Healthcare"
            sanitizedDesc.containsAny("school", "college", "fee", "course", "book", "education") -> "Education"
            sanitizedDesc.containsAny("grocery", "supermarket", "bigbasket", "market", "vegetable", "fruit") -> "Groceries"
            sanitizedDesc.containsAny("salon", "gym", "fitness", "spa", "beauty") -> "Personal Care"
            sanitizedDesc.containsAny("hotel", "flight", "travel", "booking", "vacation") -> "Travel"
            sanitizedDesc.containsAny("transfer", "upi", "neft", "imps", "nft") -> "Other"
            sanitizedDesc.containsAny("atm", "cash", "withdrawal") -> "Other"
            sanitizedDesc.containsAny("salary", "credited", "deposit", "income", "freelance") -> "Income"
            else -> "Other"
        }
    }
    
    private fun sanitizeForStorage(input: String): String {
        if (input.isBlank()) return ""
        
        return input
            .replace(Regex("[\t\r\n]"), " ")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("""javascript:|on\w+=""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[<>\"'&]"), "")
            .replace(Regex("\\p{C}"), "")
            .trim()
            .take(100)
    }
    
    private fun String.containsAny(vararg words: String): Boolean {
        return words.any { this.contains(it) }
    }

    private const val MAX_INPUT_CHARS = 2_000_000
    private const val MAX_ROWS = 10_000
    private const val MAX_COLUMNS = 50
    private const val MAX_LINE_LENGTH = 10_000
    private const val MAX_FIELD_LENGTH = 1_000
    private const val MAX_DESCRIPTION_LENGTH = 500
}
