package com.expensetracker.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.domain.engine.BudgetStatus
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.services.insights.SpendingInsight
import com.expensetracker.services.insights.InsightSeverity
import com.expensetracker.ui.components.*
import com.expensetracker.ui.theme.*
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DashboardHeader(
                greeting = greeting,
                currency = uiState.baseCurrency,
                todaySpend = uiState.todaySpend,
                weeklySpend = uiState.weeklySpend,
                monthlySpend = uiState.monthlySpend,
                onAddTransaction = onAddTransaction,
                onNavigateToChatbot = onNavigateToChatbot
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                containerColor = Primary,
                contentColor = Color(0xFFF6F3EA),
                shape = RoundedCornerShape(8.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense", modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Expense", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = MotionTokens.enterTween(durationMillis = 220),
            label = "dashboardContent"
        ) { isLoading ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        BalanceOverviewCard(
                            monthlySpend = uiState.monthlySpend,
                            weeklySpend = uiState.weeklySpend,
                            currency = uiState.baseCurrency,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    if (uiState.categoryBreakdown.isNotEmpty()) {
                        item {
                            SpendingByCategorySection(
                                breakdown = uiState.categoryBreakdown,
                                currency = uiState.baseCurrency,
                                onSeeAll = onNavigateToAnalytics,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (uiState.budgetStatuses.isNotEmpty()) {
                        item {
                            BudgetOverviewSection(
                                budgetStatuses = uiState.budgetStatuses,
                                currency = uiState.baseCurrency,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (uiState.insights.isNotEmpty()) {
                        item {
                            InsightsSection(
                                insights = uiState.insights,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Recent Transactions",
                            action = "See All",
                            onAction = onViewAllTransactions
                        )
                    }

                    if (uiState.recentTransactions.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.Receipt,
                                title = "No transactions yet",
                                subtitle = "Tap the + button below to add your first expense",
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    } else {
                        items(uiState.recentTransactions) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                onClick = { },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    greeting: String,
    currency: String,
    todaySpend: Double,
    weeklySpend: Double,
    monthlySpend: Double,
    onAddTransaction: () -> Unit,
    onNavigateToChatbot: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Expense Tracker",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onNavigateToChatbot,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Finance Assistant",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickStatCard(
                modifier = Modifier.weight(1f),
                label = "Today",
                value = CurrencyService.formatAmount(todaySpend, currency),
                icon = Icons.Default.Today,
                iconColor = Negative
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                label = "This Week",
                value = CurrencyService.formatAmount(weeklySpend, currency),
                icon = Icons.Default.DateRange,
                iconColor = Secondary
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                label = "This Month",
                value = CurrencyService.formatAmount(monthlySpend, currency),
                icon = Icons.Default.CalendarMonth,
                iconColor = Accent
            )
        }
    }
}

@Composable
private fun BalanceOverviewCard(
    monthlySpend: Double,
    weeklySpend: Double,
    currency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "This Month's Spending",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyService.formatAmount(monthlySpend, currency),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "This Week",
                    value = CurrencyService.formatAmount(weeklySpend, currency)
                )
                StatItem(
                    label = "Daily Average",
                    value = CurrencyService.formatAmount(monthlySpend / 30, currency)
                )
                StatItem(
                    label = "Status",
                    value = "Active"
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SpendingByCategorySection(
    breakdown: Map<String, Double>,
    currency: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "Spending by Category",
            action = "See All",
            onAction = onSeeAll
        )
        
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val sortedCategories = breakdown.entries.sortedByDescending { it.value }.take(4)
            sortedCategories.forEach { (category, amount) ->
                SpendingCategoryRow(
                    category = category,
                    amount = amount,
                    total = breakdown.values.sum(),
                    currency = currency
                )
            }
        }
    }
}

@Composable
private fun SpendingCategoryRow(
    category: String,
    amount: Double,
    total: Double,
    currency: String
) {
    val color = getCategoryColor(category)
    val progress = (amount / total).toFloat().coerceIn(0f, 1f)
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(category),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                SmoothLinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.15f),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = CurrencyService.formatAmount(amount, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BudgetOverviewSection(
    budgetStatuses: List<BudgetStatus>,
    currency: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(title = "Budget Overview")
        
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            budgetStatuses.take(3).forEach { status ->
                BudgetStatusRow(status = status, currency = currency)
            }
        }
    }
}

@Composable
private fun BudgetStatusRow(
    status: BudgetStatus,
    currency: String
) {
    val progressColor = when {
        status.isOverBudget -> Negative
        status.percentageUsed >= 0.8f -> Warning
        else -> Positive
    }
    
    val iconColor = when {
        status.isOverBudget -> Negative
        status.percentageUsed >= 0.8f -> Warning
        else -> Positive
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                status.isOverBudget -> Icons.Default.Warning
                                status.percentageUsed >= 0.8f -> Icons.AutoMirrored.Filled.TrendingUp
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = iconColor
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = status.budget.category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${(status.percentageUsed * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            SmoothLinearProgressIndicator(
                progress = status.percentageUsed.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${CurrencyService.formatAmount(status.spent, currency)} spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "of ${CurrencyService.formatAmount(status.budget.limit, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InsightsSection(
    insights: List<SpendingInsight>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp)
    ) {
        SectionHeader(title = "AI Insights")
        
        Spacer(modifier = Modifier.height(12.dp))
        
        insights.take(3).forEach { insight ->
            InsightCard(insight = insight)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun InsightCard(insight: SpendingInsight) {
    val backgroundColor = when (insight.severity) {
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
