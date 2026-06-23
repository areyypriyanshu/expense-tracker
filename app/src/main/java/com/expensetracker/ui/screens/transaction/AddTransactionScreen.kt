package com.expensetracker.ui.screens.transaction

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.data.model.RecurringFrequency
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.ui.components.StyledAlertDialog
import com.expensetracker.ui.theme.Accent
import com.expensetracker.ui.theme.MotionTokens
import com.expensetracker.ui.theme.Positive
import com.expensetracker.ui.theme.Negative
import com.expensetracker.ui.theme.getCategoryColor
import com.expensetracker.ui.theme.getCategoryIcon
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    transactionId: Long?,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: AddTransactionViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    val isEditing = transactionId != null && transactionId > 0

    LaunchedEffect(Unit) {
        if (!isEditing) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(transactionId) {
        if (transactionId != null && transactionId > 0) {
            viewModel.loadTransaction(transactionId)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = if (isEditing) "Edit Expense" else "Quick Add",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .animateContentSize(animationSpec = MotionTokens.spring())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (!isEditing) {
                GreetingCard()
            }

            AmountInput(
                amount = uiState.amount,
                currency = uiState.currency,
                isEditing = isEditing,
                onAmountChange = viewModel::onAmountChange,
                focusRequester = focusRequester,
                keyboardController = keyboardController
            )

            TransactionTypeToggle(
                isIncome = uiState.isIncome,
                onToggle = viewModel::onIsIncomeChange
            )

            if (!isEditing) {
                QuickAmountRow(
                    amounts = listOf(50, 100, 200, 500, 1000),
                    currency = uiState.currency,
                    onAmountSelected = { viewModel.onAmountChange(it.toString()) }
                )
            }

            CategorySection(
                categories = uiState.categories,
                selectedCategory = uiState.category,
                isEditing = isEditing,
                onCategorySelected = viewModel::onCategoryChange,
                onCategoryAndSave = { category ->
                    viewModel.onCategoryChange(category)
                    if (!isEditing && uiState.amount.isNotEmpty()) {
                        keyboardController?.hide()
                        viewModel.saveTransaction(onSaveSuccess)
                    }
                }
            )

            MoreOptionsToggle(
                showMoreOptions = showMoreOptions,
                onToggle = { showMoreOptions = !showMoreOptions }
            )

            AnimatedVisibility(
                visible = showMoreOptions,
                enter = expandVertically(animationSpec = MotionTokens.enterTween()) +
                    fadeIn(animationSpec = MotionTokens.enterTween(durationMillis = 180)),
                exit = shrinkVertically(animationSpec = MotionTokens.exitTween(durationMillis = 180)) +
                    fadeOut(animationSpec = MotionTokens.exitTween())
            ) {
                MoreOptionsSection(
                    note = uiState.note,
                    onNoteChange = viewModel::onNoteChange,
                    onAutoCategorize = viewModel::autoCategorize,
                    date = uiState.date,
                    onDateClick = { showDatePicker = true },
                    currency = uiState.currency,
                    onCurrencyClick = { showCurrencyPicker = true },
                    isRecurring = uiState.isRecurring,
                    onRecurringChange = viewModel::onRecurringChange,
                    recurringFrequency = uiState.recurringFrequency,
                    onFrequencyChange = viewModel::onRecurringFrequencyChange
                )
            }

            if (uiState.error != null) {
                ErrorMessage(message = uiState.error!!)
            }

            Spacer(modifier = Modifier.height(8.dp))

            SaveButton(
                isEditing = isEditing,
                isLoading = uiState.isLoading,
                hasAmount = uiState.amount.isNotEmpty() && uiState.amount.toDoubleOrNull() != null && uiState.amount.toDouble() > 0,
                onClick = {
                    keyboardController?.hide()
                    viewModel.saveTransaction(onSaveSuccess)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialogContent(
            currentDate = uiState.date,
            onDateSelected = { date ->
                viewModel.onDateChange(LocalDateTime.of(date, LocalTime.now()))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currentCurrency = uiState.currency,
            onCurrencySelected = {
                viewModel.onCurrencyChange(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

@Composable
private fun TransactionTypeToggle(
    isIncome: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val expenseContainerColor by animateColorAsState(
        targetValue = if (!isIncome) Negative.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = MotionTokens.fastTween(),
        label = "expenseContainer"
    )
    val incomeContainerColor by animateColorAsState(
        targetValue = if (isIncome) Positive.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = MotionTokens.fastTween(),
        label = "incomeContainer"
    )
    val expenseContentColor by animateColorAsState(
        targetValue = if (!isIncome) Negative else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionTokens.fastTween(),
        label = "expenseContent"
    )
    val incomeContentColor by animateColorAsState(
        targetValue = if (isIncome) Positive else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionTokens.fastTween(),
        label = "incomeContent"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle(false) },
                colors = CardDefaults.cardColors(
                    containerColor = expenseContainerColor
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = expenseContentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!isIncome) FontWeight.SemiBold else FontWeight.Normal,
                        color = expenseContentColor
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle(true) },
                colors = CardDefaults.cardColors(
                    containerColor = incomeContainerColor
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = incomeContentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isIncome) FontWeight.SemiBold else FontWeight.Normal,
                        color = incomeContentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingCard() {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "How much did you spend?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AmountInput(
    amount: String,
    currency: String,
    isEditing: Boolean,
    onAmountChange: (String) -> Unit,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?
) {
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        label = { Text("Amount") },
        prefix = {
            Text(
                text = CurrencyService.getSymbol(currency),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Accent
            )
        },
        placeholder = {
            Text(
                text = "0",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { keyboardController?.hide() }
        ),
        singleLine = true,
        textStyle = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold
        ),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = Accent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun QuickAmountRow(
    amounts: List<Int>,
    currency: String,
    onAmountSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        amounts.forEach { amount ->
            OutlinedCard(
                onClick = { onAmountSelected(amount) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "${CurrencyService.getSymbol(currency)}$amount",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CategorySection(
    categories: List<com.expensetracker.data.model.Category>,
    selectedCategory: String,
    isEditing: Boolean,
    onCategorySelected: (String) -> Unit,
    onCategoryAndSave: (String) -> Unit
) {
    Column {
        Text(
            text = "Category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val rows = categories.chunked(4)
            rows.forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCategories.forEach { category ->
                        CategoryCard(
                            modifier = Modifier.weight(1f),
                            name = category.name,
                            isSelected = selectedCategory == category.name,
                            onClick = {
                                if (!isEditing && categories.indexOf(category) == 0) {
                                    onCategoryAndSave(category.name)
                                } else {
                                    onCategorySelected(category.name)
                                }
                            }
                        )
                    }
                    repeat(4 - rowCategories.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    modifier: Modifier = Modifier,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = getCategoryColor(name)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        animationSpec = MotionTokens.fastTween(),
        label = "${name}CategoryContainer"
    )
    val iconBackgroundColor by animateColorAsState(
        targetValue = if (isSelected) color else color.copy(alpha = 0.7f),
        animationSpec = MotionTokens.fastTween(),
        label = "${name}CategoryIcon"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionTokens.fastTween(),
        label = "${name}CategoryText"
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = MotionTokens.spring(),
        label = "${name}CategoryScale"
    )

    Card(
        modifier = modifier
            .scale(selectionScale)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(name),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MoreOptionsToggle(
    showMoreOptions: Boolean,
    onToggle: () -> Unit
) {
    val iconRotation by animateFloatAsState(
        targetValue = if (showMoreOptions) 180f else 0f,
        animationSpec = MotionTokens.fastTween(),
        label = "moreOptionsRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(iconRotation)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (showMoreOptions) "Less options" else "More options",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MoreOptionsSection(
    note: String,
    onNoteChange: (String) -> Unit,
    onAutoCategorize: () -> Unit,
    date: LocalDateTime,
    onDateClick: () -> Unit,
    currency: String,
    onCurrencyClick: () -> Unit,
    isRecurring: Boolean,
    onRecurringChange: (Boolean) -> Unit,
    recurringFrequency: RecurringFrequency,
    onFrequencyChange: (RecurringFrequency) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = note,
            onValueChange = {
                onNoteChange(it)
                if (it.length > 3) onAutoCategorize()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            placeholder = { Text("Coffee, Uber, Groceries...") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            leadingIcon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (note.isNotEmpty()) {
                    IconButton(onClick = onAutoCategorize) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Auto-categorize",
                            tint = Accent
                        )
                    }
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickOptionCard(
                modifier = Modifier.weight(1f),
                label = "Date",
                value = if (date.toLocalDate() == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("dd MMM")),
                icon = Icons.Default.CalendarToday,
                onClick = onDateClick
            )

            QuickOptionCard(
                modifier = Modifier.weight(1f),
                label = "Currency",
                value = currency,
                icon = Icons.Default.CurrencyExchange,
                onClick = onCurrencyClick
            )
        }

        RecurringToggle(
            isRecurring = isRecurring,
            onToggle = onRecurringChange,
            frequency = recurringFrequency,
            onFrequencyChange = onFrequencyChange
        )
    }
}

@Composable
private fun QuickOptionCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringToggle(
    isRecurring: Boolean,
    onToggle: (Boolean) -> Unit,
    frequency: RecurringFrequency,
    onFrequencyChange: (RecurringFrequency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Recurring Expense",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = isRecurring,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Accent,
                        checkedTrackColor = Accent.copy(alpha = 0.5f)
                    )
                )
            }
            
            if (isRecurring) {
                Spacer(modifier = Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = frequency.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        RecurringFrequency.entries.forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onFrequencyChange(freq)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SaveButton(
    isEditing: Boolean,
    isLoading: Boolean,
    hasAmount: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !isLoading && hasAmount,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                if (isEditing) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isEditing) "Update Transaction" else "Save Expense",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogContent(
    currentDate: LocalDateTime,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDate.toLocalDate().toEpochDay() * 86400000L
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = LocalDate.ofEpochDay(millis / 86400000L)
                    onDateSelected(date)
                }
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun CurrencyPickerDialog(
    currentCurrency: String,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Currency", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                CurrencyService.SUPPORTED_CURRENCIES.take(10).forEach { currency ->
                    val isSelected = currentCurrency == currency.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { onCurrencySelected(currency.code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currency.symbol,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )
                            Column {
                                Text(currency.code, fontWeight = FontWeight.Medium)
                                Text(
                                    currency.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
