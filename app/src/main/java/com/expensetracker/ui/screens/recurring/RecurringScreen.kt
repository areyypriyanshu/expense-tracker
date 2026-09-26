package com.expensetracker.ui.screens.recurring

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.ExpenseTrackerApp
import com.expensetracker.data.model.RecurringFrequency
import com.expensetracker.data.model.RecurringRule
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.StyledAlertDialog
import com.expensetracker.ui.theme.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecurringViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Recurring Expenses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.isLoading) {
                        IconButton(onClick = { viewModel.showAddDialog() }) {
                            Icon(Icons.Default.Add, contentDescription = "Add recurring")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.recurringRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Repeat,
                    title = "No recurring expenses",
                    subtitle = "Set up automatic expense tracking",
                    actionLabel = "Add Recurring",
                    onAction = { viewModel.showAddDialog() }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.recurringRules) { rule ->
                    RecurringRuleCard(
                        rule = rule,
                        currency = uiState.baseCurrency,
                        onToggle = { viewModel.toggleRule(rule) },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }

            }
        }
    }

    if (uiState.showAddDialog) {
        AddRecurringDialog(
            baseCurrency = uiState.baseCurrency,
            onDismiss = viewModel::hideAddDialog,
            onAdd = viewModel::addRule
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringDialog(
    baseCurrency: String,
    onDismiss: () -> Unit,
    onAdd: (Double, String, String, String, RecurringFrequency) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var currency by remember(baseCurrency) { mutableStateOf(baseCurrency) }
    var category by remember { mutableStateOf("Bills & Utilities") }
    var note by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    var amountError by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }

    val categories = listOf(
        "Bills & Utilities",
        "Subscriptions",
        "Rent",
        "Transportation",
        "Healthcare",
        "Education",
        "Personal Care",
        "Other"
    )

    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recurring", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { char -> char.isDigit() || char == '.' }
                        amountError = false
                    },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = amountError,
                    supportingText = if (amountError) {
                        { Text("Enter a valid amount") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = currencyExpanded,
                    onExpandedChange = { currencyExpanded = !currencyExpanded }
                ) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = currencyExpanded,
                        onDismissRequest = { currencyExpanded = false }
                    ) {
                        CurrencyService.SUPPORTED_CURRENCIES.take(10).forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.code} - ${item.name}") },
                                onClick = {
                                    currency = item.code
                                    currencyExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    category = item
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = frequencyExpanded,
                    onExpandedChange = { frequencyExpanded = !frequencyExpanded }
                ) {
                    OutlinedTextField(
                        value = frequency.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = frequencyExpanded,
                        onDismissRequest = { frequencyExpanded = false }
                    ) {
                        RecurringFrequency.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    frequency = item
                                    frequencyExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull()
                    if (parsedAmount == null || parsedAmount <= 0.0) {
                        amountError = true
                        return@TextButton
                    }
                    onAdd(parsedAmount, currency, category, note, frequency)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RecurringRuleCard(
    rule: RecurringRule,
    currency: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val color = getCategoryColor(rule.category)
    val containerColor by animateColorAsState(
        targetValue = if (rule.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = MotionTokens.fastTween(),
        label = "recurringContainer"
    )
    val iconTint by animateColorAsState(
        targetValue = if (rule.isActive) color else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionTokens.fastTween(),
        label = "recurringIcon"
    )
    val titleColor by animateColorAsState(
        targetValue = if (rule.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionTokens.fastTween(),
        label = "recurringTitle"
    )
    val amountColor by animateColorAsState(
        targetValue = if (rule.isActive) Negative else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionTokens.fastTween(),
        label = "recurringAmount"
    )

    Card(
        modifier = Modifier.animateContentSize(animationSpec = MotionTokens.spring()),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getCategoryIcon(rule.category),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                Text(
                    text = rule.note.ifEmpty { rule.frequency.name.lowercase().replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Next: ${rule.nextDate.format(DateTimeFormatter.ofPattern("MMM dd"))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyService.formatAmount(rule.amount, rule.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                Row {
                    IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (rule.isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        StyledAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Recurring", fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to delete this recurring expense?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
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
