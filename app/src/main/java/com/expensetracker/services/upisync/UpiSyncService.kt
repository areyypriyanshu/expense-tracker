package com.expensetracker.services.upisync

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.app.ActivityCompat
import com.expensetracker.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SyncedTransaction(
    val transaction: Transaction,
    val sourceMessage: String,
    val sourceType: String,
    val messageHash: String,
    val alreadyImported: Boolean = false
)

class UpiSyncService(private val context: Context) {
    
    private val knownSenders = listOf(
        "GPay", "PhonePe", "Paytm", "MobiKwik", "Amazon Pay",
        "HDFC", "SBI", "ICICI", "Axis", "Kotak", "Yes Bank",
        "IDBI", "BOB", "PNB", "IB", "UPI", "UPI-", "NPCI",
        "Bhim", "Google Pay", "Phone Pay", "PayU", "FreeCharge"
    )
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("upi_sync_prefs", Context.MODE_PRIVATE)
    }
    
    private val encryptionKey: SecretKey by lazy {
        getOrCreateEncryptionKey()
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null)
            if (entry is KeyStore.SecretKeyEntry) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
    
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
    
    fun setLastSyncTime(time: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
    }
    
    fun getImportedHashes(): Set<String> {
        return try {
            val encryptedHashes = prefs.getString(ENCRYPTED_HASHES_KEY, null) ?: return emptySet()
            decryptHashes(encryptedHashes)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to decrypt hashes")
            emptySet()
        }
    }
    
    @Synchronized
    fun addImportedHashes(hashes: Set<String>) {
        val existing = try {
            val encrypted = prefs.getString(ENCRYPTED_HASHES_KEY, null)
            if (encrypted != null) decryptHashes(encrypted).toMutableSet() else mutableSetOf()
        } catch (e: Exception) {
            mutableSetOf()
        }
        existing.addAll(hashes)
        // Prune to prevent unbounded growth
        val pruned = if (existing.size > MAX_HASH_COUNT) {
            existing.toList().takeLast(MAX_HASH_COUNT).toSet()
        } else existing
        prefs.edit().putString(ENCRYPTED_HASHES_KEY, encryptHashes(pruned)).apply()
    }
    
    fun addImportedHash(hash: String) {
        addImportedHashes(setOf(hash))
    }
    
    private fun encryptHashes(hashes: Set<String>): String {
        val escapedHashes = hashes.map { hash -> 
            hash.replace(SEPARATOR_ESCAPE, SEPARATOR_REPLACEMENT)
                   .replace(SEPARATOR, SEPARATOR_ESCAPE)
        }
        val plaintext = escapedHashes.joinToString(SEPARATOR)
        
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    private fun decryptHashes(encryptedData: String): Set<String> {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH + GCM_TAG_LENGTH) { "Invalid encrypted hash payload" }
        
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec)
        
        val decrypted = cipher.doFinal(encrypted)
        val plaintext = String(decrypted, Charsets.UTF_8)
        
        if (plaintext.isEmpty()) return emptySet()
        
        val unescapedHashes = plaintext.split(SEPARATOR).map { hash ->
            hash.replace(SEPARATOR_ESCAPE, SEPARATOR)
                .replace(SEPARATOR_REPLACEMENT, SEPARATOR_ESCAPE)
        }
        return unescapedHashes.toSet()
    }
    
    suspend fun scanSmsSince(
        daysAgo: Int = 7,
        existingTransactions: List<Transaction> = emptyList()
    ): List<SyncedTransaction> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<SyncedTransaction>()
        
        if (!hasSmsPermission()) {
            return@withContext transactions
        }
        
        val contentResolver: ContentResolver = context.contentResolver
        val boundedDaysAgo = daysAgo.coerceIn(1, MAX_SMS_SCAN_DAYS)
        val cutoffTime = System.currentTimeMillis() - (boundedDaysAgo.toLong() * 24 * 60 * 60 * 1000)
        
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(cutoffTime.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        val cursor = try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to query SMS")
            return@withContext transactions
        }
        
        val importedHashes = getImportedHashes().toMutableSet()
        val seenHashes = mutableSetOf<String>()
        
        cursor?.use {
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            
            while (it.moveToNext()) {
                val body = it.getString(bodyIndex) ?: continue
                val address = it.getString(addressIndex) ?: continue
                val dateMs = it.getLong(dateIndex)
                
                if (isRelevantSender(address) && body.length > 20) {
                    val result = UpiSmsParser.parse(body, address)
                    if (result != null && result.amount != null && result.amount > 0) {
                        val messageDate = result.date ?: LocalDateTime.ofEpochSecond(
                            dateMs / 1000, 0, ZoneOffset.systemDefault().rules.getOffset(java.time.Instant.now())
                        )
                        
                        val messageHash = generateTransactionHash(
                            amount = result.amount,
                            date = messageDate,
                            type = result.type,
                            sourceId = "${address.take(40)}_${result.reference.orEmpty().take(40)}_${result.merchant.orEmpty().take(30)}_${result.upiId.orEmpty().take(30)}"
                        )
                        
                        if (seenHashes.contains(messageHash)) {
                            continue
                        }
                        seenHashes.add(messageHash)
                        
                        val alreadyImported = importedHashes.contains(messageHash) ||
                            isDuplicateInDb(
                                amount = result.amount,
                                date = messageDate,
                                transactions = existingTransactions
                            )
                        
                        if (alreadyImported) {
                            continue
                        }
                        
                        val category: String
                        val isIncome: Boolean
                        
                        when (result.type) {
                            UpiTransactionType.CREDIT -> {
                                category = if (body.contains(Regex("(?i)(refund|cashback|reversal)"))) {
                                    "Refund"
                                } else {
                                    "Income"
                                }
                                isIncome = true
                            }
                            UpiTransactionType.DEBIT -> {
                                category = UpiSmsParser.categorizeFromMessage(body)
                                isIncome = false
                            }
                            UpiTransactionType.UNKNOWN -> {
                                category = "Other"
                                isIncome = false
                            }
                        }
                        
                        val merchant = result.merchant ?: extractMerchantFromMessage(body)
                        
                        val transaction = Transaction(
                            amount = result.amount,
                            currency = "INR",
                            category = category,
                            note = "${merchant ?: "UPI Transaction"} - ${result.reference ?: address.take(20)}",
                            date = messageDate,
                            isRecurring = false,
                            isIncome = isIncome
                        )
                        
                        transactions.add(
                            SyncedTransaction(
                                transaction = transaction,
                                sourceMessage = body.take(300),
                                sourceType = address,
                                messageHash = messageHash,
                                alreadyImported = false
                            )
                        )
                    }
                }
            }
        }
        
        transactions
    }
    
    private fun generateTransactionHash(
        amount: Double,
        date: LocalDateTime,
        type: UpiTransactionType,
        sourceId: String
    ): String {
        val roundedAmount = (amount * 100).toLong()
        // Use 10-minute blocks instead of hourly to reduce collision window
        val tenMinBlock = date.withMinute((date.minute / 10) * 10).withSecond(0).withNano(0)
        val normalizedSourceId = sourceId
            .lowercase()
            .replace(Regex("[^a-z0-9_@.-]"), "")
            .take(120)
        
        val input = "${type.name}_${roundedAmount}_${tenMinBlock}_${normalizedSourceId}"
        return sha256Hash(input)
    }
    
    private fun sha256Hash(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private fun isDuplicateInDb(
        amount: Double,
        date: LocalDateTime,
        transactions: List<Transaction>
    ): Boolean {
        if (transactions.isEmpty()) return false
        
        val amountInRupees = Math.round(amount * 100)
        
        return transactions.any { existing ->
            val existingAmountInRupees = Math.round(existing.amount * 100)
            val amountMatch = existingAmountInRupees == amountInRupees
            
            val hoursDiff = kotlin.math.abs(
                java.time.Duration.between(existing.date, date).toHours()
            )
            val dateMatch = hoursDiff <= 2
            
            amountMatch && dateMatch
        }
    }
    
    private fun isRelevantSender(address: String): Boolean {
        val lowerAddress = address.uppercase()
        return knownSenders.any { lowerAddress.contains(it.uppercase()) }
    }
    
    private fun extractMerchantFromMessage(message: String): String {
        val patterns = listOf(
            Regex("""(?:to|for|payee|pay)\s+([A-Za-z\s]+?)(?:\s+on|\s+via|\s+[A-Z]|\s+[0-9]|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:paid|payment)\s+(?:to\s+)?([A-Za-z\s]+?)(?:\s+[A-Z]|\s+[0-9]|$)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length >= 3 && merchant.length <= 30) {
                    return merchant.replaceFirstChar { it.uppercase() }
                }
            }
        }
        
        return "UPI"
    }
    
    fun hasSmsPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.READ_SMS
        )
    }
    
    fun clearAllSyncedData() {
        prefs.edit()
            .remove(ENCRYPTED_HASHES_KEY)
            .remove(KEY_LAST_SYNC_TIME)
            .apply()
    }
    
    companion object {
        private const val TAG = "UpiSyncService"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "upi_sync_encryption_key"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val ENCRYPTED_HASHES_KEY = "imported_hashes_encrypted"
        private const val SEPARATOR = "\u0000"  // NULL character as separator
        private const val SEPARATOR_ESCAPE = "\u0001"  // Escape sequence for separator within hash
        private const val SEPARATOR_REPLACEMENT = "\u0002"
        private const val MAX_HASH_COUNT = 5000
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val MAX_SMS_SCAN_DAYS = 30
    }
}
