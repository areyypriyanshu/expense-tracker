package com.expensetracker.domain.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared date handling for the app: one list of accepted input formats and one
 * definition of the date range a user is allowed to record.
 *
 * Note: receipt OCR parsing deliberately keeps its own strict, per-format
 * resolvers (see ReceiptParser) because it needs to know which pattern matched.
 */
object DateUtil {

    /**
     * Input formats accepted from bank CSV exports and UPI SMS bodies.
     * Ordered: date-and-time first, then date-only. Formats that can be confused
     * with each other (dd/MM vs MM/dd) keep their original relative order.
     */
    val INPUT_FORMATS: List<DateTimeFormatter> = listOf(
        "dd/MM/yyyy HH:mm",
        "dd-MM-yyyy HH:mm",
        "dd MMM yyyy, HH:mm",
        "dd/MM/yy HH:mm",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd.MM.yyyy",
        "yyyy-MM-dd",
        "MM/dd/yyyy",
        "dd MMM yyyy",
        "dd MMMM yyyy"
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    /** Display format used when echoing a parsed receipt date back to the user. */
    val RECEIPT_DISPLAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)

    /** Transactions older than this are rejected (guards against bad imports). */
    private val MIN_DATE: LocalDateTime get() = LocalDateTime.now().minusYears(2)

    /** Slight clock skew is tolerated so "today" is always valid. */
    private val MAX_DATE: LocalDateTime get() = LocalDateTime.now().plusDays(1)

    /**
     * True when [date] is inside the range a transaction may be recorded for.
     */
    fun isWithinAllowedRange(date: LocalDateTime): Boolean =
        !date.isAfter(MAX_DATE) && !date.isBefore(MIN_DATE)

    /**
     * Pulls [date] into the allowed range instead of rejecting it, for the
     * date picker where silently moving the selection is friendlier than an error.
     */
    fun clampToAllowedRange(date: LocalDateTime): LocalDateTime = when {
        date.isAfter(MAX_DATE) -> MAX_DATE
        date.isBefore(MIN_DATE) -> MIN_DATE
        else -> date
    }

    /**
     * Parses [value] against every supported [INPUT_FORMATS] entry, accepting both
     * full date-times and bare dates (a bare date is anchored at start of day).
     * Returns null when nothing matches.
     */
    fun parseDateTimeOrNull(value: String): LocalDateTime? {
        val candidate = value.trim()
        if (candidate.isEmpty()) return null

        for (formatter in INPUT_FORMATS) {
            try {
                return LocalDateTime.parse(candidate, formatter)
            } catch (_: Exception) {
                try {
                    return LocalDate.parse(candidate, formatter).atStartOfDay()
                } catch (_: Exception) {
                    // Try the next format
                }
            }
        }
        return null
    }
}
