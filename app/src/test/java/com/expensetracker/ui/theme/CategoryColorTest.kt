package com.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the category → colour mapping.
 *
 * This is the invariant that was broken: the same category could arrive in
 * different colours on different screens, and two different categories could
 * share one. Both are invisible in a single screenshot and obvious the moment
 * two screens are put side by side, which is exactly why they went unnoticed.
 */
class CategoryColorTest {

    private val builtInCategories = listOf(
        "Food & Dining", "Transportation", "Shopping", "Entertainment",
        "Bills & Utilities", "Healthcare", "Education", "Personal Care",
        "Travel", "Groceries", "Income", "Other"
    )

    @Test
    fun `every built-in category has its own colour`() {
        val colors = builtInCategories.map { getCategoryColor(it) }
        assertEquals(
            "Two built-in categories share a colour: ${colors.size} categories produced " +
                "${colors.toSet().size} distinct colours",
            builtInCategories.size,
            colors.toSet().size
        )
    }

    @Test
    fun `colour does not depend on anything but the name`() {
        // The whole point: same category, same colour, always. A mapping that
        // keyed on rank or hash made this change between screens and periods.
        builtInCategories.forEach { category ->
            assertEquals(category, getCategoryColor(category), getCategoryColor(category))
        }
    }

    @Test
    fun `casing and surrounding whitespace do not create a new category`() {
        // Names arrive from CSV imports and SMS parsing, not just from the
        // seeded list, so "  food & dining " has to resolve to the same colour.
        assertEquals(getCategoryColor("Food & Dining"), getCategoryColor("food & dining"))
        assertEquals(getCategoryColor("Food & Dining"), getCategoryColor("  FOOD & DINING  "))
    }

    @Test
    fun `user-created categories are neutral and do not borrow a built-in colour`() {
        // Distinct names, so a hash would have coloured them differently and
        // implied a mapping that does not exist.
        listOf("Gift", "Pet", "Travel Fund", "Reimbursement").forEach { custom ->
            assertEquals(NeutralCategoryColor, getCategoryColor(custom))
        }
        assertNotEquals(getCategoryColor("Travel"), getCategoryColor("Travel Fund"))
    }

    @Test
    fun `aggregate colour is distinguishable from the neutral category colour`() {
        // A donut can place "Other" and an "Other (3)" aggregate side by side.
        assertNotEquals(NeutralCategoryColor, AggregateCategoryColor)
    }

    @Test
    fun `the documented anchors keep their intended colours`() {
        // Food & Dining is the category that dominates most months, so it holds
        // the leading blue the palette was chosen around.
        assertEquals(CategoryColors[0], getCategoryColor("Food & Dining"))
        // "Other" is the miscellaneous built-in and is deliberately the neutral
        // slot — it is the one built-in that shares its colour with a
        // user-created category, which is the point of it.
        assertEquals(NeutralCategoryColor, getCategoryColor("Other"))
    }

    @Test
    fun `only Other falls back to neutral among built-in categories`() {
        // A typo in the table would silently drop a real category to neutral.
        val neutralBuiltIns = builtInCategories.filter { getCategoryColor(it) == NeutralCategoryColor }
        assertEquals(listOf("Other"), neutralBuiltIns)
    }

    @Test
    fun `no two categories are close enough to be mistaken for each other`() {
        // Different is not the same as distinguishable. The palette shipped with
        // emerald/teal at ΔE 21.9 and violet/indigo at 14.5 — different values
        // that read as one colour. Comparing colour values for inequality would
        // have passed; only measuring the distance catches it.
        val pairs = builtInCategories.indices.flatMap { i ->
            (i + 1 until builtInCategories.size).map { j ->
                Triple(
                    builtInCategories[i],
                    builtInCategories[j],
                    deltaE(getCategoryColor(builtInCategories[i]), getCategoryColor(builtInCategories[j]))
                )
            }
        }
        val closest = pairs.minByOrNull { it.third }!!
        assertTrue(
            "${closest.first} and ${closest.second} are only ΔE ${"%.1f".format(closest.third)} apart — " +
                "close enough that a user reads them as the same colour",
            closest.third >= MIN_SEPARATION
        )
    }

    @Test
    fun `Income is not confusable with Transportation`() {
        // The pair that was actually reported: both had come out green, and it
        // went unnoticed because no single screen ever showed them together.
        val gap = deltaE(getCategoryColor("Income"), getCategoryColor("Transportation"))
        assertTrue("Income and Transportation are ΔE ${"%.1f".format(gap)} apart", gap >= 45.0)
    }

    private companion object {
        /**
         * CIE76 ΔE in CIELAB. Roughly: under 10 is a just-noticeable difference,
         * under 25 is "different values, same colour" to most eyes, above 30 is
         * plainly distinct. The best achievable over every ordering of the
         * previous palette was 14.5, so this sits near the practical ceiling
         * rather than somewhere a well-meaning future edit can quietly fall
         * below.
         */
        const val MIN_SEPARATION = 24.0

        fun deltaE(a: Color, b: Color): Double {
            val (l1, a1, b1) = toLab(a)
            val (l2, a2, b2) = toLab(b)
            return kotlin.math.sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
        }

        fun toLab(c: Color): Triple<Double, Double, Double> {
            fun lin(v: Double): Double {
                val x = v / 255.0
                return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
            }
            val r = lin(c.red.toDouble())
            val g = lin(c.green.toDouble())
            val b = lin(c.blue.toDouble())
            val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
            fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116.0
            val fx = f(x)
            val fy = f(y)
            val fz = f(z)
            return Triple(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
        }
    }
}
