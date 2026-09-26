package com.expensetracker.services.receipt

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Evaluates ReceiptParser against the full IndianReceiptDataset (train/validation/test).
 * This is the training/evaluation harness for post-OCR extraction quality.
 */
class ReceiptDatasetEvaluationTest {

    data class DatasetRecord(
        val id: String,
        val ocrText: String,
        val expectedMerchant: String,
        val expectedAmount: Double,
        val expectedCurrency: String,
        val expectedDate: String,
        val expectedCategory: String
    )

    data class FieldFailure(
        val id: String,
        val field: String,
        val expected: String,
        val actual: String
    )

    @Test
    fun evaluateDataset() {
        val datasetDir = findDatasetDir()
        // IndianReceiptDataset/ is gitignored (see .gitignore) and is supplied out of band.
        // Absent it, this harness is skipped rather than failed - nothing about parser
        // quality can be measured without the records.
        assumeTrue(
            "IndianReceiptDataset directory not found. Looked relative to: ${File(".").absolutePath}",
            datasetDir != null && datasetDir.exists()
        )

        val records = loadAllRecords(datasetDir!!)
        assertTrue("Expected 100 dataset records, got ${records.size}", records.size == 100)

        var amountCorrect = 0
        var merchantCorrect = 0
        var currencyCorrect = 0
        var dateCorrect = 0
        val failures = mutableListOf<FieldFailure>()

        for (record in records) {
            val result = ReceiptParser.parse(record.ocrText)

            // Amount
            val amountOk = result.totalAmount != null &&
                kotlin.math.abs(result.totalAmount - record.expectedAmount) < 0.02
            if (amountOk) {
                amountCorrect++
            } else {
                failures += FieldFailure(
                    record.id,
                    "amount",
                    record.expectedAmount.toString(),
                    result.totalAmount?.toString() ?: "null"
                )
            }

            // Merchant (case-insensitive; allow partial match either way)
            val merchantOk = result.merchant != null && merchantsMatch(result.merchant, record.expectedMerchant)
            if (merchantOk) {
                merchantCorrect++
            } else {
                failures += FieldFailure(
                    record.id,
                    "merchant",
                    record.expectedMerchant,
                    result.merchant ?: "null"
                )
            }

            // Currency
            val currencyOk = result.currency == record.expectedCurrency
            if (currencyOk) {
                currencyCorrect++
            } else {
                failures += FieldFailure(
                    record.id,
                    "currency",
                    record.expectedCurrency,
                    result.currency ?: "null"
                )
            }

            // Date
            val dateOk = result.date != null &&
                result.date.toLocalDate() == LocalDate.parse(record.expectedDate, DateTimeFormatter.ISO_LOCAL_DATE)
            if (dateOk) {
                dateCorrect++
            } else {
                failures += FieldFailure(
                    record.id,
                    "date",
                    record.expectedDate,
                    result.date?.toLocalDate()?.toString() ?: "null"
                )
            }
        }

        val n = records.size
        println("=== INDIAN RECEIPT DATASET EVALUATION REPORT ===")
        println("Total records: $n")
        println("Amount   accuracy: $amountCorrect / $n (${pct(amountCorrect, n)}%)")
        println("Merchant accuracy: $merchantCorrect / $n (${pct(merchantCorrect, n)}%)")
        println("Currency accuracy: $currencyCorrect / $n (${pct(currencyCorrect, n)}%)")
        println("Date     accuracy: $dateCorrect / $n (${pct(dateCorrect, n)}%)")
        println("--------------------------------------------------")
        if (failures.isNotEmpty()) {
            println("Failures (${failures.size}):")
            failures.groupBy { it.field }.forEach { (field, list) ->
                println("  [$field] ${list.size} failures")
                list.take(15).forEach {
                    println("    ${it.id}: expected='${it.expected}' actual='${it.actual}'")
                }
                if (list.size > 15) println("    ... and ${list.size - 15} more")
            }
        }
        println("==================================================")

        // Training target thresholds (raise as parser improves)
        assertTrue("Amount accuracy should be >= 80% (got ${pct(amountCorrect, n)}%)", amountCorrect >= n * 0.80)
        assertTrue("Merchant accuracy should be >= 85% (got ${pct(merchantCorrect, n)}%)", merchantCorrect >= n * 0.85)
        assertTrue("Currency accuracy should be >= 80% (got ${pct(currencyCorrect, n)}%)", currencyCorrect >= n * 0.80)
        assertTrue("Date accuracy should be >= 85% (got ${pct(dateCorrect, n)}%)", dateCorrect >= n * 0.85)
    }

    private fun merchantsMatch(actual: String, expected: String): Boolean {
        val a = normalizeMerchant(actual)
        val e = normalizeMerchant(expected)
        return a == e || a.contains(e) || e.contains(a)
    }

    private fun normalizeMerchant(value: String): String =
        value.lowercase()
            .replace("café", "cafe")
            .replace("é", "e")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun pct(correct: Int, total: Int): Int =
        if (total == 0) 0 else (correct * 100) / total

    private fun loadAllRecords(datasetDir: File): List<DatasetRecord> {
        val files = listOf("train.jsonl", "validation.jsonl", "test.jsonl")
        val records = mutableListOf<DatasetRecord>()
        for (name in files) {
            val file = File(datasetDir, name)
            if (!file.exists()) continue
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                parseRecord(line)?.let { records += it }
            }
        }
        return records
    }

    /**
     * Minimal JSON object parser for our flat dataset schema.
     * Handles escaped strings (\n, \", \\, unicode) so OCR multiline text is restored.
     */
    private fun parseRecord(line: String): DatasetRecord? {
        val id = readString(line, "id") ?: return null
        val ocrText = readString(line, "ocr_text") ?: return null
        val expectedMerchant = readString(line, "expected_merchant") ?: return null
        val expectedAmount = readNumber(line, "expected_amount") ?: return null
        val expectedCurrency = readString(line, "expected_currency") ?: return null
        val expectedDate = readString(line, "expected_date") ?: return null
        val expectedCategory = readString(line, "expected_category") ?: return null
        return DatasetRecord(
            id = id,
            ocrText = ocrText,
            expectedMerchant = expectedMerchant,
            expectedAmount = expectedAmount,
            expectedCurrency = expectedCurrency,
            expectedDate = expectedDate,
            expectedCategory = expectedCategory
        )
    }

    private fun readString(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        var i = keyIdx + key.length + 2
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != ':') return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        i++ // opening quote
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            when {
                c == '\\' && i + 1 < json.length -> {
                    val n = json[i + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'u' -> {
                            if (i + 5 < json.length) {
                                val hex = json.substring(i + 2, i + 6)
                                sb.append(hex.toInt(16).toChar())
                                i += 4 // extra beyond the two we always skip
                            }
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                }
                c == '"' -> return sb.toString()
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    private fun readNumber(json: String, key: String): Double? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        var i = keyIdx + key.length + 2
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != ':') return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        val start = i
        while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] == '-' || json[i] == 'e' || json[i] == 'E' || json[i] == '+')) {
            i++
        }
        if (start == i) return null
        return json.substring(start, i).toDoubleOrNull()
    }

    private fun findDatasetDir(): File? {
        val candidates = listOf(
            File("IndianReceiptDataset"),
            File("../IndianReceiptDataset"),
            File("../../IndianReceiptDataset"),
            File("app/../IndianReceiptDataset"),
            // Gradle unit test working directory is often the module dir
            File(System.getProperty("user.dir"), "IndianReceiptDataset"),
            File(System.getProperty("user.dir"), "../IndianReceiptDataset")
        )
        return candidates.map { it.canonicalFile }.firstOrNull { it.exists() && it.isDirectory }
    }
}
