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
import com.expensetracker.ui.components.ConfirmDeleteDialog
import com.expensetracker.ui.components.CurrencyPickerDialog
import com.expensetracker.ui.components.FormDialog
import com.expensetracker.ui.components.FormField
import com.expensetracker.ui.components.OptionPickerDialog
import com.expensetracker.ui.components.PickerField
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
    var picker by remember { mutableStateOf<RecurringPicker?>(null) }

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

    FormDialog(
        title = "Add Recurring",
        confirmLabel = "Add",
        // The error is raised by pressing Add, not by the field losing focus,
        // so a blank form still offers the button and explains itself after.
        confirmEnabled = true,
        onConfirm = {
            val parsedAmount = amount.toDoubleOrNull()
            if (parsedAmount == null || parsedAmount <= 0.0) {
                amountError = true
                return@FormDialog
            }
            onAdd(parsedAmount, currency, category, note, frequency)
        },
        onDismiss = onDismiss
    ) {
        FormField(
            label = "Amount",
            value = amount,
            onValueChange = {
                amount = it.filter { char -> char.isDigit() || char == '.' }
                amountError = false
            },
            placeholder = "0",
            prefix = CurrencyService.getSymbol(currency),
            keyboardType = KeyboardType.Decimal,
            isError = amountError,
            supportingText = if (amountError) "Enter a valid amount" else null
        )

        PickerField(
            label = "Currency",
            value = currency,
            placeholder = "Choose a currency",
            onClick = { picker = RecurringPicker.Currency }
        )

        PickerField(
            label = "Category",
            value = category,
            placeholder = "Choose a category",
            onClick = { picker = RecurringPicker.Category }
        )

        FormField(
            label = "Note",
            value = note,
            onValueChange = { note = it.take(200) },
            placeholder = "Optional"
        )

        PickerField(
            label = "Frequency",
            value = frequency.displayName(),
            placeholder = "Choose a frequency",
            onClick = { picker = RecurringPicker.Frequency }
        )
    }

    when (picker) {
        // The same picker Add Expense and Settings use, so all three list every
        // currency in one place. This dialog used to show the first ten only.
        RecurringPicker.Currency -> CurrencyPickerDialog(
            selectedCode = currency,
            onSelect = {
                currency = it
                picker = null
            },
            onDismiss = { picker = null }
        )

        RecurringPicker.Category -> OptionPickerDialog(
            title = "Category",
            options = categories,
            selected = category,
            label = { it },
            onSelect = {
                category = it
                picker = null
            },
            onDismiss = { picker = null },
            leadingIcon = { getCategoryIcon(it) },
            leadingTint = { getCategoryColor(it) }
        )

        RecurringPicker.Frequency -> OptionPickerDialog(
            title = "Frequency",
            options = RecurringFrequency.entries,
            selected = frequency,
            label = { it.displayName() },
            onSelect = {
                frequency = it
                picker = null
            },
            onDismiss = { picker = null }
        )

        null -> Unit
    }
}

/** Which of the Add Recurring fields is currently asking to be changed. */
private enum class RecurringPicker { Currency, Category, Frequency }

private fun RecurringFrequency.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

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
        modifier = Modifier.animateContentSize(animationSpec = MotionTokens.gentle()),
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
        ConfirmDeleteDialog(
            title = "Delete this recurring expense?",
            detail = "${CurrencyService.formatAmount(rule.amount, rule.currency)} · ${rule.category}",
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
