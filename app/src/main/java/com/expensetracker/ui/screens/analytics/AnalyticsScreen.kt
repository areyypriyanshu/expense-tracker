package com.expensetracker.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.expensetracker.ui.components.ScrollAwareBlurScrim
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.data.model.Transaction
import com.expensetracker.domain.engine.ReportPeriod
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.services.insights.InsightSeverity
import com.expensetracker.services.insights.SpendingInsight
import com.expensetracker.ui.components.AppHeader
import com.expensetracker.ui.components.SectionHeader
import com.expensetracker.ui.components.SmoothLinearProgressIndicator
import com.expensetracker.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    bottomContentPadding: Dp = 0.dp,
    viewModel: AnalyticsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                AppHeader(
                    title = "Analytics",
                    currency = uiState.baseCurrency,
                    todaySpend = uiState.todaySpend,
                    weeklySpend = uiState.weeklySpend,
                    monthlySpend = uiState.monthlySpend
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val listState = rememberLazyListState()
            // No colour map here on purpose. Every category takes its colour
            // from getCategoryColor, the same call the dashboard, the
            // transaction list and the category picker make — so a category
            // cannot be blue in this donut and green two taps away. An earlier
            // version ranked categories by size and coloured them by position,
            // which is what made the same category change colour between
            // screens and between periods.
            ScrollAwareBlurScrim(listState = listState) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp + bottomContentPadding)
                ) {
                    item { PeriodSelector(uiState.selectedPeriod, viewModel::onPeriodChange) }
                    item { SpendingOverviewCard(uiState) }
                    if (uiState.dailySpending.isNotEmpty()) {
                        item { SpendingChart(uiState.dailySpending, uiState.selectedPeriod, uiState.baseCurrency, Modifier.padding(16.dp)) }
                    }
                    if (uiState.categoryBreakdown.isNotEmpty()) {
                        item { DonutChartSection(uiState.categoryBreakdown, uiState.totalSpend, uiState.baseCurrency) }
                        item { SectionHeader(title = "By Category") }
                        items(uiState.categoryBreakdown.toList()) { (category, amount) ->
                            CategoryItem(
                                category = category,
                                amount = amount,
                                total = uiState.totalSpend,
                                currency = uiState.baseCurrency,
                                transactions = uiState.categoryTransactions[category] ?: emptyList(),
                                color = getCategoryColor(category)
                            )
                        }
                    }
                    if (uiState.insights.isNotEmpty()) {
                        item { SectionHeader(title = "Insights") }
                        items(uiState.insights.take(3)) { insight -> AnalyticsInsightCard(insight) }
                    }
                }
            } // end ScrollAwareBlurScrim
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selectedPeriod: ReportPeriod, onPeriodChange: (ReportPeriod) -> Unit) {
    val periods = listOf(ReportPeriod.WEEKLY, ReportPeriod.MONTHLY, ReportPeriod.YEARLY)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        periods.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selectedPeriod == period,
                onClick = { onPeriodChange(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(period.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SpendingOverviewCard(uiState: AnalyticsUiState) {
    val trendColor = when (uiState.spendTrend) {
        SpendTrend.DOWN -> Positive
        SpendTrend.UP -> Negative
        SpendTrend.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val trendIcon = when (uiState.spendTrend) {
        SpendTrend.UP -> Icons.AutoMirrored.Filled.TrendingUp
        SpendTrend.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
        SpendTrend.FLAT -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val animatedTotal by animateFloatAsState(
        targetValue = uiState.totalSpend.toFloat(),
        animationSpec = MotionTokens.progressTween(), label = "total"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total Spending", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        CurrencyService.formatAmount(animatedTotal.toDouble(), uiState.baseCurrency),
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.spendTrend != SpendTrend.FLAT || uiState.trendPercentage != 0.0) {
                    Surface(
                        color = trendColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(trendIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = trendColor)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${String.format("%.1f", abs(uiState.trendPercentage))}%",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = trendColor
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickPill("Daily Avg", CurrencyService.formatAmount(uiState.dailyAverage, uiState.baseCurrency), Modifier.weight(1f))
                QuickPill("Transactions", "${uiState.transactionCount}", Modifier.weight(1f))
                QuickPill("Top", uiState.topCategory.ifEmpty { "-" }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- Spending Trend Chart ---

/**
 * One column of the trend chart.
 *
 * [label] is what the x-axis prints when it names this column, [shortLabel] is
 * the same date in a form that fits on one line outside the plot (the "Peak"
 * pill), and [fullLabel] is the whole date, which the tap callout shows.
 */
internal data class ChartPoint(
    val label: String,
    val shortLabel: String,
    val fullLabel: String,
    val amount: Double
)

/** Plot proportions, in dp. Shared by the canvas and the overlay so the
 *  columns, the gridlines and the callout all agree on where things are. */
private val AXIS_GUTTER = 46.dp
private val PLOT_HEIGHT = 196.dp

/** Empty band above the tallest column, reserved for the tap callout so a
 *  full-height bar never pushes its own value off the top of the card. */
private val PLOT_TOP_INSET = 46.dp

/** Fixed so the callout's position can be solved in dp without measuring it. */
private val CALL_OUT_WIDTH = 118.dp

/** Room under the plot for the x-axis labels. */
private val X_AXIS_GUTTER = 10.dp

private const val AXIS_DIVISIONS = 4

/**
 * Turns the per-day totals into one column per unit of time, covering the
 * whole period whether or not anything was spent on it.
 *
 * The previous version only emitted days that had transactions, so a month with
 * spending on the 3rd, 19th and 27th drew three bars crammed side by side and
 * labelled them 3, 19, 27 — an axis where distance no longer means time. An
 * empty day is information ("nothing spent"), so it keeps its column.
 */
internal fun List<DailySpending>.toChartPoints(period: ReportPeriod, today: LocalDate): List<ChartPoint> {
    val byDate = associate { LocalDate.parse(it.date) to it.amount }
    val dayFmt = DateTimeFormatter.ofPattern("d")
    val weekFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val longFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val shortDayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val shortWeekFmt = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
    return when (period) {
        ReportPeriod.WEEKLY -> (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            ChartPoint(
                date.format(weekFmt),
                date.format(shortWeekFmt),
                date.format(longFmt),
                byDate[date] ?: 0.0
            )
        }
        ReportPeriod.MONTHLY -> (1..today.dayOfMonth).map { day ->
            val date = today.withDayOfMonth(day)
            ChartPoint(
                date.format(dayFmt),
                date.format(shortDayFmt),
                date.format(longFmt),
                byDate[date] ?: 0.0
            )
        }
        ReportPeriod.YEARLY -> (1..today.monthValue).map { month ->
            val total = byDate.filterKeys { YearMonth.from(it).monthValue == month && it.year == today.year }
                .values.sum()
            val firstOfMonth = today.withDayOfMonth(1).withMonth(month)
            ChartPoint(firstOfMonth.format(monthFmt), firstOfMonth.format(monthFmt), firstOfMonth.format(monthFmt), total)
        }
    }
}

/**
 * Which columns the x-axis names, and what it calls them.
 *
 * A month is up to 31 columns of bare day numbers, and no phone-width axis can
 * carry that many. The obvious answer — draw them all and drop whatever does not
 * fit — is what produced the axis this replaced: a run of single-digit days a
 * third of an inch apart, then a run of two-digit days twice that far apart,
 * with a label or two missing in the middle because the gap rule had nowhere to
 * put them. The result read as 1 2 3 4 5 6 7 8 9 11 13 15 17 19 21 23 25, which
 * is not a scale: two rhythms on one axis, and not even every day named.
 *
 * So a month names its two ends and nothing else — where the period started and
 * where it ended — and any day between them is one tap away, with the full date
 * in the callout. Two labels are also the only number that can be trusted to sit
 * under the right column, because there are enough of them to be exact.
 *
 * A week is seven columns and a year twelve, and both fit whole, so they are
 * still named in full.
 */
internal fun xAxisTicks(points: List<ChartPoint>, period: ReportPeriod): List<Pair<Int, String>> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) return listOf(0 to points[0].label)
    return when (period) {
        ReportPeriod.MONTHLY -> listOf(0 to points.first().label, points.lastIndex to points.last().label)
        ReportPeriod.WEEKLY, ReportPeriod.YEARLY -> points.mapIndexed { index, point -> index to point.label }
    }
}

/**
 * Rounds the top of the value axis up to a step a person would say out loud
 * (1, 2, 2.5 or 5 times a power of ten). Two things fall out of that: the
 * topmost gridline carries a round number instead of whatever the single worst
 * day happened to cost, and the tallest bar keeps a sliver of air under the
 * frame rather than touching it.
 */
private fun niceAxisMax(rawMax: Double): Double {
    if (rawMax <= 0.0) return AXIS_DIVISIONS.toDouble()
    val rough = rawMax / AXIS_DIVISIONS
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalised = rough / magnitude
    val step = when {
        normalised <= 1.0 -> 1.0
        normalised <= 2.0 -> 2.0
        normalised <= 2.5 -> 2.5
        normalised <= 5.0 -> 5.0
        else -> 10.0
    } * magnitude
    return AXIS_DIVISIONS * step
}

/** "₹12.5k" rather than "₹12500.00" — gridline labels have ~4 characters of room. */
private fun axisValueLabel(value: Double, symbol: String): String {
    val magnitude = abs(value)
    val (scaled, suffix) = when {
        magnitude >= 1_000_000 -> value / 1_000_000 to "M"
        magnitude >= 1_000 -> value / 1_000 to "k"
        else -> value to ""
    }
    val text = if (suffix.isEmpty()) {
        scaled.toInt().toString()
    } else {
        val oneDecimal = String.format(Locale.US, "%.1f", scaled)
        (if (oneDecimal.endsWith(".0")) oneDecimal.dropLast(2) else oneDecimal) + suffix
    }
    return symbol + text
}

/**
 * Which columns keep their x-axis label.
 *
 * [centres] and [widths] are the measured position and measured width of each
 * label, in dp and index-matched. A label is kept when it can sit at its own
 * centre with [gap] of air to the right of the label kept before it, so the
 * ticks come out near-evenly spaced without ever being told how many of them
 * to draw. The first and last column always keep theirs: an axis that drops
 * its endpoints stops reading as a scale.
 *
 * A label is kept whole or dropped whole. This is the part that matters — the
 * version it replaces divided the axis into equal columns and let each label
 * have no more than one of them, so in a 20-to-31 day month two digits at
 * labelSmall did not fit and came out as their first character. The axis read
 * 1, 5, 9, 12, 16, 2.
 */
internal fun xLabelIndices(centres: List<Float>, widths: List<Float>, gap: Float): Set<Int> {
    if (centres.isEmpty()) return emptySet()
    val kept = sortedSetOf<Int>()
    var lastRight = Float.NEGATIVE_INFINITY
    centres.indices.forEach { index ->
        if (centres[index] - widths[index] / 2f >= lastRight + gap) {
            kept += index
            lastRight = centres[index] + widths[index] / 2f
        }
    }
    val last = centres.lastIndex
    if (last !in kept) {
        val left = centres[last] - widths[last] / 2f
        // The last column outranks the interior ticks, so they give way to it.
        while (kept.size > 1 && centres[kept.last()] + widths[kept.last()] / 2f + gap > left) {
            kept.remove(kept.last())
        }
        kept += last
    }
    return kept
}

/** Air kept between two neighbouring x-axis labels, so they read as ticks. */
private val X_LABEL_GAP = 6.dp

/**
 * The x-axis, drawing exactly the ticks it is handed.
 *
 * Each tick carries the column it belongs to, so the label is placed at that
 * column's centre and measured to its own width — a two-digit day such as "21"
 * is drawn whole and can never be cut down, or nudged off the column it names.
 *
 * The geometry is the canvas's: the plot runs from the right of [AXIS_GUTTER]
 * to the right edge, and that is the span a column is measured against here
 * too. The version this replaces divided by the full width while drawing inside
 * a box already inset by the gutter, which pushed every label one gutter-width
 * to the right of the bar it named — harmless in the middle, and enough to
 * throw the last column's label clean off the card. That is why a 27-day month
 * used to end its axis on "25".
 *
 * The collision pass stays as a backstop for the periods that still ask for
 * every column, but it is no longer what decides the axis: what gets named is
 * [xAxisTicks]' decision, made before anything was measured.
 */
@Composable
private fun XAxisLabels(
    ticks: List<Pair<Int, String>>,
    columnCount: Int,
    plotWidth: Dp,
    measurer: TextMeasurer,
    style: TextStyle
) {
    if (ticks.isEmpty() || columnCount <= 0 || plotWidth <= 0.dp) return
    val density = LocalDensity.current
    // Worked out in pixels, because that is what the text measurer and the
    // offsets below speak; dp only at the edges.
    val gutterPx = with(density) { AXIS_GUTTER.toPx() }
    val plotPx = with(density) { plotWidth.toPx() }
    // The span the columns themselves occupy: everything right of the gutter.
    val innerWidth = (plotPx - gutterPx).coerceAtLeast(1f)
    val slot = innerWidth / columnCount
    val gapPx = with(density) { X_LABEL_GAP.toPx() }
    // A pixel over the measured width, so rounding can never shave a sliver off
    // the last glyph and bring the clipping back through the other door.
    val widths = ticks.map { measurer.measure(it.second, style).size.width.toFloat() + 1f }
    val centres = ticks.map { slot * (it.first + 0.5f) }
    val keep = remember(ticks, plotPx, style) { xLabelIndices(centres, widths, gapPx) }
    val rightEdge = (innerWidth - 1f).coerceAtLeast(0f)
    Box(Modifier.fillMaxWidth().padding(start = AXIS_GUTTER)) {
        ticks.forEachIndexed { index, tick ->
            if (index !in keep) return@forEachIndexed
            val width = widths[index]
            // Pulled back inside the plot if it would hang off either end.
            val x = (centres[index] - width / 2f).coerceIn(0f, (rightEdge - width).coerceAtLeast(0f))
            Text(
                tick.second,
                modifier = Modifier
                    .offset(x = with(density) { x.toDp() })
                    .width(with(density) { width.toDp() }),
                style = style,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SpendingChart(dailySpending: List<DailySpending>, period: ReportPeriod, currency: String, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    val chartPoints = remember(dailySpending, period, today) { dailySpending.toChartPoints(period, today) }
    val maxAmount = chartPoints.maxOfOrNull { it.amount } ?: 0.0
    val averageAmount = if (chartPoints.isNotEmpty()) chartPoints.sumOf { it.amount } / chartPoints.size else 0.0
    val highestPoint = chartPoints.maxByOrNull { it.amount }?.takeIf { it.amount > 0 }
    val axisMax = niceAxisMax(maxAmount)
    val axisTicks = remember(chartPoints, period) { xAxisTicks(chartPoints, period) }
    var selectedIndex by remember(chartPoints) { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp), border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(
                        when (period) { ReportPeriod.WEEKLY -> "Last 7 days"; ReportPeriod.MONTHLY -> "This month"; ReportPeriod.YEARLY -> "This year" },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    Text("Spending trend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(CurrencyService.formatAmount(maxAmount, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("highest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickPill("Average", CurrencyService.formatAmount(averageAmount, currency), Modifier.weight(1f))
                // The date, not the bare day number: the axis names only the
                // two ends of the month, so a lone "26" here would be a number
                // with nothing on screen to line it up against.
                QuickPill("Peak", highestPoint?.shortLabel ?: "-", Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            if (chartPoints.isNotEmpty() && maxAmount > 0) {
                SpendingBarChart(
                    points = chartPoints,
                    axisTicks = axisTicks,
                    axisMax = axisMax,
                    averageAmount = averageAmount,
                    currency = currency,
                    selectedIndex = selectedIndex,
                    onSelect = { index -> selectedIndex = if (selectedIndex == index) null else index },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("No spending data for this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The trend chart: one column per day (or month), a labelled value axis, a
 * dashed average line, and tap-to-inspect.
 *
 * Everything is drawn on a single canvas rather than a row of weighted boxes,
 * so column width, gaps, corner radius and the 0 baseline are all decided in
 * one place instead of being spread across layout parameters. A zero day draws
 * nothing at all — the old version forced every bar to at least 4% height,
 * which invented spending on days the user had none.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun SpendingBarChart(
    points: List<ChartPoint>,
    axisTicks: List<Pair<Int, String>>,
    axisMax: Double,
    averageAmount: Double,
    currency: String,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = Accent
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()
    val symbol = remember(currency) { CurrencyService.getSymbol(currency) }

    var progressTarget by remember(points) { mutableFloatStateOf(0f) }
    LaunchedEffect(points) { progressTarget = 1f }
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        // Held back so the bars start growing once the total above them has
        // settled — two reveals racing each other is busy, not calm.
        animationSpec = MotionTokens.progressTween(delayMillis = MotionTokens.RevealStaggerMedium),
        label = "barAnim"
    )

    BoxWithConstraints(modifier) {
        // Captured here because the nested layout scopes below cannot reach the
        // BoxWithConstraints receiver directly.
        val plotWidth = maxWidth
        val plotHeight = PLOT_HEIGHT - PLOT_TOP_INSET
        val selected = selectedIndex?.takeIf { it in points.indices }

        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PLOT_HEIGHT)
                    .pointerInput(points) {
                        detectTapGestures { tap ->
                            val plotLeft = AXIS_GUTTER.toPx()
                            if (tap.x < plotLeft || points.isEmpty()) return@detectTapGestures
                            val slot = (size.width - plotLeft) / points.size
                            onSelect(((tap.x - plotLeft) / slot).toInt().coerceIn(0, points.lastIndex))
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val plotLeft = AXIS_GUTTER.toPx()
                    val plotRight = size.width
                    val plotBottom = size.height
                    val step = axisMax / AXIS_DIVISIONS

                    // Gridlines + the value that sits on each of them. The
                    // bottom line is the 0 baseline and is drawn heavier, so the
                    // columns have a floor to stand on.
                    repeat(AXIS_DIVISIONS + 1) { i ->
                        val y = PLOT_TOP_INSET.toPx() + plotHeight.toPx() * i / AXIS_DIVISIONS
                        val isBaseline = i == AXIS_DIVISIONS
                        drawLine(
                            color = if (isBaseline) axisColor else gridColor,
                            start = Offset(plotLeft, y),
                            end = Offset(plotRight, y),
                            strokeWidth = if (isBaseline) 1.5.dp.toPx() else 1.dp.toPx()
                        )
                        val label = measurer.measure(axisValueLabel(axisMax - step * i, symbol), axisLabelStyle)
                        drawText(
                            textLayoutResult = label,
                            topLeft = Offset(plotLeft - 6.dp.toPx() - label.size.width, y - label.size.height / 2f)
                        )
                    }

                    // Average line, only while it is somewhere on the axis to be
                    // seen — otherwise it would pin itself to the frame and read
                    // as a border.
                    if (averageAmount > 0 && averageAmount < axisMax) {
                        val y = PLOT_TOP_INSET.toPx() + plotHeight.toPx() * (1f - (averageAmount / axisMax).toFloat())
                        drawLine(
                            color = accent.copy(alpha = 0.55f),
                            start = Offset(plotLeft, y),
                            end = Offset(plotRight, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }

                    val slot = (plotRight - plotLeft) / points.size
                    val barWidth = (slot * 0.62f).coerceIn(2.5.dp.toPx(), 26.dp.toPx())
                    val corner = min(4.dp.toPx(), barWidth / 2f)
                    points.forEachIndexed { index, point ->
                        if (point.amount <= 0.0) return@forEachIndexed
                        val ratio = (point.amount / axisMax).toFloat().coerceIn(0f, 1f) * animatedProgress
                        val height = plotHeight.toPx() * ratio
                        if (height <= 0f) return@forEachIndexed
                        val left = plotLeft + slot * index + (slot - barWidth) / 2f
                        val top = plotBottom - height
                        val radius = min(corner, height)
                        val path = Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    left = left,
                                    top = top,
                                    right = left + barWidth,
                                    bottom = plotBottom,
                                    topLeftCornerRadius = CornerRadius(radius, radius),
                                    topRightCornerRadius = CornerRadius(radius, radius),
                                    bottomRightCornerRadius = CornerRadius.Zero,
                                    bottomLeftCornerRadius = CornerRadius.Zero
                                )
                            )
                        }
                        drawPath(
                            path = path,
                            color = if (index == selected) accent else primary.copy(alpha = if (selected == null) 0.85f else 0.25f)
                        )
                    }
                }

                // Callout for the selected column. Fixed width, so its position
                // can be worked out in dp alone — no measuring pass, and it can
                // never be pushed off the top of the card by a full-height bar.
                if (selected != null) {
                    val point = points[selected]
                    val barRatio = (point.amount / axisMax).toFloat().coerceIn(0f, 1f) * animatedProgress
                    val slotWidth = (plotWidth - AXIS_GUTTER) / points.size
                    val centreX = AXIS_GUTTER + slotWidth * (selected + 0.5f)
                    val barTop = PLOT_HEIGHT - plotHeight * barRatio
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(CALL_OUT_WIDTH)
                                .offset(
                                    x = (centreX - CALL_OUT_WIDTH / 2).coerceIn(0.dp, maxOf(0.dp, plotWidth - CALL_OUT_WIDTH)),
                                    y = (barTop - PLOT_TOP_INSET).coerceAtLeast(0.dp)
                                )
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        CurrencyService.formatAmount(point.amount, currency),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        point.fullLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(X_AXIS_GUTTER))
            // The x-axis shares the columns' geometry exactly — same gutter,
            // same slot centres — so a label always sits under the bar it
            // belongs to, and is measured to its own width rather than to the
            // slot it happens to share.
            XAxisLabels(
                ticks = axisTicks,
                columnCount = points.size,
                plotWidth = plotWidth,
                measurer = measurer,
                style = axisLabelStyle
            )
            Spacer(Modifier.height(6.dp))
            ChartLegend(averageAmount, currency, selected != null)
        }
    }
}

/** Names the dashed line, and says what a tap does until one has been made. */
@Composable
private fun ChartLegend(averageAmount: Double, currency: String, hasSelection: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(16.dp).height(2.dp)) {
            val y = size.height / 2f
            drawLine(
                color = Accent.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "daily average ${CurrencyService.formatAmount(averageAmount, currency)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (hasSelection) "tap again to clear" else "tap a bar for its value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// --- Donut Chart ---

private data class DonutSlice(val label: String, val amount: Double, val color: Color)

/** Past this many categories the ring stops being readable, so the tail folds
 *  into one grey "Other" slice rather than a fringe of hairlines. */
private const val MAX_DONUT_SLICES = 6

private val OTHER_SLICE_COLOR = AggregateCategoryColor

/**
 * The slices the ring is made of, biggest first.
 *
 * The fold only happens once there is a genuine tail: with six or seven
 * categories every one of them still gets its own named slice, because
 * "Other (1)" tells the reader less than the category's own name would.
 */
private fun buildDonutSlices(categoryBreakdown: Map<String, Double>): List<DonutSlice> {
    val sorted = categoryBreakdown.entries.sortedByDescending { it.value }
    val named = if (sorted.size <= MAX_DONUT_SLICES + 1) {
        sorted
    } else {
        sorted.take(MAX_DONUT_SLICES - 1)
    }
    val tail = sorted.drop(named.size)
    return named.map { DonutSlice(it.key, it.value, getCategoryColor(it.key)) } +
        if (tail.isEmpty()) emptyList()
        else listOf(DonutSlice("Other (${tail.size})", tail.sumOf { it.value }, OTHER_SLICE_COLOR))
}

/** "₹12,345" — the cent digits in the middle of a donut add width, not meaning. */
private fun formatCompact(amount: Double, currency: String): String {
    val symbol = CurrencyService.getSymbol(currency)
    val magnitude = abs(amount)
    return when {
        magnitude >= 10_000_000 -> symbol + String.format(Locale.US, "%.1fM", amount / 1_000_000).replace(".0M", "M")
        magnitude >= 100_000 -> symbol + String.format(Locale.US, "%.0fk", amount / 1_000)
        else -> symbol + String.format(Locale.US, "%,.0f", amount)
    }
}

@Composable
private fun DonutChartSection(
    categoryBreakdown: Map<String, Double>,
    totalSpend: Double,
    currency: String
) {
    val slices = remember(categoryBreakdown) { buildDonutSlices(categoryBreakdown) }
    var selectedLabel by remember(slices) { mutableStateOf<String?>(null) }
    val selected = slices.firstOrNull { it.label == selectedLabel }
    val sliceTotal = slices.sumOf { it.amount }.takeIf { it > 0 } ?: 1.0

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp), border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Category Split", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                    var sweepTarget by remember(slices) { mutableFloatStateOf(0f) }
                    LaunchedEffect(slices) { sweepTarget = 360f }
                    val animatedSweep by animateFloatAsState(
                        targetValue = sweepTarget,
                        // Last in the sequence: the donut is the summary of the
                        // two things above it, so it draws the eye last.
                        animationSpec = MotionTokens.progressTween(delayMillis = MotionTokens.RevealStaggerLarge),
                        label = "donut"
                    )
                    Canvas(Modifier.size(148.dp)) {
                        val strokeWidth = 20.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val radius = diameter / 2f
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val arcSize = Size(diameter, diameter)
                        // A gap measured in pixels (so it looks the same at any
                        // radius) converted to the angle that many pixels span.
                        val gapDegrees = Math.toDegrees((2.5.dp.toPx() / radius).toDouble()).toFloat()
                        var startAngle = -90f
                        slices.forEach { slice ->
                            val sweep = (slice.amount / sliceTotal).toFloat() * animatedSweep
                            if (sweep > 0f) {
                                // Slices thinner than the gap keep a visible
                                // sliver instead of the negative sweep the old
                                // fixed 2° inset produced, which drew nothing.
                                val gap = if (slices.size == 1) 0f else min(gapDegrees, sweep * 0.4f)
                                val drawSweep = (sweep - gap).coerceAtLeast(0.5f)
                                drawArc(
                                    color = if (selected != null && selected.label != slice.label) slice.color.copy(alpha = 0.3f) else slice.color,
                                    startAngle = startAngle + gap / 2f,
                                    sweepAngle = drawSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    // Butt caps: round caps on neighbouring
                                    // slices bleed into each other and the
                                    // boundaries between them disappear.
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp)) {
                        if (selected != null) {
                            Text(
                                "${((selected.amount / sliceTotal) * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                            )
                            Text(
                                formatCompact(selected.amount, currency),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                            )
                            Text(
                                selected.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                formatCompact(totalSpend, currency),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                            )
                            Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                // The legend covers every slice, including the folded tail. The
                // old one stopped at five while the ring drew all of them, so
                // the swatches stopped explaining the chart halfway round.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slices.forEach { slice ->
                        // Rounded, not truncated: truncating every share is how a
                        // legend ends up claiming 97% and looking wrong.
                        val pct = ((slice.amount / sliceTotal) * 100).roundToInt()
                        val isSelected = selected?.label == slice.label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) slice.color.copy(alpha = 0.12f) else Color.Transparent
                                )
                                .clickable { selectedLabel = if (isSelected) null else slice.label }
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                slice.label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                "$pct%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Category List ---

@Composable
private fun CategoryItem(category: String, amount: Double, total: Double, currency: String, transactions: List<Transaction>, color: Color) {
    val percentage = if (total > 0) ((amount / total) * 100).roundToInt() else 0
    val progress = (amount / total).toFloat().coerceIn(0f, 1f)
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionTokens.fastTween(), label = "chevron"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp), border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(getCategoryIcon(category), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("$percentage% of total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(CurrencyService.formatAmount(amount, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp).rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                SmoothLinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = color, trackColor = color.copy(alpha = 0.12f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = MotionTokens.expandWithFade(),
                exit = MotionTokens.collapseWithFade()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (transactions.isEmpty()) {
                        Text(
                            "No transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        transactions.forEach { tx ->
                            TransactionSubRow(tx, currency)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionSubRow(tx: Transaction, currency: String) {
    val dateStr = tx.date.format(DateTimeFormatter.ofPattern("MMM d"))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = tx.note.ifEmpty { tx.category },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = CurrencyService.formatAmount(tx.amount, currency),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

// --- Insights ---

@Composable
private fun AnalyticsInsightCard(insight: SpendingInsight) {
    val bgColor = when (insight.severity) {
        InsightSeverity.ALERT -> MaterialTheme.colorScheme.errorContainer
        InsightSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        InsightSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
    }
    val accentColor = when (insight.severity) {
        InsightSeverity.ALERT -> MaterialTheme.colorScheme.error
        InsightSeverity.WARNING -> Warning
        InsightSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    val icon = when (insight.severity) {
        InsightSeverity.ALERT -> Icons.Default.Warning
        InsightSeverity.WARNING -> Icons.AutoMirrored.Filled.TrendingUp
        InsightSeverity.INFO -> Icons.Default.Lightbulb
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp), border = CardDefaults.outlinedCardBorder()
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor)
                Spacer(Modifier.height(2.dp))
                Text(insight.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
