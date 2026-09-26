package com.expensetracker.ui.screens.budgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.data.model.Budget
import com.expensetracker.data.model.BudgetPeriod
import com.expensetracker.domain.engine.BudgetStatus
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.ui.components.AppHeader
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.ScrollAwareBlurScrim
import com.expensetracker.ui.components.SmoothLinearProgressIndicator
import com.expensetracker.ui.components.StyledAlertDialog
import com.expensetracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    bottomContentPadding: Dp = 0.dp,
    viewModel: BudgetsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                AppHeader(
                    title = "Budgets",
                    currency = uiState.baseCurrency,
                    todaySpend = uiState.todaySpend,
                    weeklySpend = uiState.weeklySpend,
                    monthlySpend = uiState.monthlySpend
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                modifier = Modifier.padding(bottom = bottomContentPadding),
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.budgetStatuses.isEmpty()) {
            EmptyState(
                icon = Icons.Default.AccountBalance,
                title = "No budgets set",
                subtitle = "Set spending limits to track your expenses",
                actionLabel = "Add Budget",
                onAction = { viewModel.showAddDialog() },
                modifier = Modifier.padding(padding)
            )
        } else {
            val listState = rememberLazyListState()
            ScrollAwareBlurScrim(listState = listState) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomContentPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.budgetStatuses) { status ->
                        BudgetCard(
                            status = status,
                            currency = uiState.baseCurrency,
                            onDelete = { viewModel.deleteBudget(status.budget) }
                        )
                    }

                }
            }
        }
    }

    if (uiState.showAddDialog) {
        AddBudgetDialog(
            categories = uiState.categories,
            currency = uiState.baseCurrency,
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { category, limit, period, threshold ->
                viewModel.addBudget(category, limit, period, threshold)
            }
        )
    }
}

@Composable
private fun BudgetCard(
    status: BudgetStatus,
    currency: String,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val progressColor = when {
        status.isOverBudget -> Negative
        status.percentageUsed >= 0.8f -> Warning
        else -> Positive
    }
    val animatedProgressColor by animateColorAsState(
        targetValue = progressColor,
        animationSpec = MotionTokens.fastTween(),
        label = "budgetProgressColor"
    )

    Card(
        modifier = Modifier.animateContentSize(animationSpec = MotionTokens.gentle()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = status.budget.category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = status.budget.period.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(status.percentageUsed * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = animatedProgressColor
                    )
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SmoothLinearProgressIndicator(
                progress = status.percentageUsed.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = animatedProgressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyService.formatAmount(status.spent, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (status.isOverBudget) Negative else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyService.formatAmount(status.remaining, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyService.formatAmount(status.budget.limit, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            AnimatedVisibility(
                visible = status.shouldAlert && !status.isOverBudget,
                enter = MotionTokens.expandWithFade(),
                exit = MotionTokens.collapseWithFade()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Warning.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Warning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Approaching budget limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = Warning
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = status.isOverBudget,
                enter = MotionTokens.expandWithFade(),
                exit = MotionTokens.collapseWithFade()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Negative.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Negative,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Budget exceeded by ${CurrencyService.formatAmount(status.spent - status.budget.limit, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Negative
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        StyledAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Budget", fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to delete this budget?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    categories: List<String>,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, BudgetPeriod, Float) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var limit by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(BudgetPeriod.MONTHLY) }
    var threshold by remember { mutableFloatStateOf(0.8f) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }

    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Budget", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(    ),
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Budget Limit") },
                    prefix = { Text(CurrencyService.getSymbol(currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = !periodExpanded }
                ) {
                    OutlinedTextField(
                        value = period.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        label = { Text("Period") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = periodExpanded,
                        onDismissRequest = { periodExpanded = false }
                    ) {
                        BudgetPeriod.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    period = p
                                    periodExpanded = false
                                }
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "Alert at ${(threshold * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = 0.5f..0.95f,
                        steps = 8
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val limitValue = limit.toDoubleOrNull() ?: 0.0
                    if (selectedCategory.isNotEmpty() && limitValue > 0 && limitValue < 1_000_000_000) {
                        onConfirm(selectedCategory, limitValue, period, threshold)
                    }
                },
                enabled = selectedCategory.isNotEmpty() && limit.toDoubleOrNull()?.let { it > 0 && it < 1_000_000_000 } == true
            ) {
                Text("Add", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
