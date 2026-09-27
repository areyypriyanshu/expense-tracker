package com.expensetracker.ui.screens.transaction

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.data.model.Transaction
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.ui.components.*
import com.expensetracker.ui.navigation.LocalScrollBlurProvider
import com.expensetracker.ui.theme.*
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onTransactionClick: (Long) -> Unit,
    onAddTransaction: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    viewModel: TransactionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                TransactionsHeader(
                    greeting = greeting,
                    currency = uiState.baseCurrency,
                    todaySpend = uiState.todaySpend,
                    weeklySpend = uiState.weeklySpend,
                    monthlySpend = uiState.monthlySpend
                )

                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                // Pinned under the search, and always open.
                //
                // Filtering used to live behind a 44dp circle in the header
                // that expanded a card — so the one way to slice this list was
                // invisible, the card arrived by shoving the list down, and once
                // you had scrolled there was nothing on screen to say a filter
                // was on. A row of chips that is simply there says what is
                // filtered at all times, and the header gets its clutter back.
                CategoryFilterRow(
                    categories = uiState.categories,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = viewModel::onCategorySelected
                )
            }
        },
        floatingActionButton = {
            // Out of the way once the list moves, so it never sits on top of
            // the rows it would be covering. A solid fill needs this: the slot
            // overlays the content rather than reserving space for it, so a
            // button that stays put hides whatever scrolls under it.
            val isScrolled = LocalScrollBlurProvider.current.isScrolled
            AnimatedVisibility(
                visible = !isScrolled,
                enter = scaleIn(animationSpec = MotionTokens.fastTween(), initialScale = 0.85f) +
                    fadeIn(animationSpec = MotionTokens.fastTween()),
                exit = scaleOut(animationSpec = MotionTokens.fastTween(), targetScale = 0.85f) +
                    fadeOut(animationSpec = MotionTokens.fastTween())
            ) {
                FloatingActionButton(
                    onClick = onAddTransaction,
                    modifier = Modifier.padding(bottom = bottomContentPadding),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        }
    ) { padding ->
        val listState = rememberLazyListState()

        // ScrollAwareBlurScrim tracks listState and paints an animated gradient
        // scrim in the nav-bar strip; it also pushes isScrolled into
        // LocalScrollBlurProvider so the glass nav bar adjusts its own blur.
        ScrollAwareBlurScrim(listState = listState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .animateContentSize(animationSpec = MotionTokens.gentle())
            ) {

                if (uiState.isLoading) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + bottomContentPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(5) {
                            LoadingShimmer()
                        }
                    }
                } else if (uiState.filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = if (uiState.searchQuery.isNotEmpty() || uiState.selectedCategory != null)
                                Icons.Outlined.SearchOff else Icons.Outlined.Receipt,
                            title = if (uiState.searchQuery.isNotEmpty() || uiState.selectedCategory != null)
                                "No matching transactions"
                            else
                                "No transactions yet",
                            subtitle = if (uiState.searchQuery.isNotEmpty() || uiState.selectedCategory != null)
                                "Try adjusting your search or filters"
                            else
                                "Start tracking your expenses by adding your first transaction",
                            actionLabel = if (uiState.searchQuery.isEmpty() && uiState.selectedCategory == null)
                                "Add Expense" else null,
                            onAction = if (uiState.searchQuery.isEmpty() && uiState.selectedCategory == null)
                                onAddTransaction else null
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp + bottomContentPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.filteredTransactions,
                            key = { it.id }
                        ) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                onClick = { onTransactionClick(transaction.id) },
                                onDelete = { transactionToDelete = transaction }
                            )
                        }
                    }
                }
            }
        } // end ScrollAwareBlurScrim
        
        if (transactionToDelete != null) {
            transactionToDelete?.let { target ->
                ConfirmDeleteDialog(
                    title = "Delete this expense?",
                    detail = "${CurrencyService.formatAmount(target.amount, target.currency)} · ${target.category}",
                    onConfirm = {
                        viewModel.deleteTransaction(target)
                        transactionToDelete = null
                    },
                    onDismiss = { transactionToDelete = null }
                )
            }
        }
    }
}

@Composable
private fun TransactionsHeader(
    greeting: String,
    currency: String,
    todaySpend: Double,
    weeklySpend: Double,
    monthlySpend: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Transactions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

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

/**
 * The category filter, as a row of chips that is simply always there.
 *
 * One chip family, one look: the "All" chip is the same [CategoryChip] with a
 * list glyph and the neutral colour, rather than a hand-styled `FilterChip`
 * sitting next to twelve that are styled by category — which is what the old
 * row looked like, two components that matched in size but not in voice.
 */
@Composable
private fun CategoryFilterRow(
    categories: List<com.expensetracker.data.model.Category>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            CategoryChip(
                category = "All",
                isSelected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                icon = Icons.Outlined.List,
                color = NeutralCategoryColor
            )
        }
        items(categories, key = { it.name }) { category ->
            CategoryChip(
                category = category.name,
                isSelected = selectedCategory == category.name,
                onClick = { onCategorySelected(category.name) }
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { 
            Text(
                "Search transactions...",
                style = MaterialTheme.typography.bodyMedium
            ) 
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}
