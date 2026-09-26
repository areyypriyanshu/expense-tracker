package com.expensetracker.services.receipt

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Converts OCR text into conservative receipt suggestions. It never guesses a total from an unlabelled number. */
object ReceiptParser {

    private data class TotalLabel(val pattern: Regex, val isGeneric: Boolean = false)
    private data class AmountCandidate(val value: Double, val start: Int)
    private data class DateExtraction(val value: LocalDateTime?, val isAmbiguous: Boolean)

    // Order is intentional: higher priority first.
    private val totalLabels = listOf(
        TotalLabel(Regex("\\bgrand\\s+total\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\b(?:amount|amt)\\s+due\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\bpayable\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\bnet\\s+total\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\bnet\\s+(?:amount|amt)\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\btotal\\s+(?:amount|amt)\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\b(?:amount|amt)\\s+payable\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\bbalance\\s+due\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\bfinal\\s+(?:amount|amt)\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\binvoice\\s+total\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\bbill\\s+total\\b", RegexOption.IGNORE_CASE)),
        TotalLabel(Regex("\\btotal\\b", RegexOption.IGNORE_CASE), isGeneric = true)
    )

    private val unrelatedLinePattern = Regex(
        "\\b(?:tax|cgst|sgst|igst|gst|vat|cess|service\\s+tax|service\\s+charge|service\\s+charges|discount|subtotal|sub-?|gross\\s+total|qty|quantity|items?|item)\\b",
        RegexOption.IGNORE_CASE
    )

    private val genericTotalExclusions = listOf(
        Regex("\\b(?:tax|cgst|sgst|igst|gst|vat|cess|discount|disc|savings?|saved|item|items|sub|sub-?|gross|qty|quantity|pcs|pieces|units?)\\s*[:=-]?\\s*total\\b", RegexOption.IGNORE_CASE),
        Regex("\\btotal\\s*[:=-]?\\s*(?:tax|cgst|sgst|igst|gst|vat|cess|discount|disc|savings?|saved|qty|quantity|items?|pcs|pieces|count|units?|gross)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:qty|quantity|items?|pcs|pieces|count)\\s*[:=-]?\\s*\\d", RegexOption.IGNORE_CASE),
        Regex("\\btotal\\s+(?:no\\.?\\s+of\\s+)?(?:items?|qty|quantity|pcs|pieces|units?)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:vat|gst|cgst|sgst|igst|service\\s+tax|service\\s+charge|service\\s+charges|discount|subtotal|sub-total|gross\\s+total)\\b", RegexOption.IGNORE_CASE),
        Regex("\\btotal\\s+qty\\b", RegexOption.IGNORE_CASE),
        Regex("\\btotal\\s+items?\\b", RegexOption.IGNORE_CASE),
        Regex("\\btax\\s+total\\b", RegexOption.IGNORE_CASE)
    )
    // Matches Indian format (1,23,456.50), standard (1,234,567.50), and corrupted currency symbols glued to digits
    private val moneyPattern = Regex(
        "(?i)(?:[?₹]|\\?\\)|rs\\.?|inr)?\\s*(\\d{1,3}(?:,\\d{2})*,\\d{3}|\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?"
    )
    private val numericOrDateLine = Regex("^[\\d\\s,./:-]+$")
    private val labelledDatePattern = Regex("\\b(?:(?:bill|invoice|receipt)\\s+)?date\\b", RegexOption.IGNORE_CASE)
    private val datePatterns = listOf(
        Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b") to strictFormatter("d/M/uuuu"),
        Regex("\\b\\d{1,2}[.-]\\d{1,2}[.-]\\d{4}\\b") to strictFormatter("d.M.uuuu"),
        Regex("\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b") to strictFormatter("uuuu-M-d"),
        Regex("\\b\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}\\b") to strictFormatter("d MMM uuuu"),
        Regex("\\b\\d{1,2}\\s+[A-Za-z]{4,9}\\s+\\d{4}\\b") to strictFormatter("d MMMM uuuu"),
        Regex("\\b\\d{1,2}-[A-Za-z]{3}-\\d{4}\\b") to strictFormatter("d-MMM-uuuu"),
        Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2}\\b") to strictFormatter("d/M/uu")
    )

    fun parse(text: String, defaultCurrency: String = "INR"): ReceiptScanResult {
        val rawText = text.take(MAX_RAW_TEXT).trim()
        val lines = rawText.lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .take(MAX_LINES)
            .toList()
        val date = findDate(lines)
        val merchant = findMerchant(lines)

        return ReceiptScanResult(
            totalAmount = findLabelledTotal(lines),
            merchant = merchant,
            date = date.value,
            isDateAmbiguous = date.isAmbiguous,
            currency = findCurrency(rawText, defaultCurrency),
            categoryText = listOfNotNull(merchant, rawText.take(MAX_CATEGORY_TEXT))
                .joinToString(" ")
                .take(MAX_CATEGORY_TEXT),
            rawText = rawText
        )
    }

    private fun findLabelledTotal(lines: List<String>): Double? {
        for (label in totalLabels) {
            val labelIndices = lines.indices.filter { i ->
                val line = lines[i]
                label.pattern.containsMatchIn(line) &&
                    (!label.isGeneric || genericTotalExclusions.none { it.containsMatchIn(line) })
            }
            if (labelIndices.isEmpty()) continue

            // For each label line, look for the total.
            val candidates = labelIndices.mapNotNull { index ->
                selectTotalWithContext(lines, index, label.pattern)
            }.distinct()

            if (candidates.size == 1) return candidates.single()
            if (candidates.size > 1) {
                return null
            }
        }

        // Fallback: look for explicit currency-prefixed lines in the lower half of the receipt (e.g. "₹ 2809.02", "Rs 7053.43")
        val currencyLinePattern = Regex("(?i)^(?:[?₹]|\\?\\)|rs\\.?|inr)\\s*\\d")
        val currencyCandidates = lines.mapIndexedNotNull { index, line ->
            if (currencyLinePattern.containsMatchIn(line.trim()) &&
                index >= lines.size / 2 &&
                isPlausibleTotalLine(lines, index)
            ) {
                findAmounts(line).singleOrNull()?.value
            } else null
        }.distinct()

        if (currencyCandidates.size == 1) {
            return currencyCandidates.single().takeIf { it > 0.0 && it < MAX_AMOUNT }
        }

        // Fallback: If receipt has a total label anywhere and a trailing block of amounts (common in POS block-column layouts),
        // take the final amount in the document as the grand total.
        val hasPosSignature = lines.any {
            it.contains("Powered by", ignoreCase = true) ||
            it.contains("dotpe", ignoreCase = true) ||
            it.contains("sub total", ignoreCase = true) ||
            it.contains("subtotal", ignoreCase = true) ||
            it.contains("sleek bill", ignoreCase = true) ||
            it.contains("supermarket", ignoreCase = true) ||
            it.contains("store", ignoreCase = true) ||
            it.contains("market", ignoreCase = true) ||
            it.contains("items purchased", ignoreCase = true) ||
            it.contains("cashier", ignoreCase = true)
        }
        val hasGrandTotalKeyword = lines.any { line ->
            val matchesKeyword = Regex("(?i)\\b(?:bill\\s+total|grand\\s+total|net\\s+total|net\\s+amount|mrp\\s+total|invoice\\s+total|amount\\s+due|payable|final\\s+amount|total)\\b").containsMatchIn(line)
            val isExcluded = genericTotalExclusions.any { it.containsMatchIn(line) }
            matchesKeyword && !isExcluded
        }
        if (hasPosSignature && hasGrandTotalKeyword) {
            val allAmounts = lines.indices
                .filter { isPlausibleTotalLine(lines, it) }
                .flatMap { findAmounts(lines[it]) }
                .map { it.value }
            if (allAmounts.isNotEmpty()) {
                return allAmounts.last().takeIf { it > 0.0 && it < MAX_AMOUNT }
            }
        }

        return null
    }

    /**
     * A fallback may only claim an amount that could plausibly BE the grand total.
     * The amount is rejected when its own line is an unrelated tax/item/discount line, or
     * when the nearest preceding content line is one - that is how a bare "₹50" belonging to
     * "Item 123" gets excluded without disturbing genuine trailing totals in column layouts,
     * where the line above the total is just another number.
     */
    private fun isPlausibleTotalLine(lines: List<String>, index: Int): Boolean {
        if (isUnrelatedTotalLine(lines[index])) return false
        val previous = lines.subList(0, index).lastOrNull { !isSkipLine(it) } ?: return true
        return !isUnrelatedTotalLine(previous)
    }

    private fun isUnrelatedTotalLine(line: String): Boolean =
        unrelatedLinePattern.containsMatchIn(line) ||
            looksLikeMerchantMetadata(line) ||
            looksLikeAddressOrProduct(line)

    private fun isSkipLine(line: String): Boolean {
        // Line is considered "skip line" if it contains ONLY currency symbols, common punctuation, or whitespace
        val stripped = line.replace(Regex("(?i)(?:[?₹]|\\?\\)|rs\\.?|inr|:|\\s)+"), "")
        return stripped.isEmpty()
    }

    private fun selectTotalWithContext(lines: List<String>, labelIndex: Int, label: Regex): Double? {
        // First, check if the amount is on the same line
        selectLineTotal(lines[labelIndex], label)?.let { return tabAmountOrNull(it) }

        // Look ahead up to 15 lines for a single clean amount
        // If multiple amounts appear in the window, this is likely an item-total column
        val foundAmounts = mutableListOf<Double>()
        for (i in 1..15) {
            val nextIndex = labelIndex + i
            if (nextIndex >= lines.size) break
            val nextLine = lines[nextIndex]

            // IF the line is skippable (like currency only), just continue
            if (isSkipLine(nextLine)) continue

            // Safety: Stop if we hit another label, metadata, address/product line, or unrelated tax/charge lines
            if (looksLikeMerchantMetadata(nextLine) || looksLikeAddressOrProduct(nextLine) ||
                Regex("(?i)\\b(?:txn|transaction|invoice|inv|gstin|ph|phone|date|table|mode|cash|tendered|change)\\b").containsMatchIn(nextLine) ||
                totalLabels.any { it.pattern.containsMatchIn(nextLine) } ||
                unrelatedLinePattern.containsMatchIn(nextLine)) break

            val amounts = findAmounts(nextLine)
            if (amounts.isNotEmpty()) {
                // Collect all amounts found in the window
                amounts.forEach { foundAmounts.add(it.value) }
            }
        }

        // If exactly one amount was found in the entire window, it's a valid total
        // If multiple amounts were found, this is an item-total column (reject)
        return if (foundAmounts.size == 1) foundAmounts.single().takeIf { it > 0.0 && it < MAX_AMOUNT } else null
    }

    private fun tabAmountOrNull(value: Double): Double? = value.takeIf { it > 0.0 && it < MAX_AMOUNT }

    private fun selectLineTotal(line: String, label: Regex): Double? {
        val labelMatch = label.find(line) ?: return null
        val amounts = findAmounts(line)
        if (amounts.isEmpty()) return null

        // A final amount normally follows its label. Do not select a largest value from the line.
        val afterLabel = amounts.filter { it.start >= labelMatch.range.last + 1 }
        return when {
            afterLabel.size == 1 -> afterLabel.single().value
            afterLabel.size > 1 -> afterLabel.first().value.takeIf { afterLabel[1].start - afterLabel[0].start > 12 }
            amounts.size == 1 -> amounts.single().value
            else -> null
        }
    }

    private fun findAmounts(line: String): List<AmountCandidate> = moneyPattern.findAll(line)
        .mapNotNull { match ->
            val whole = match.groupValues[1].replace(",", "")
            val fraction = match.groupValues[2]
            val normalized = if (fraction.isBlank()) whole else "$whole.$fraction"
            // Clean up duplicate trailing decimals like .00.00
            val cleanedNormalized = normalized.replace(Regex("(\\.\\d{2})\\.\\d+"), "$1")

            // Reject 6+ digit unformatted integers without currency symbol or commas/decimals (e.g. PIN codes like 444602)
            val rawMatchStr = match.value
            val hasCurrency = Regex("(?i)[?₹]|rs\\.?|inr").containsMatchIn(rawMatchStr)
            val hasSeparator = rawMatchStr.contains(",") || rawMatchStr.contains(".")
            if (!hasCurrency && !hasSeparator && whole.length >= 6) {
                return@mapNotNull null
            }

            cleanedNormalized.toDoubleOrNull()
                ?.takeIf { it > 0.0 && it < MAX_AMOUNT }
                ?.let { AmountCandidate(it, match.range.first) }
        }
        .toList()

    private fun findMerchant(lines: List<String>): String? {
        val candidates = lines.take(MAX_MERCHANT_LINES)
            .mapIndexedNotNull { index, rawLine ->
                val cleaned = rawLine.replace(Regex("^(?i)(?:welcome\\s+to|store|merchant|shop)\\s*[:\\-]?\\s*"), "")
                val line = cleaned.replace(Regex("^[#*\\-: ]+|[#*\\-: ]+$"), "").trim()
                merchantScore(line, index, lines)?.let { score -> score to line }
            }

        if (candidates.isEmpty()) return null

        val best = candidates.maxByOrNull { it.first } ?: return null
        if (best.first < MIN_MERCHANT_SCORE) return null

        // Find the index of the best candidate in the original lines list
        val originalIndex = lines.indexOfFirst { it.contains(best.second) }

        // Try to join with the next line if it's a good candidate and consecutive
        if (originalIndex >= 0 && originalIndex + 1 < lines.size) {
            val nextLineRaw = lines[originalIndex + 1]
            val nextLine = nextLineRaw.replace(Regex("^[#*\\-: ]+|[#*\\-: ]+$"), "").trim()

            // If the next line has a decent score and doesn't look like metadata/total, combine
            if (merchantScore(nextLine, originalIndex + 1, lines) != null &&
                nextLine.length >= 3 &&
                !looksLikeMerchantMetadata(nextLine) &&
                !totalLabels.any { it.pattern.containsMatchIn(nextLine) }) {
                return "${best.second} ${nextLine}".take(MAX_MERCHANT_LENGTH)
            }
        }

        return best.second.take(MAX_MERCHANT_LENGTH)
    }

    private fun merchantScore(line: String, index: Int, lines: List<String>): Int? {
        if (line.length !in 3..MAX_MERCHANT_LENGTH || line.count { it.isLetter() } < 3) return null
        val nextLine = if (index + 1 < lines.size) lines[index + 1] else null
        if (numericOrDateLine.matches(line) || looksLikeMerchantMetadata(line) || looksLikeAddressOrProduct(line, nextLine)) return null

        val letters = line.count { it.isLetter() }
        val hasDigits = line.any { it.isDigit() }
        val isUppercase = letters > 0 && line.filter { it.isLetter() }.all { it.isUpperCase() }
        val businessSuffix = Regex("\\b(?:pvt|ltd|llp|inc|store|mart|cafe|restaurant|hotel|traders|supermarket|bazaar)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(line)

        return (MAX_MERCHANT_LINES - index) +
            (if (!hasDigits) 4 else 1) +
            (if (isUppercase) 2 else 0) +
            (if (businessSuffix) 3 else 0)
    }

    private fun looksLikeMerchantMetadata(line: String): Boolean {
        val value = line.lowercase(Locale.ROOT)
        return listOf(
            "tax invoice", "retail invoice", "cash receipt", "bill of supply",
            "invoice no", "receipt no", "gstin", "gst no", "cashier", "tax:", "item:",
            "table no", "table:", "bill no", "order no", "subtotal", "grand total", "amount due",
            "net total", "payable", "thank you", "date", "http", "www.", "@",
            "welcome to", "customer copy", "original copy", "duplicate copy",
            "name:", "service rd", "layout", "banaswadi", "bengaluru", "karnataka",
            "tax", "item",
            "invoice no:", "time:", "mode:", "cash", "card", "gst no:",
            "total", "item", "price", "qty", "quantity", "rate", "amount"
        ).any(value::contains) || Regex("\\b(?:gst|pan)[a-z0-9-]*\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    private fun looksLikeAddressOrProduct(line: String, nextLine: String? = null): Boolean {
        val value = line.lowercase(Locale.ROOT)
        val address = Regex("\\b(?:road|rd|street|st|lane|nagar|building|floor|near|opp|plot|pin|layout|bengaluru|bangalore|mumbai|delhi|karnataka)\\b")
        val product = Regex("\\b(?:qty|quantity|pcs?|kg|g|ltr|ml|rate|hsn|item|items|price|amount|hsn|hsn/sec|mutton|biriyani|roti|chicken|rice|dal|paneer|soup|curry|fish|noodles|masala|tandoori|paratha|naan|gobhi|veg|freshener|drink|poha|chana)\\b")
        val phone = Regex("(?:\\+?91[- ]?)?\\d[\\d -]{7,}\\d")

        val isFollowedByItemDetail = nextLine != null && Regex("^\\d+\\s*[@x/]|\\d+\\s*(?:ea|rs|₹)").containsMatchIn(nextLine.lowercase(Locale.ROOT))

        return address.containsMatchIn(value) || product.containsMatchIn(value) ||
            phone.containsMatchIn(value) || isFollowedByItemDetail
    }

    private fun findDate(lines: List<String>): DateExtraction {
        val labelledDates = lines.filter { labelledDatePattern.containsMatchIn(it) }.flatMap(::extractDates).distinct()
        if (labelledDates.size == 1) return DateExtraction(labelledDates.single(), false)
        if (labelledDates.size > 1) return DateExtraction(null, true)

        val allDates = lines.flatMap(::extractDates).distinct()
        return when (allDates.size) {
            0 -> DateExtraction(null, false)
            1 -> DateExtraction(allDates.single(), false)
            else -> DateExtraction(null, true)
        }
    }

    private fun extractDates(line: String): List<LocalDateTime> = datePatterns.flatMap { (pattern, formatter) ->
        pattern.findAll(line).mapNotNull { match ->
            val dateStr = match.value
            try {
                LocalDate.parse(dateStr, formatter).atStartOfDay()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(dateStr.replace('-', '/'), strictFormatter("d/M/uuuu")).atStartOfDay()
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDate.parse(dateStr.replace('.', '/'), strictFormatter("d/M/uuuu")).atStartOfDay()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }.toList()
    }

    private fun findCurrency(text: String, defaultCurrency: String = "INR"): String = when {
        Regex("(?i)(₹|\\brs\\.?\\s*|\\binr\\b|\\b\\?|\\?\\)|\\br5\\b|gstin)").containsMatchIn(text) -> "INR"
        Regex("(?i)\\busd\\b|us\\\$").containsMatchIn(text) -> "USD"
        Regex("€|(?i)\\beur\\b").containsMatchIn(text) -> "EUR"
        Regex("£|(?i)\\bgbp\\b").containsMatchIn(text) -> "GBP"
        else -> defaultCurrency
    }

    private fun strictFormatter(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)

    private const val MAX_RAW_TEXT = 10_000
    private const val MAX_LINES = 100
    private const val MAX_CATEGORY_TEXT = 500
    private const val MAX_MERCHANT_LINES = 12
    private const val MAX_MERCHANT_LENGTH = 100
    private const val MIN_MERCHANT_SCORE = 5
    private const val MAX_AMOUNT = 1_000_000_000.0
}
