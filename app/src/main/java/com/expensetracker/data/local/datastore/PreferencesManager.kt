package com.expensetracker.data.local.datastore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expensetracker.data.model.UserPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_preferences")

class PreferencesManager(private val context: Context) {

    private val dataStore = context.dataStore
    private val secureDataStore = context.secureDataStore

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

    private object Keys {
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val THEME = stringPreferencesKey("theme")
        val HAS_SEEN_UPI_PERMISSION_PROMPT = booleanPreferencesKey("has_seen_upi_permission_prompt")
    }

    val userPreferences: Flow<UserPreference> = dataStore.data.map { prefs ->
        val currency = prefs[Keys.BASE_CURRENCY]
        val theme = prefs[Keys.THEME]
        
        UserPreference(
            baseCurrency = currency ?: getSystemDefaultCurrency(),
            theme = theme ?: "system"
        )
    }

    val hasSeenUpiPermissionPrompt: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.HAS_SEEN_UPI_PERMISSION_PROMPT] ?: false
    }

    private fun getSystemDefaultCurrency(): String {
        return try {
            val locale = java.util.Locale.getDefault()
            val currency = java.util.Currency.getInstance(locale)
            currency.currencyCode
        } catch (e: Exception) {
            "INR"
        }
    }

    suspend fun updateBaseCurrency(currency: String) {
        val sanitizedCurrency = currency
            .take(5)
            .filter { it.isLetter() }
            .uppercase()
        
        if (sanitizedCurrency.length in 3..5) {
            dataStore.edit { prefs ->
                prefs[Keys.BASE_CURRENCY] = sanitizedCurrency
            }
        }
    }

    suspend fun updateTheme(theme: String) {
        val sanitizedTheme = theme
            .take(20)
            .filter { it.isLetter() }
            .lowercase()
        
        if (sanitizedTheme in listOf("system", "light", "dark")) {
            dataStore.edit { prefs ->
                prefs[Keys.THEME] = sanitizedTheme
            }
        }
    }

    suspend fun markUpiPermissionPromptSeen() {
        dataStore.edit { prefs ->
            prefs[Keys.HAS_SEEN_UPI_PERMISSION_PROMPT] = true
        }
    }

    suspend fun setSecureValue(key: String, value: String) {
        require(isValidSecurePreferenceKey(key)) { "Invalid secure preference key" }
        val encrypted = encryptValue(value)
        secureDataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = encrypted
        }
    }

    suspend fun getSecureValue(key: String): String? {
        if (!isValidSecurePreferenceKey(key)) return null
        return try {
            val prefs = secureDataStore.data.first()
            val encrypted = prefs[stringPreferencesKey(key)] ?: return null
            decryptValue(encrypted)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to retrieve secure value")
            null
        }
    }

    private fun encryptValue(plaintext: String): String {
        require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptValue(encryptedData: String): String {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH + GCM_TAG_LENGTH) { "Invalid encrypted preference payload" }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec)

        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun isValidSecurePreferenceKey(key: String): Boolean {
        return key.length in 1..64 && key.matches(Regex("^[A-Za-z0-9_.-]+$"))
    }

    companion object {
        private const val TAG = "PreferencesManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "expense_tracker_encryption_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
}
