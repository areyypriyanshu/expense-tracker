package com.expensetracker.services.upisync

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

data class UpiSmsResult(
    val amount: Double?,
    val type: UpiTransactionType,
    val merchant: String?,
    val account: String?,
    val upiId: String?,
    val reference: String?,
    val date: LocalDateTime?,
    val rawMessage: String
)

enum class UpiTransactionType {
    DEBIT, CREDIT, UNKNOWN
}

object UpiSmsParser {
    
    private val datePatterns = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yy HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    )
    
    private val upiPatterns = listOf(
        Pattern.compile("""(?:Rs\.?|₹|INR)\s*([\d,]+\.?\d*)\s*(?:debited|credited|sent|received|paid|transfer)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Rs\.?|₹|INR)\s*([\d,]+\.?\d*)\s+(?:to|from|via)\s+([A-Za-z0-9@._-]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([A-Za-z0-9@._-]+)\s*(?:received|debits|credits)[\s:]+(?:Rs\.?|₹|INR)\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Rs\.?|₹|INR)\s*([\d,]+\.?\d*).{0,200}?(?:UPI|IMPS|NEFT|RTGS)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(debit|credit|transfer).{0,200}?(?:Rs\.?|₹|INR)\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE)
    )
    
    private val merchantPatterns = listOf(
        Pattern.compile("""(?:to|for|payee)\s+([A-Za-z\s]+?)(?:\s+(?:on|at|via|UPI|Ac|No|#))""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:paid to|payee|pay)\s+([A-Za-z\s]+?)(?:\s+[A-Za-z0-9@._-]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([A-Za-z]+)\s+(?:UPI|upi)\s+([A-Za-z0-9@._-]+)""", Pattern.CASE_INSENSITIVE)
    )
    
    private val accountPatterns = listOf(
        Pattern.compile("""(?:a\/c|ac|account|ac no|no)\s*[:.]?\s*[*xX]?(\d+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:from|to)\s+(?:a\/c|account)\s*[:.]?\s*[*xX]?(\d+)""", Pattern.CASE_INSENSITIVE)
    )
    
    private val upiIdPatterns = listOf(
        Pattern.compile("""([A-Za-z0-9]+@[A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:upi|UPI)\s*(?:id|ID)?\s*[:.]?\s*([A-Za-z0-9@._-]+)""", Pattern.CASE_INSENSITIVE)
    )
    
    private val referencePatterns = listOf(
        Pattern.compile("""(?:ref(?:erence)?|txn|transaction)\s*(?:id|no|#)?\s*[:.]?\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:UPI|IMPS|NEFT|RTGS)\s*(?:ref|txn|id)?\s*[:.]?\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
    )
    
    fun parse(message: String, sender: String? = null): UpiSmsResult? {
        val cleanedMessage = cleanMessage(message)
        
        if (!isUpiRelated(cleanedMessage)) {
            return null
        }
        
        val amount = extractAmount(cleanedMessage)
        val type = determineTransactionType(cleanedMessage)
        val merchant = extractMerchant(cleanedMessage)
        val account = extractAccount(cleanedMessage)
        val upiId = extractUpiId(cleanedMessage)
        val reference = extractReference(cleanedMessage)
        val date = extractDate(cleanedMessage)
        
        if (amount == null) {
            return null
        }
        
        return UpiSmsResult(
            amount = amount,
            type = type,
            merchant = merchant,
            account = account,
            upiId = upiId,
            reference = reference,
            date = date,
            rawMessage = message
        )
    }
    
    private fun cleanMessage(message: String): String {
        return message
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[\u200B-\u200D\uFEFF]"""), "")
            .trim()
    }
    
    private fun isUpiRelated(message: String): Boolean {
        val upiKeywords = listOf(
            "upi", "google pay", "gpay", "phonepe", "paytm", "bhim",
            "imps", "neft", "rtgs", "debit", "credit", "transfer",
            "debited", "credited", "transaction", "txn", "paid",
            "₹", "rs.", "inr", "rupees", "rupee",
            "bank", "account", "a/c", "balance"
        )
        
        val lowerMessage = message.lowercase()
        return upiKeywords.count { lowerMessage.contains(it) } >= 2
    }
    
    private fun extractAmount(message: String): Double? {
        val patterns = listOf(
            Regex("""(?:Rs\.?|₹|INR)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+\.\d{2})\s*(?:only)?""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+)\.00""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                return amountStr.toDoubleOrNull()
            }
        }
        
        return null
    }
    
    private fun determineTransactionType(message: String): UpiTransactionType {
        val lowerMessage = message.lowercase()
        
        // Strong DEBIT patterns - money going OUT
        val debitPatterns = listOf(
            "sent rs", "sent ₹",
            "paid rs", "paid ₹", "paid to",
            "transfer to",
            "debited from", "debited to",
            "debit from", "debit to",
            "you paid", "you sent",
            "towards",
            "upi from", "upi to"
        )
        
        // Strong CREDIT patterns - money coming IN
        val creditPatterns = listOf(
            "credited to", "credited in",
            "received rs", "received ₹",
            "credited rs", "credited ₹",
            "money received",
            "funds credited",
            "deposited in",
            "refund of", "cashback of",
            "credited your",
            "money credited",
            "balance credited",
            "account credited"
        )
        
        // Check for strong DEBIT indicators first
        for (pattern in debitPatterns) {
            if (lowerMessage.contains(pattern)) {
                // Double check it's not a refund/credit
                if (creditPatterns.any { lowerMessage.contains(it) }) {
                    continue // Might be both, continue checking
                }
                return UpiTransactionType.DEBIT
            }
        }
        
        // Check for CREDIT indicators
        for (pattern in creditPatterns) {
            if (lowerMessage.contains(pattern)) {
                return UpiTransactionType.CREDIT
            }
        }
        
        // Additional checks based on common patterns
        return when {
            lowerMessage.contains("debited") -> UpiTransactionType.DEBIT
            lowerMessage.contains("credited") -> UpiTransactionType.CREDIT
            lowerMessage.contains("received") && lowerMessage.contains("from") -> UpiTransactionType.CREDIT
            lowerMessage.contains("sent") && lowerMessage.contains("to") -> UpiTransactionType.DEBIT
            lowerMessage.contains("paid") && lowerMessage.contains("to") -> UpiTransactionType.DEBIT
            else -> UpiTransactionType.UNKNOWN
        }
    }
    
    private fun extractMerchant(message: String): String? {
        val sanitizedMessage = message.take(MAX_MESSAGE_LENGTH).lowercase()
        
        val merchantKeywords = listOf(
            "swiggy", "zomato", "dominos", "mcdonalds", "kfc",
            "amazon", "flipkart", "myntra", "shopify",
            "uber", "ola", "rapido", "swiggy",
            "netflix", "hotstar", "prime", "spotify",
            "phonepe", "paytm", "gpay", "google pay",
            "airtel", "jio", "vodafone",
            "electricity", "gas", "water bill"
        )
        
        for (keyword in merchantKeywords) {
            if (sanitizedMessage.contains(keyword)) {
                return keyword.replaceFirstChar { it.uppercase() }
            }
        }
        
        val patterns = listOf(
            Pattern.compile("""(?:to|for|payee|pay)\s+([A-Za-z\s]{1,20})(?:\s+(?:on|at|via|UPI|Ac|No|#|$))""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:paid to|payee)\s+([A-Za-z\s]{1,20})(?:\s+[A-Za-z0-9@._-])""", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim() ?: ""
                if (merchant.length >= 3 && merchant.length <= 30 && !containsDangerousChars(merchant)) {
                    return merchant.replaceFirstChar { it.uppercase() }
                }
            }
        }
        
        return null
    }
    
    private fun containsDangerousChars(input: String): Boolean {
        return input.contains(Regex("[<>\"'&;\\$]|\\p{C}"))
    }
    
    private fun extractAccount(message: String): String? {
        val patterns = listOf(
            Pattern.compile("""(?:a\/c|ac|account)\s*[:.]?\s*[*xX]?(\d{4,18})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:from|to)\s+(?:a\/c|account)\s*[:.]?\s*[*xX]?(\d{4,18})""", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val account = matcher.group(1)
                if (account != null && account.matches(Regex("^\\d+$"))) {
                    return account
                }
            }
        }
        
        return null
    }
    
    private fun extractUpiId(message: String): String? {
        val pattern = Pattern.compile("""([A-Za-z0-9._-]+@[A-Za-z0-9.-]+)""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(message)
        return if (matcher.find()) {
            val upiId = matcher.group(1)
            if (upiId != null && upiId.length <= 50 && !containsDangerousChars(upiId)) {
                upiId
            } else null
        } else null
    }
    
    private fun extractReference(message: String): String? {
        val patterns = listOf(
            Pattern.compile("""(?:ref|reference|txn|transaction)\s*(?:id|no|#)?\s*[:.]?\s*([A-Za-z0-9]{6,20})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:UPI|IMPS|NEFT|RTGS)\s*(?:ref|txn|id)?\s*[:.]?\s*([A-Za-z0-9]{6,20})""", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val ref = matcher.group(1)
                if (ref != null && !containsDangerousChars(ref) && ref.length <= 20) {
                    return ref
                }
            }
        }
        
        return null
    }
    
    private fun extractDate(message: String): LocalDateTime? {
        val dateRegexes = listOf(
            Regex("""(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})"""),
            Regex("""(\d{2}-\d{2}-\d{4}\s+\d{2}:\d{2})"""),
            Regex("""(\d{2}\s+[A-Za-z]{3}\s+\d{4},\s+\d{2}:\d{2})"""),
            Regex("""(\d{2}/\d{2}/\d{2}\s+\d{2}:\d{2})"""),
            Regex("""(\d{2}/\d{2}/\d{4})"""),
            Regex("""(\d{2}-\d{2}-\d{4})""")
        )
        
        for (regex in dateRegexes) {
            val match = regex.find(message)
            if (match != null) {
                val dateStr = match.groupValues[1]
                for (formatter in datePatterns) {
                    try {
                        return LocalDateTime.parse(dateStr, formatter)
                    } catch (_: Exception) {
                        try {
                            val date = java.time.LocalDate.parse(dateStr.take(10), formatter)
                            return date.atStartOfDay()
                        } catch (_: Exception) {
                            // Try next format
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    fun categorizeFromMessage(message: String): String {
        val boundedMessage = message.take(MAX_MESSAGE_LENGTH)
        val lowerMessage = boundedMessage.lowercase()
        
        return when {
            lowerMessage.containsAny("swiggy", "zomato", "dominos", "mcdonalds", "pizza", "restaurant", "cafe", "coffee") -> "Food & Dining"
            lowerMessage.containsAny("uber", "ola", "auto", "taxi", "metro", "rapido", "fuel", "petrol") -> "Transportation"
            lowerMessage.containsAny("amazon", "flipkart", "myntra", "shopping", "store", "myntra") -> "Shopping"
            lowerMessage.containsAny("netflix", "hotstar", "prime", "spotify", "movie", "youtube") -> "Entertainment"
            lowerMessage.containsAny("airtel", "jio", "vodafone", "bsnl", "recharge", "phone") -> "Bills & Utilities"
            lowerMessage.containsAny("electricity", "power", "bescom", "reliance energy") -> "Bills & Utilities"
            lowerMessage.containsAny("gas", "indane", "hp gas", "bharat gas") -> "Bills & Utilities"
            lowerMessage.containsAny("water bill", "bwssb", "municipal") -> "Bills & Utilities"
            lowerMessage.containsAny("pharmacy", "hospital", "doctor", "medical", "health") -> "Healthcare"
            lowerMessage.containsAny("school", "college", "fee", "tution", "education") -> "Education"
            lowerMessage.containsAny("grocery", "supermarket", "bigbasket", "market", "kirana") -> "Groceries"
            lowerMessage.containsAny("salon", "gym", "fitness", "spa", "beauty") -> "Personal Care"
            lowerMessage.containsAny("hotel", "flight", "booking", "travel", "irctc") -> "Travel"
            lowerMessage.containsAny("salary", "credited", "deposit", "income", "refund", "cashback") -> "Income"
            lowerMessage.containsAny("investment", "sip", "mutual fund", "stock") -> "Investment"
            lowerMessage.containsAny("loan", "emi", "insurance", "premium") -> "Bills & Utilities"
            lowerMessage.containsAny("transfer", "upi", "imps", "neft", "rtgs", "nft") -> "Transfer"
            lowerMessage.containsAny("atm", "cash", "withdrawal") -> "Cash"
            else -> "Other"
        }
    }
    
    private fun String.containsAny(vararg words: String): Boolean {
        return words.any { this.contains(it) }
    }
}

private const val MAX_MESSAGE_LENGTH = 500
