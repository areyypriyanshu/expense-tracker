package com.expensetracker.services.receipt

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ReceiptOcrService(private val context: Context) {

    suspend fun scan(uri: Uri, defaultCurrency: String = "INR"): ReceiptOcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = withContext(Dispatchers.IO) {
                InputImage.fromFilePath(context, uri)
            }
            val recognizedText = recognizer.process(image).awaitResult()
            val ocrText = recognizedText.text
            val lines = ocrText.lines().size
            val chars = ocrText.length
            android.util.Log.d("RECEIPT_OCR_DEBUG", "Lines: $lines, Chars: $chars")
            android.util.Log.d("RECEIPT_OCR_DEBUG", "Raw OCR text:\n$ocrText")

            if (ocrText.isBlank()) {
                ReceiptOcrResult.NoText
            } else {
                ReceiptOcrResult.Success(ReceiptParser.parse(recognizedText.text, defaultCurrency))
            }
        } catch (_: Exception) {
            ReceiptOcrResult.Failure("Could not read this receipt. Try a clearer image.")
        } finally {
            recognizer.close()
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
    }
}
