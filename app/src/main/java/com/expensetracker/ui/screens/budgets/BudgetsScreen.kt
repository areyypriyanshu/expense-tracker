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
import com.expensetracker.ui.components.ConfirmDeleteDialog
import com.expensetracker.ui.components.FormDialog
import com.expensetracker.ui.components.FormField
import com.expensetracker.ui.components.FormSlider
import com.expensetracker.ui.components.OptionPickerDialog
import com.expensetracker.ui.components.PickerField
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
                // Solid, and the same dark green as the Home and Transactions
                // buttons. This one was the last holdout for the translucent
                // fill, which at 0.6 over a light surface turned the gold into a
                // pale wash with a near-black glyph on it — the weakest-looking
                // button in the app, and the only one of the three not matching.
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
        ConfirmDeleteDialog(
            title = "Delete this budget?",
            detail = "${status.budget.category} · ${status.budget.period.name.lowercase().replaceFirstChar { it.uppercase() }}",
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

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
    var picker by remember { mutableStateOf<BudgetPicker?>(null) }

    val limitValue = limit.toDoubleOrNull()
    val limitIsValid = limitValue != null && limitValue > 0 && limitValue < 1_000_000_000

    FormDialog(
        title = "Add Budget",
        confirmLabel = "Add",
        confirmEnabled = selectedCategory.isNotEmpty() && limitIsValid,
        onConfirm = {
            if (selectedCategory.isNotEmpty() && limitIsValid) {
                onConfirm(selectedCategory, limitValue ?: 0.0, period, threshold)
            }
        },
        onDismiss = onDismiss
    ) {
        PickerField(
            label = "Category",
            value = selectedCategory,
            placeholder = "Choose a category",
            onClick = { picker = BudgetPicker.Category }
        )

        FormField(
            label = "Budget Limit",
            value = limit,
            onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
            placeholder = "0",
            prefix = CurrencyService.getSymbol(currency),
            keyboardType = KeyboardType.Decimal
        )

        PickerField(
            label = "Period",
            value = period.displayName(),
            placeholder = "Choose a period",
            onClick = { picker = BudgetPicker.Period }
        )

        FormSlider(
            label = "Alert at ${(threshold * 100).toInt()}%",
            value = threshold,
            onValueChange = { threshold = it },
            valueRange = 0.5f..0.95f,
            steps = 8
        )
    }

    when (picker) {
        BudgetPicker.Category -> OptionPickerDialog(
            title = "Category",
            options = categories,
            selected = selectedCategory,
            label = { it },
            onSelect = {
                selectedCategory = it
                picker = null
            },
            onDismiss = { picker = null },
            leadingIcon = { getCategoryIcon(it) },
            leadingTint = { getCategoryColor(it) }
        )

        BudgetPicker.Period -> OptionPickerDialog(
            title = "Period",
            options = BudgetPeriod.entries,
            selected = period,
            label = { it.displayName() },
            onSelect = {
                period = it
                picker = null
            },
            onDismiss = { picker = null }
        )

        null -> Unit
    }
}

/** Which of the Add Budget fields is currently asking to be changed. */
private enum class BudgetPicker { Category, Period }

private fun BudgetPeriod.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }
