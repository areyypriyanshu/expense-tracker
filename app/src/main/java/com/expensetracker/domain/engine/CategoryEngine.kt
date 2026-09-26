package com.expensetracker.domain.engine

import com.expensetracker.data.model.Category

/**
 * Single source of truth for transaction categorization.
 *
 * Every feature that turns free text into a category name — CSV import, UPI SMS
 * parsing, receipt scanning and the on-device assistant — goes through here, so a
 * new keyword or category only has to be added once.
 */
class CategoryEngine {

    companion object {

        /**
         * Canonical category names accepted by the data layer.
         * Any category outside this list is coerced to [FALLBACK_CATEGORY] on write.
         */
        val SUPPORTED_CATEGORIES = listOf(
            "Food & Dining", "Transportation", "Shopping", "Entertainment",
            "Bills & Utilities", "Healthcare", "Education", "Groceries",
            "Personal Care", "Travel", "Income", "Investment", "Gift", "Other"
        )

        const val FALLBACK_CATEGORY = "Other"

        /**
         * Keyword rules in priority order — the first category with a matching keyword wins,
         * so more specific categories are listed before broader ones (e.g. "BookMyShow" hits
         * Entertainment before Education's "book" could claim it).
         */
        private val KEYWORD_RULES: List<Pair<String, List<String>>> = listOf(
            "Food & Dining" to listOf(
                "swiggy", "zomato", "dominos", "mcdonalds", "pizza", "restaurant", "cafe",
                "coffee", "food", "meal", "lunch", "dinner", "breakfast", "burger", "dining",
                "biryani", "biriyani", "mutton", "chicken", "paneer", "naan", "curry", "rice",
                "roti", "thali", "tandoor", "paratha", "dhaba", "kitchen", "darshini", "bhavan"
            ),
            "Transportation" to listOf(
                "uber", "lyft", "ola", "rapido", "auto", "taxi", "metro", "rail", "train",
                "bus", "fuel", "petrol", "diesel", "parking", "fastag",
                "transport", "transportation"
            ),
            "Shopping" to listOf(
                "amazon", "flipkart", "myntra", "shopping", "shop", "store", "mall",
                "retail", "ebay", "walmart", "ajio", "meesho"
            ),
            "Entertainment" to listOf(
                "netflix", "hotstar", "prime", "spotify", "movie", "youtube", "game",
                "concert", "ticket", "streaming", "bookmyshow"
            ),
            "Bills & Utilities" to listOf(
                "electricity", "electric", "power", "bescom", "reliance energy", "water",
                "bwssb", "municipal", "gas", "indane", "bharat gas", "bill", "utility",
                "rent", "recharge", "broadband", "internet", "phone", "airtel", "jio",
                "vodafone", "bsnl", "loan", "emi", "insurance", "premium"
            ),
            "Healthcare" to listOf(
                "pharmacy", "hospital", "doctor", "medical", "medicine", "health",
                "clinic", "apollo", "practo"
            ),
            "Education" to listOf(
                "school", "college", "university", "fee", "course", "book", "education",
                "tuition", "tution", "udemy", "coursera"
            ),
            "Groceries" to listOf(
                "grocery", "supermarket", "bigbasket", "market", "vegetable", "fruit",
                "kirana", "dmart", "zepto", "blinkit", "instamart"
            ),
            "Personal Care" to listOf(
                "salon", "gym", "fitness", "spa", "beauty", "barber", "haircut", "cosmetic"
            ),
            "Travel" to listOf(
                "hotel", "flight", "travel", "booking", "vacation", "trip", "irctc",
                "makemytrip", "goibibo", "airbnb"
            ),
            "Income" to listOf(
                "salary", "freelance", "earning", "credited", "deposit", "income",
                "payout", "dividend", "interest"
            ),
            "Investment" to listOf(
                "investment", "mutual fund", "stock", "sip"
            )
        )

        private val REFUND_PATTERN = Regex("refund|cashback|reversal", RegexOption.IGNORE_CASE)

        private const val MAX_SCAN_LENGTH = 500

        /**
         * True when the text describes money coming back rather than a new expense
         * (refund, cashback or a reversal). Used to split credits from genuine income.
         */
        fun isRefundOrCashback(text: String): Boolean =
            REFUND_PATTERN.containsMatchIn(text)

        /**
         * Maps free text to a canonical category name using [KEYWORD_RULES].
         * Returns [FALLBACK_CATEGORY] when nothing matches.
         */
        fun categorizeText(text: String): String {
            if (text.isBlank()) return FALLBACK_CATEGORY
            val lowerText = text.take(MAX_SCAN_LENGTH).lowercase()

            for ((category, keywords) in KEYWORD_RULES) {
                if (keywords.any { lowerText.contains(it) }) return category
            }
            return FALLBACK_CATEGORY
        }

        /**
         * True when [category] is a name the data layer will accept unchanged.
         */
        fun isSupportedCategory(category: String): Boolean =
            category in SUPPORTED_CATEGORIES

        /**
         * Resolves a user-supplied category phrase to a canonical name, e.g. "food" ->
         * "Food & Dining". Text that matches no rule is passed through capitalised, so
         * user-defined categories such as "Coffee" survive the round trip.
         */
        fun resolveCategoryName(raw: String): String {
            val guess = categorizeText(raw)
            return if (guess == FALLBACK_CATEGORY) {
                raw.replaceFirstChar { it.uppercase() }
            } else {
                guess
            }
        }
    }

    /**
     * Categorises a note/description against the user's own categories first (so their
     * custom keywords always win), then falls back to the built-in [KEYWORD_RULES] and only
     * returns the built-in match if that category actually exists in [categories].
     */
    fun autoCategorize(note: String, categories: List<Category>): String {
        if (note.isBlank()) return FALLBACK_CATEGORY

        val boundedNote = note.take(MAX_SCAN_LENGTH).lowercase()

        for (category in categories) {
            val keywords = category.keywords.take(MAX_SCAN_LENGTH).split(",").map { it.trim().lowercase().take(50) }
            for (keyword in keywords) {
                if (keyword.isNotEmpty() && boundedNote.contains(keyword)) {
                    return category.name
                }
            }
        }

        val builtIn = categorizeText(boundedNote)
        if (builtIn != FALLBACK_CATEGORY && categories.any { it.name == builtIn }) {
            return builtIn
        }
        return FALLBACK_CATEGORY
    }

    fun getCategoryIcon(categoryName: String, categories: List<Category>): String {
        val boundedName = categoryName.take(50)
        return categories.find { it.name == boundedName }?.icon ?: "more_horiz"
    }
}
