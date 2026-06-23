package com.expensetracker.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel) {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
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
                            transactions = uiState.categoryTransactions[category] ?: emptyList()
                        )
                    }
                }
                if (uiState.insights.isNotEmpty()) {
                    item { SectionHeader(title = "Insights") }
                    items(uiState.insights.take(3)) { insight -> AnalyticsInsightCard(insight) }
                }
            }
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
        animationSpec = MotionTokens.progressTween(durationMillis = 650), label = "total"
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

private data class ChartPoint(val label: String, val amount: Double)

private fun List<DailySpending>.toChartPoints(period: ReportPeriod): List<ChartPoint> {
    val dayFmt = DateTimeFormatter.ofPattern("d")
    val weekFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    return when (period) {
        ReportPeriod.YEARLY -> groupBy { YearMonth.from(LocalDate.parse(it.date)) }.toSortedMap()
            .map { (m, pts) -> ChartPoint(m.format(monthFmt), pts.sumOf { it.amount }) }
        ReportPeriod.WEEKLY -> map { ChartPoint(LocalDate.parse(it.date).format(weekFmt), it.amount) }
        ReportPeriod.MONTHLY -> map { ChartPoint(LocalDate.parse(it.date).format(dayFmt), it.amount) }
    }
}

@Composable
private fun SpendingChart(dailySpending: List<DailySpending>, period: ReportPeriod, currency: String, modifier: Modifier = Modifier) {
    val chartPoints = remember(dailySpending, period) { dailySpending.toChartPoints(period) }
    val maxAmount = chartPoints.maxOfOrNull { it.amount } ?: 0.0
    val averageAmount = if (chartPoints.isNotEmpty()) chartPoints.sumOf { it.amount } / chartPoints.size else 0.0
    val highestPoint = chartPoints.maxByOrNull { it.amount }

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
                QuickPill("Peak", highestPoint?.label ?: "-", Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            if (chartPoints.isNotEmpty() && maxAmount > 0) {
                AnimatedBarChart(chartPoints, maxAmount, averageAmount, Modifier.fillMaxWidth().height(220.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("No spending data for this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AnimatedBarChart(points: List<ChartPoint>, maxAmount: Double, averageAmount: Double, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val avgLineColor = Accent
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    var progressTarget by remember(points) { mutableFloatStateOf(0f) }
    LaunchedEffect(points) {
        progressTarget = 1f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = MotionTokens.progressTween(durationMillis = 760),
        label = "barAnim"
    )

    Column(modifier = modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val gridCount = 4
                repeat(gridCount + 1) { i ->
                    val y = size.height * i / gridCount
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                // Average line
                if (maxAmount > 0) {
                    val avgY = size.height * (1f - (averageAmount / maxAmount).toFloat()).coerceIn(0f, 1f)
                    drawLine(avgLineColor.copy(alpha = 0.6f), Offset(0f, avgY), Offset(size.width, avgY), strokeWidth = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                }
            }
            Row(Modifier.matchParentSize().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                points.forEachIndexed { index, point ->
                    val ratio = ((point.amount / maxAmount).toFloat() * animatedProgress).coerceIn(0.04f, 1f)
                    val barColor = if (point.amount == maxAmount) secondary else primary
                    Box(
                        Modifier.weight(1f).fillMaxHeight(ratio)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(barColor.copy(alpha = if (index % 2 == 0) 0.9f else 0.72f))
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEachIndexed { index, point ->
                val showLabel = points.size <= 12 || index == 0 || index == points.lastIndex || index % 5 == 0
                Text(if (showLabel) point.label else "", style = MaterialTheme.typography.labelSmall, color = labelColor, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

// --- Donut Chart ---

@Composable
private fun DonutChartSection(categoryBreakdown: Map<String, Double>, totalSpend: Double, currency: String) {
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
                Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    var sweepTarget by remember(categoryBreakdown) { mutableFloatStateOf(0f) }
                    LaunchedEffect(categoryBreakdown) {
                        sweepTarget = 360f
                    }
                    val animatedSweep by animateFloatAsState(
                        targetValue = sweepTarget,
                        animationSpec = MotionTokens.progressTween(durationMillis = 860),
                        label = "donut"
                    )
                    val entries = categoryBreakdown.entries.toList()
                    Canvas(Modifier.size(140.dp)) {
                        val strokeWidth = 20.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2
                        val topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2)
                        val arcSize = Size(radius * 2, radius * 2)
                        var startAngle = -90f
                        entries.forEachIndexed { index, (category, amount) ->
                            val sweep = if (totalSpend > 0) ((amount / totalSpend) * animatedSweep).toFloat() else 0f
                            val color = getCategoryColor(category)
                            drawArc(color = color, startAngle = startAngle, sweepAngle = sweep - 2f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(CurrencyService.formatAmount(totalSpend, currency), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryBreakdown.entries.take(5).forEach { (category, amount) ->
                        val pct = if (totalSpend > 0) (amount / totalSpend * 100).toInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(getCategoryColor(category)))
                            Spacer(Modifier.width(8.dp))
                            Text(category, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Text("$pct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// --- Category List ---

@Composable
private fun CategoryItem(category: String, amount: Double, total: Double, currency: String, transactions: List<Transaction>) {
    val percentage = if (total > 0) (amount / total * 100).toInt() else 0
    val color = getCategoryColor(category)
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
                enter = expandVertically(animationSpec = MotionTokens.enterTween()) +
                    androidx.compose.animation.fadeIn(
                        animationSpec = MotionTokens.enterTween(durationMillis = 180)
                    ),
                exit = shrinkVertically(animationSpec = MotionTokens.exitTween(durationMillis = 180)) +
                    androidx.compose.animation.fadeOut(animationSpec = MotionTokens.exitTween())
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
