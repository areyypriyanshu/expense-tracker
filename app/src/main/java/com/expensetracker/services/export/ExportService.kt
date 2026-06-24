package com.expensetracker.services.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.expensetracker.data.model.Transaction
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

class ExportService(private val context: Context) {

    fun exportToCsv(transactions: List<Transaction>, fileName: String): Result<File> {
        return try {
            val safeFileName = sanitizeFileName(fileName)
            val exportDir = getExportDirectory()
            val file = File(exportDir, "${safeFileName}.csv")
            FileOutputStream(file).bufferedWriter().use { writer ->
                writer.append("Date,Type,Amount,Currency,Category,Note\n")
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                transactions.forEach { tx ->
                    val type = if (tx.isIncome) "Income" else "Expense"
                    val signedAmount = if (tx.isIncome) "+${tx.amount}" else "-${tx.amount}"
                    writer.append("${tx.date.format(formatter)},")
                    writer.append("$type,")
                    writer.append("$signedAmount,")
                    writer.append("${tx.currency},")
                    writer.append(escapeCsvField(tx.category))
                    writer.append(",")
                    writer.append(escapeCsvField(tx.note))
                    writer.append("\n")
                }
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsvField(field: String): String {
        val sanitized = sanitizeForCsvFormula(field)
            .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "")
            .take(MAX_CSV_FIELD_LENGTH)
        
        val escaped = sanitized.replace("\"", "\"\"")
        return if (escaped.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun sanitizeForCsvFormula(field: String): String {
        if (field.isEmpty()) return field

        val firstNonWhitespace = field.firstOrNull { !it.isWhitespace() } ?: return field
        return if (firstNonWhitespace in CSV_FORMULA_PREFIXES) "'$field" else field
    }

    fun exportToPdf(transactions: List<Transaction>, title: String, fileName: String): Result<File> {
        return try {
            val safeFileName = sanitizeFileName(fileName)
            val file = File(getExportDirectory(), "${safeFileName}.pdf")
            val document = PdfDocument()
            
            try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 50f
            val lineHeight = 15f
            var currentPage = 1
            var y = margin
            
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            
            val titlePaint = Paint().apply {
                textSize = 20f
                isFakeBoldText = true
            }
            val headerPaint = Paint().apply {
                textSize = 12f
                isFakeBoldText = true
            }
            val textPaint = Paint().apply {
                textSize = 10f
            }
            val incomePaint = Paint().apply {
                textSize = 10f
                color = 0xFF2E7D32.toInt() // Green for income
            }
            val expensePaint = Paint().apply {
                textSize = 10f
                color = 0xFFC62828.toInt() // Red for expense
            }
            
            canvas.drawText(title, margin, y, titlePaint)
            y += 30f
            canvas.drawText("Generated on ${java.time.LocalDate.now()}", margin, y, textPaint)
            y += 30f
            
            canvas.drawText("Date", margin, y, headerPaint)
            canvas.drawText("Type", margin + 85f, y, headerPaint)
            canvas.drawText("Amount", margin + 140f, y, headerPaint)
            canvas.drawText("Currency", margin + 210f, y, headerPaint)
            canvas.drawText("Category", margin + 280f, y, headerPaint)
            canvas.drawText("Note", margin + 375f, y, headerPaint)
            y += 20f
            
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            
            for (tx in transactions) {
                if (y > pageHeight - margin - lineHeight) {
                    document.finishPage(page)
                    currentPage++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin
                }
                
                val note = sanitizeForPdf(tx.note.take(25))
                val category = sanitizeForPdf(tx.category.take(15))
                val type = if (tx.isIncome) "Income" else "Expense"
                val amountPrefix = if (tx.isIncome) "+" else "-"
                val amountPaint = if (tx.isIncome) incomePaint else expensePaint
                
                canvas.drawText(tx.date.format(formatter), margin, y, textPaint)
                canvas.drawText(type, margin + 85f, y, amountPaint)
                canvas.drawText("$amountPrefix${String.format("%.2f", tx.amount)}", margin + 140f, y, amountPaint)
                canvas.drawText(tx.currency, margin + 210f, y, textPaint)
                canvas.drawText(category, margin + 280f, y, textPaint)
                canvas.drawText(note, margin + 375f, y, textPaint)
                y += lineHeight
            }
            
            document.finishPage(page)
            FileOutputStream(file).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeForPdf(text: String): String {
        return text
            .replace(Regex("[\t\r\n]"), " ")
            .replace(Regex("""[%_\\]"""), " ")
            .take(500)
            .trim()
    }

    fun shareFile(file: File): Intent {
        val exportDir = getExportDirectory().canonicalFile
        val canonicalFile = file.canonicalFile
        val relativePath = canonicalFile.relativeToOrNull(exportDir)
        if (relativePath == null || relativePath.path.startsWith("..") || canonicalFile.extension !in ALLOWED_EXTENSIONS) {
            throw SecurityException("File is not in allowed directory")
        }
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            canonicalFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".csv") -> "text/csv"
                file.name.endsWith(".pdf") -> "application/pdf"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun sanitizeFileName(fileName: String): String {
        val sanitized = fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
        
        return if (sanitized.isBlank()) "export_${System.currentTimeMillis()}" else sanitized
    }

    private fun getExportDirectory(): File {
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "Documents")
        val directory = File(documentsDir, EXPORT_DIRECTORY_NAME)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    private companion object {
        private const val MAX_CSV_FIELD_LENGTH = 500
        private const val EXPORT_DIRECTORY_NAME = "exports"
        private val CSV_FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r', '\n')
        private val ALLOWED_EXTENSIONS = setOf("csv", "pdf")
    }
}
