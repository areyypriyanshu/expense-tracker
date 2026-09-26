package com.expensetracker.ui.screens.upisync

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.UiState
import com.expensetracker.services.upisync.SyncedTransaction
import com.expensetracker.services.upisync.UpiSyncService
import com.expensetracker.ui.theme.Accent
import com.expensetracker.ui.theme.Positive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

data class UpiSyncUiState(
    val uiState: UiState<List<SyncedTransaction>> = UiState.Loading,
    val isScanning: Boolean = false,
    val hasPermission: Boolean = false,
    val importedCount: Int = 0,
    val selectedTransactions: Set<Int> = emptySet(),
    val error: String? = null,
    val lastSyncTime: Long = 0L
)

class UpiSyncViewModel(
    private val transactionRepository: TransactionRepository,
    private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UpiSyncUiState())
    val uiState: StateFlow<UpiSyncUiState> = _uiState.asStateFlow()
    
    private var syncedTransactions = listOf<SyncedTransaction>()
    private val upiSyncService = UpiSyncService(context)
    
    suspend fun getExistingTransactions(): List<Transaction> {
        return try {
            transactionRepository.getAllTransactions().first()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun updatePermissionStatus(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
        if (hasPermission && syncedTransactions.isEmpty()) {
            scanForTransactions()
        }
    }
    
    fun scanForTransactions() {
        _uiState.update { 
            it.copy(
                isScanning = true, 
                error = null,
                lastSyncTime = upiSyncService.getLastSyncTime()
            ) 
        }
    }
    
    fun onTransactionsFound(transactions: List<SyncedTransaction>) {
        syncedTransactions = transactions
        _uiState.update {
            it.copy(
                uiState = UiState.Success(transactions),
                isScanning = false,
                selectedTransactions = transactions.indices.toSet()
            )
        }
    }
    
    fun toggleTransaction(index: Int) {
        _uiState.update { state ->
            val newSelected = if (state.selectedTransactions.contains(index)) {
                state.selectedTransactions - index
            } else {
                state.selectedTransactions + index
            }
            state.copy(selectedTransactions = newSelected)
        }
    }
    
    fun selectAll() {
        _uiState.update { state ->
            val successState = state.uiState as? UiState.Success
            val allIndices = successState?.data?.indices?.toSet() ?: emptySet()
            state.copy(selectedTransactions = allIndices)
        }
    }
    
    fun deselectAll() {
        _uiState.update { it.copy(selectedTransactions = emptySet()) }
    }
    
    fun importSelectedTransactions() {
        val selectedIndices = _uiState.value.selectedTransactions.toList()
        if (selectedIndices.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            
            try {
                val importedHashes = mutableSetOf<String>()
                
                selectedIndices.forEach { index ->
                    if (index < syncedTransactions.size) {
                        val synced = syncedTransactions[index]
                        transactionRepository.insertTransaction(synced.transaction)
                        if (synced.messageHash.isNotEmpty()) {
                            importedHashes.add(synced.messageHash)
                        }
                    }
                }
                
                upiSyncService.addImportedHashes(importedHashes)
                upiSyncService.setLastSyncTime()
                
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        importedCount = selectedIndices.size,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        error = "Failed to import transactions"
                    )
                }
            }
        }
    }
    
    fun reset() {
        syncedTransactions = emptyList()
        _uiState.value = UpiSyncUiState()
    }
    
    class Factory(private val database: ExpenseDatabase, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpiSyncViewModel(
                TransactionRepository(database.transactionDao()),
                context
            ) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiSyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: UpiSyncViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    val upiSyncService = remember { UpiSyncService(context) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        viewModel.updatePermissionStatus(allGranted)
    }
    
    LaunchedEffect(Unit) {
        val hasPermission = upiSyncService.hasSmsPermission()
        viewModel.updatePermissionStatus(hasPermission)
        
        if (hasPermission) {
            viewModel.scanForTransactions()
        }
    }
    
    LaunchedEffect(uiState.isScanning, uiState.hasPermission) {
        if (uiState.isScanning && uiState.hasPermission) {
            val existingTransactions = viewModel.getExistingTransactions()
            val transactions = upiSyncService.scanSmsSince(7, existingTransactions)
            viewModel.onTransactionsFound(transactions)
        }
    }
    
    LaunchedEffect(uiState.importedCount) {
        if (uiState.importedCount > 0) {
            snackbarHostState.showSnackbar(
                message = "${uiState.importedCount} transactions imported!",
                duration = SnackbarDuration.Short
            )
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("UPI Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val successState = uiState.uiState as? UiState.Success
                    if (successState != null && successState.data.isNotEmpty()) {
                        TextButton(onClick = {
                            if (uiState.selectedTransactions.size == successState.data.size) {
                                viewModel.deselectAll()
                            } else {
                                viewModel.selectAll()
                            }
                        }) {
                            Text(
                                if (uiState.selectedTransactions.size == successState.data.size) "Deselect All" else "Select All"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !uiState.hasPermission -> {
                    PermissionRequestView(
                        onRequestPermission = {
                            permissionLauncher.launch(upiSyncService.getRequiredPermissions())
                        }
                    )
                }
                
                uiState.isScanning -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Scanning SMS for UPI transactions...")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Looking for messages from payment apps and banks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                uiState.uiState is UiState.Success -> {
                    val transactions = (uiState.uiState as UiState.Success).data
                    
                    if (transactions.isEmpty()) {
                        EmptySyncView()
                    } else {
                        SyncTransactionList(
                            transactions = transactions,
                            selectedIndices = uiState.selectedTransactions,
                            onToggle = viewModel::toggleTransaction,
                            onImport = viewModel::importSelectedTransactions,
                            importedCount = uiState.importedCount
                        )
                    }
                }
                
                else -> {
                    ErrorView(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = {
                            viewModel.reset()
                            viewModel.scanForTransactions()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Message,
            contentDescription = "SMS Permission Icon",
            modifier = Modifier.size(80.dp),
            tint = Accent.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "SMS Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "To sync UPI transactions automatically, we need permission to read SMS from payment apps and banks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "What we access:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• SMS from Google Pay, PhonePe, Paytm", style = MaterialTheme.typography.bodySmall)
                Text("• Bank transaction alerts (HDFC, SBI, ICICI, etc.)", style = MaterialTheme.typography.bodySmall)
                Text("• UPI notifications", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your data stays on your device. We never send your SMS anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Positive
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Permission", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptySyncView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Positive.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No New Transactions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "We scanned your SMS and didn't find any new UPI transactions to import.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Try Again")
        }
    }
}

@Composable
private fun SyncTransactionList(
    transactions: List<SyncedTransaction>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    onImport: () -> Unit,
    importedCount: Int
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Accent.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${selectedIndices.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${transactions.size} transactions found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = Accent
                        )
                    }
                }
            }
            
            items(transactions.indices.toList()) { index ->
                val synced = transactions[index]
                val isSelected = selectedIndices.contains(index)
                
                TransactionSyncItem(
                    syncedTransaction = synced,
                    isSelected = isSelected,
                    isImported = false,
                    onToggle = { onToggle(index) }
                )
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (importedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Positive
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$importedCount transactions imported successfully!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Positive
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Button(
                    onClick = onImport,
                    enabled = selectedIndices.isNotEmpty() && importedCount == 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedIndices.isEmpty()) "Select transactions" else "Import ${selectedIndices.size} Transactions",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionSyncItem(
    syncedTransaction: SyncedTransaction,
    isSelected: Boolean,
    isImported: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Accent.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Accent)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = syncedTransaction.transaction.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "₹${String.format("%.2f", syncedTransaction.transaction.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (syncedTransaction.transaction.note.contains("debited", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Positive
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = syncedTransaction.transaction.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = syncedTransaction.transaction.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = syncedTransaction.sourceType,
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent
                    )
                }
            }
        }
    }
}
