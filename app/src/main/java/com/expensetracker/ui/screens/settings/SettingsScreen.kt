package com.expensetracker.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.ui.components.ScrollAwareBlurScrim
import com.expensetracker.ui.components.StyledAlertDialog
import com.expensetracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToRecurring: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToUpiSync: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsHeader(
                currency = uiState.baseCurrency
            )
        }
    ) { padding ->
        ScrollAwareBlurScrim(scrollState = scrollState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            SettingsSection(title = "General") {
                SettingsItem(
                    icon = Icons.Default.CurrencyExchange,
                    title = "Base Currency",
                    subtitle = "${uiState.baseCurrency} (${CurrencyService.SUPPORTED_CURRENCIES.find { it.code == uiState.baseCurrency }?.name ?: ""})",
                    onClick = { showCurrencyDialog = true }
                )
            }

            SettingsSection(title = "Features") {
                SettingsItem(
                    icon = Icons.Default.SmartToy,
                    title = "Finance Assistant Chatbot",
                    subtitle = "Ask questions about your expenses and budget",
                    onClick = onNavigateToChatbot
                )
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = "UPI Auto-Sync",
                    subtitle = "Sync from GPay, PhonePe, Paytm SMS",
                    onClick = onNavigateToUpiSync
                )
                SettingsItem(
                    icon = Icons.Default.FileUpload,
                    title = "Import Transactions",
                    subtitle = "Import from bank statement CSV",
                    onClick = onNavigateToImport
                )
                SettingsItem(
                    icon = Icons.Default.Repeat,
                    title = "Recurring Expenses",
                    subtitle = "Manage automatic expenses",
                    onClick = onNavigateToRecurring
                )
                SettingsItem(
                    icon = Icons.Default.Assessment,
                    title = "Reports",
                    subtitle = "Export and view reports",
                    onClick = onNavigateToReports
                )
            }

            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version 1.0.4",
                    onClick = { showAboutDialog = true }
                )
            }

        }
        }
    }

    if (showCurrencyDialog) {
        BaseCurrencyDialog(
            selectedCode = uiState.baseCurrency,
            onSelect = { code ->
                viewModel.updateBaseCurrency(code)
                showCurrencyDialog = false
            },
            onDismiss = { showCurrencyDialog = false }
        )
    }

    if (showAboutDialog) {
        StyledAlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Expense Tracker", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Version 1.0.4")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A clean, fast expense tracking app to help you manage your finances.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Base currency picker.
 *
 * A purpose-built dialog rather than an alert with a 15-row body. The old one
 * stacked a symbol, a code and a name on two lines each, which at fifteen
 * currencies overran the alert's body — the list was not scrollable, so the
 * bottom entries were simply unreachable — and its only button was a "Close"
 * that said nothing, since tapping a row already applied and dismissed.
 *
 * Now: one line per currency, a fixed-width check column so the names line up,
 * a bounded scrolling list, and no buttons at all.
 */
@Composable
private fun BaseCurrencyDialog(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Base Currency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                Column(
                    Modifier
                        // Capped so the dialog stays a dialog on a tall screen
                        // instead of stretching to fifteen rows of it.
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    CurrencyService.SUPPORTED_CURRENCIES.forEach { currency ->
                        val isSelected = currency.code == selectedCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else Color.Transparent
                                )
                                .clickable { onSelect(currency.code) }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currency.symbol,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(
                                text = currency.code,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(
                                text = currency.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                // Keeps every name starting at the same x, so
                                // the column of names reads as a column.
                                Spacer(Modifier.width(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsHeader(
    currency: String
) {
    val greeting = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Base Currency: $currency",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
