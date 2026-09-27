package com.expensetracker.ui.screens.analytics

import com.expensetracker.domain.engine.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Guards the x-axis of the spending trend chart.
 *
 * The bug: from the 20th of a month onwards, two-digit days were drawn as their
 * first character alone — "21" showed as "2" — while 1..19 looked fine. Nothing
 * in the formatter was wrong. The label string was always the full day; the
 * axis had divided itself into equal columns and then handed each label no more
 * room than one of them, which in a 20-to-31 day month is about nine dp, less
 * than two digits at labelSmall. The number was cut to fit its slot.
 *
 * Two things are therefore pinned here: that the formatter hands out whole day
 * numbers, and that the axis drops a label entirely rather than narrowing one.
 */
class XAxisLabelTest {

    /**
     * The formatter log: input day -> output label, for every day of a 31 day
     * month. Run this to see the 20+ values the bug report was about printed
     * next to the 1..19 values that were unaffected.
     */
    @Test
    fun `x axis formatter logs the full day for every day of a 31 day month`() {
        val today = LocalDate.of(2026, 1, 31)
        val points = emptyList<DailySpending>().toChartPoints(ReportPeriod.MONTHLY, today)

        println("x-axis label formatter, MONTHLY ending $today (${points.size} columns):")
        points.forEachIndexed { index, point ->
            val day = index + 1
            val band = if (day >= 20) "TWO-DIGIT" else "single  "
            println("  day=%2d (%s) -> label=\"%s\"  callout=\"%s\"".format(day, band, point.label, point.fullLabel))
        }

        assertEquals("A 31 day month must have a column per day", 31, points.size)
        assertEquals(
            "A two-digit day was shortened somewhere in the formatter",
            (1..31).map { it.toString() },
            points.map { it.label }
        )
    }

    /**
     * The same check on real data rather than an empty month: the day a
     * transaction lands on and the day the axis prints are the same number, so
     * the bug really was display-only.
     */
    @Test
    fun `a transaction on the 21st is drawn under a column labelled 21`() {
        val today = LocalDate.of(2026, 1, 22)
        val spending = (20..22).map { DailySpending(LocalDate.of(2026, 1, it).toString(), 500.0) }
        val points = spending.toChartPoints(ReportPeriod.MONTHLY, today)

        val labelled = points.filter { it.amount > 0.0 }
        println("x-axis formatter with real data: " + labelled.joinToString { "${it.fullLabel} -> \"${it.label}\"" })

        assertEquals(listOf("20", "21", "22"), labelled.map { it.label })
        assertEquals(listOf(500.0, 500.0, 500.0), labelled.map { it.amount })
    }

    /**
     * The regression itself. A 31 day month on a phone leaves roughly nine dp
     * per column, which is narrower than a two-digit label: there is no way to
     * draw all of them, and the old axis resolved that by cutting each one
     * down. Now it resolves it by dropping labels whole, keeping the endpoints.
     */
    @Test
    fun `a month too narrow for two digit labels drops labels instead of cutting them`() {
        val plotWidth = 282f // card interior less the 46dp axis gutter, on a 360dp phone
        val days = 31
        val labelWidth = 12f // two digits at labelSmall
        val gap = 6f

        val slot = plotWidth / days
        val centres = (0 until days).map { slot * (it + 0.5f) }
        val widths = List(days) { labelWidth }

        assertTrue(
            "The premise of this test is that a two-digit label does not fit its slot",
            labelWidth > slot
        )

        val keep = xLabelIndices(centres, widths, gap)

        assertTrue("The first column must keep its label", 0 in keep)
        assertTrue("The last column must keep its label", days - 1 in keep)
        assertTrue("The axis should thin out rather than draw all 31", keep.size < days)
        assertKeptLabelsAreWhole(keep, days, gap)
    }

    /** No two kept labels touch, and none of them was narrowed to fit. */
    private fun assertKeptLabelsAreWhole(keep: Set<Int>, days: Int, gap: Float) {
        val plotWidth = 282f
        val slot = plotWidth / days
        val labelWidth = 12f
        val kept = keep.sorted()

        for (i in 1 until kept.size) {
            val previousRight = slot * (kept[i - 1] + 0.5f) + labelWidth / 2f
            val left = slot * (kept[i] + 0.5f) - labelWidth / 2f
            assertTrue(
                "labels ${kept[i - 1]} and ${kept[i]} would overlap",
                left >= previousRight + gap
            )
        }
        assertTrue("An axis should never end up empty", kept.size >= 2)
    }

    /** The early days of a month, where one column per label genuinely fits. */
    @Test
    fun `an axis with room to spare keeps every label`() {
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val plotWidth = 282f
        val slot = plotWidth / labels.size
        val widths = List(labels.size) { 22f }

        val centres = labels.indices.map { slot * (it + 0.5f) }

        assertEquals(labels.indices.toSet(), xLabelIndices(centres, widths, 6f))
    }

    /**
     * One column and a plot too narrow even for that: the endpoints still get
     * their labels, because a lone "1" is the least wrong answer.
     */
    @Test
    fun `a cramped axis still labels both endpoints`() {
        val keep = xLabelIndices(listOf(4f, 8f), listOf(12f, 12f), 6f)
        assertEquals(setOf(0, 1), keep)
    }

    @Test
    fun `an empty axis has no labels`() {
        assertEquals(emptySet<Int>(), xLabelIndices(emptyList(), emptyList(), 6f))
    }

    // ── Which columns get named ──────────────────────────────────────────

    /**
     * The month axis names its two ends and stops.
     *
     * This is the rule that replaced "draw them all and drop what does not
     * fit". Dropping, on a 27-day month, produced 1 2 3 4 5 6 7 8 9 11 13 15 17
     * 19 21 23 25 — single-digit days a third of an inch apart, two-digit days
     * twice that far apart, and 10 and 12 missing entirely because the gap rule
     * had nowhere to put them. The user reads that as noise, not as a scale.
     */
    @Test
    fun `a month names only its first and last day`() {
        val today = LocalDate.of(2026, 9, 27)
        val points = emptyList<DailySpending>().toChartPoints(ReportPeriod.MONTHLY, today)

        val ticks = xAxisTicks(points, ReportPeriod.MONTHLY)

        assertEquals(
            "A month names where it starts and where it ends; the rest is one tap away",
            listOf(0 to "1", 26 to "27"),
            ticks
        )
    }

    /** The days in between are still there to be tapped — the axis just stops naming them. */
    @Test
    fun `the days the axis no longer names still carry their own dates`() {
        val today = LocalDate.of(2026, 9, 27)
        val points = emptyList<DailySpending>().toChartPoints(ReportPeriod.MONTHLY, today)

        val named = xAxisTicks(points, ReportPeriod.MONTHLY).map { it.first }.toSet()
        val reachable = points.indices.filter { it !in named }

        assertEquals(25, reachable.size)
        // Each of those is what the callout prints when its column is tapped.
        // The month is spelled by the platform's own CLDR data ("Sep" here,
        // "Sept" on some JDKs), so only the day is pinned.
        assertEquals("7", points[6].shortLabel.substringBefore(" "))
        assertTrue(
            "The callout needs a whole date, not a bare day number",
            points[6].fullLabel.contains(" 7 ") && points[6].fullLabel.length > points[6].shortLabel.length
        )
    }

    /** A week is seven columns, which fit whole, so it is still named in full. */
    @Test
    fun `a week still names every day`() {
        val today = LocalDate.of(2026, 9, 27)
        val points = emptyList<DailySpending>().toChartPoints(ReportPeriod.WEEKLY, today)

        assertEquals(points.indices.toList(), xAxisTicks(points, ReportPeriod.WEEKLY).map { it.first })
    }

    /** A year is twelve columns, which also fit. */
    @Test
    fun `a year still names every month`() {
        val today = LocalDate.of(2026, 9, 27)
        val points = emptyList<DailySpending>().toChartPoints(ReportPeriod.YEARLY, today)

        assertEquals(points.indices.toList(), xAxisTicks(points, ReportPeriod.YEARLY).map { it.first })
    }

    /** On the 3rd the month has three columns, and its two ends are 1 and 3. */
    @Test
    fun `a month that has only begun names its first and last day`() {
        val today = LocalDate.of(2026, 9, 3)
        val points = emptyList<DailySpending>().toChartPoints(ReportPeriod.MONTHLY, today)

        assertEquals(listOf(0 to "1", 2 to "3"), xAxisTicks(points, ReportPeriod.MONTHLY))
    }

    /** A single-column month — the 1st — has one end, and names it once. */
    @Test
    fun `a single column month names its only day once`() {
        val today = LocalDate.of(2026, 9, 1)
        val points = emptyList<DailySpending>().toChartPoints(ReportPeriod.MONTHLY, today)

        assertEquals(listOf(0 to "1"), xAxisTicks(points, ReportPeriod.MONTHLY))
    }

    @Test
    fun `an empty chart has no ticks`() {
        assertEquals(emptyList<Pair<Int, String>>(), xAxisTicks(emptyList(), ReportPeriod.MONTHLY))
    }
}
