package com.expensetracker.ui.screens.transaction

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.expensetracker.data.model.Category
import com.expensetracker.data.model.RecurringFrequency
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.ui.components.CategoryChip
import com.expensetracker.ui.components.CurrencyPickerDialog
import com.expensetracker.ui.theme.MotionTokens
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Add / edit expense.
 *
 * The form used to open on a greeting card, two full-width outlined receipt
 * buttons and a five-chip amount row before the user had entered anything, and
 * it buried a second copy of the date and currency pickers behind a
 * "More options" expander at the bottom. Twelve categories went into a 4-wide
 * grid of cards, each with a 44dp icon disc, so the picker alone was taller than
 * the rest of the form, and the Save button sat at the end of a scroll.
 *
 * What it is now: one bordered row holding the amount and the currency it is
 * denominated in, the three choices a new entry actually needs (type, amount,
 * category) in that order, optional detail below, and Save pinned where the
 * thumb already is. Everything else is either gone or a single quiet line.
 */
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
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var pendingReceiptFile by remember { mutableStateOf<File?>(null) }

    val receiptCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val capturedFile = pendingReceiptFile
        pendingReceiptFile = null
        if (saved && capturedFile != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                capturedFile
            )
            viewModel.scanReceipt(uri, capturedFile)
        } else {
            capturedFile?.delete()
        }
    }

    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.scanReceipt(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val file = createReceiptImageFile(context)
                pendingReceiptFile = file
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                receiptCaptureLauncher.launch(uri)
            } catch (_: Exception) {
                pendingReceiptFile?.delete()
                pendingReceiptFile = null
                viewModel.showReceiptScanError("Could not open the camera. Please choose an image instead.")
            }
        } else {
            viewModel.showReceiptScanError("Camera permission is required to scan receipts.")
        }
    }

    val isEditing = transactionId != null && transactionId > 0
    val hasAmount = uiState.amount.toDoubleOrNull()?.let { it > 0 } == true

    val scanReceipt = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                val file = createReceiptImageFile(context)
                pendingReceiptFile = file
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                receiptCaptureLauncher.launch(uri)
            } catch (_: Exception) {
                pendingReceiptFile?.delete()
                pendingReceiptFile = null
                viewModel.showReceiptScanError("Could not open the camera. Please choose an image instead.")
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEditing) "Edit Expense" else "Add Expense",
                            style = MaterialTheme.typography.titleLarge,
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
                HairLineDivider()
            }
        },
        bottomBar = {
            SaveBar(
                isEditing = isEditing,
                isLoading = uiState.isLoading,
                isScanning = uiState.isScanningReceipt,
                hasAmount = hasAmount,
                onClick = {
                    keyboardController?.hide()
                    viewModel.saveTransaction(onSaveSuccess)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                // The form grows when the recurring frequency picker or a scan
                // result appears; a size tween keeps that from snapping.
                .animateContentSize(animationSpec = MotionTokens.gentle())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            AmountField(
                amount = uiState.amount,
                currency = uiState.currency,
                onAmountChange = viewModel::onAmountChange,
                onCurrencyClick = { showCurrencyPicker = true },
                focusRequester = focusRequester,
                keyboardController = keyboardController
            )

            // Directly under the amount, and not at the foot of the form. These
            // are a way of *entering* an amount, so they belong beside the field
            // they fill — and at the bottom of the form they were below the
            // category grid on every phone short enough to scroll, which made a
            // headline feature something you had to go looking for.
            ReceiptActions(
                isScanning = uiState.isScanningReceipt,
                onScanReceipt = scanReceipt,
                onChooseImage = {
                    receiptPickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )

            if (uiState.isScanningReceipt || uiState.receiptScanMessage != null) {
                ReceiptStatus(
                    isScanning = uiState.isScanningReceipt,
                    message = uiState.receiptScanMessage,
                    isError = uiState.receiptScanIsError,
                    onDismissMessage = viewModel::clearReceiptScanMessage
                )
            }

            if (uiState.receiptReviewPending) {
                OcrReviewBanner(onReject = viewModel::rejectReceiptScan)
            }

            TypeToggle(
                isIncome = uiState.isIncome,
                onToggle = viewModel::onIsIncomeChange
            )

            if (!isEditing) {
                QuickAmounts(
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
                    if (!isEditing && uiState.amount.isNotEmpty() && !uiState.receiptReviewPending) {
                        keyboardController?.hide()
                        viewModel.saveTransaction(onSaveSuccess)
                    }
                }
            )

            NoteField(
                note = uiState.note,
                onNoteChange = viewModel::onNoteChange,
                onAutoCategorize = viewModel::autoCategorize
            )

            DetailsSection(
                date = uiState.date,
                onDateClick = { showDatePicker = true },
                isRecurring = uiState.isRecurring,
                onRecurringChange = viewModel::onRecurringChange,
                frequency = uiState.recurringFrequency,
                onFrequencyChange = viewModel::onRecurringFrequencyChange
            )

            // The last thing in the form, because it is raised by pressing the
            // button pinned to the bottom of the screen.
            if (uiState.error != null) {
                ErrorMessage(message = uiState.error ?: "An unknown error occurred")
            }

            Spacer(modifier = Modifier.height(8.dp))
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
            selectedCode = uiState.currency,
            onSelect = {
                viewModel.onCurrencyChange(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

/**
 * The strip the Save bar has to clear at the bottom of the screen: the
 * keyboard, or the navigation bar, whichever is taller.
 *
 * They overlap — the IME sits over the navigation bar — so padding by both
 * would leave a nav-bar-sized gap under an open keyboard. `union` takes the
 * larger of the two per side, which is the one number that is right in both
 * states.
 */
@Composable
private fun saveBarInsets(): WindowInsets =
    WindowInsets.ime.union(WindowInsets.navigationBars)

private fun createReceiptImageFile(context: android.content.Context): File {
    val directory = File(context.cacheDir, "receipts")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Unable to create receipt cache directory")
    }
    return File.createTempFile("receipt_", ".jpg", directory)
}

// ─────────────────────────────────────────────────────────────────────────────
// Form pieces
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The amount, and the currency it is counted in, as one control.
 *
 * They were previously three apart: the symbol was a prefix inside the amount
 * field, and the currency that chose it sat in a second card under a collapsed
 * section. Tapping a currency now happens where the currency is read.
 */
@Composable
private fun AmountField(
    amount: String,
    currency: String,
    onAmountChange: (String) -> Unit,
    onCurrencyClick: () -> Unit,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurrencyButton(currency = currency, onClick = onCurrencyClick)

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                prefix = {
                    Text(
                        text = CurrencyService.getSymbol(currency),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                placeholder = {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                shape = RoundedCornerShape(10.dp),
                // The row around it is the border. A second one, drawn by the
                // field itself, is what made the old field look boxed-in.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun CurrencyButton(
    currency: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currency,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Change currency",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TypeToggle(
    isIncome: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = MaterialTheme.colorScheme.surface,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveBorderColor = MaterialTheme.colorScheme.outline
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !isIncome,
            onClick = { onToggle(false) },
            shape = SegmentedButtonDefaults.itemShape(
                index = 0,
                count = 2,
                baseShape = RoundedCornerShape(12.dp)
            ),
            colors = colors,
            // The check mark duplicates the filled segment, and the arrow
            // already says which way the money moves.
            icon = {},
            label = { TypeLabel(text = "Expense", icon = Icons.Default.ArrowDownward) }
        )
        SegmentedButton(
            selected = isIncome,
            onClick = { onToggle(true) },
            shape = SegmentedButtonDefaults.itemShape(
                index = 1,
                count = 2,
                baseShape = RoundedCornerShape(12.dp)
            ),
            colors = colors,
            icon = {},
            label = { TypeLabel(text = "Income", icon = Icons.Default.ArrowUpward) }
        )
    }
}

@Composable
private fun TypeLabel(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun QuickAmounts(
    amounts: List<Int>,
    currency: String,
    onAmountSelected: (Int) -> Unit
) {
    val symbol = CurrencyService.getSymbol(currency)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Scrolls rather than squeezing five pills into the width, which
            // truncated "₹1000" on a narrow phone.
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        amounts.forEach { amount ->
            SelectPill(
                text = "$symbol$amount",
                selected = false,
                onClick = { onAmountSelected(amount) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    categories: List<Category>,
    selectedCategory: String,
    isEditing: Boolean,
    onCategorySelected: (String) -> Unit,
    onCategoryAndSave: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Category")

        // Chips, not a grid of cards. The grid gave every category a 44dp icon
        // disc and a full card, so twelve of them filled more of the screen than
        // the amount, the note and the Save button put together.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.forEach { category ->
                CategoryChip(
                    category = category.name,
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
        }
    }
}

/**
 * The note, which is also what drives the category guess.
 *
 * There used to be a sparkle button in the trailing icon that ran the guess on
 * demand. It advertised itself as a second, better way to categorise when the
 * guess had already been running silently on every keystroke past the fourth
 * character — so the button did nothing the typing did not. The guess stays;
 * the button goes.
 */
@Composable
private fun NoteField(
    note: String,
    onNoteChange: (String) -> Unit,
    onAutoCategorize: () -> Unit
) {
    OutlinedTextField(
        value = note,
        onValueChange = {
            onNoteChange(it)
            if (it.length > 3) onAutoCategorize()
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Add a note",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = fieldColors()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSection(
    date: LocalDateTime,
    onDateClick: () -> Unit,
    isRecurring: Boolean,
    onRecurringChange: (Boolean) -> Unit,
    frequency: RecurringFrequency,
    onFrequencyChange: (RecurringFrequency) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CalendarToday,
                text = if (date.toLocalDate() == LocalDate.now()) {
                    "Today"
                } else {
                    date.format(DateTimeFormatter.ofPattern("dd MMM"))
                },
                onClick = onDateClick
            )
            DetailPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Repeat,
                text = if (isRecurring) "Recurring" else "One-time",
                onClick = { onRecurringChange(!isRecurring) },
                selected = isRecurring
            )
        }

        if (isRecurring) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurringFrequency.entries.forEach { entry ->
                    SelectPill(
                        text = entry.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = entry == frequency,
                        onClick = { onFrequencyChange(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun ReceiptActions(
    isScanning: Boolean,
    onScanReceipt: () -> Unit,
    onChooseImage: () -> Unit
) {
    // Two text buttons on one line. As outlined buttons they were a full-width
    // band of chrome above the amount field, competing with the only field on
    // this screen that the user has to fill in.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onScanReceipt, enabled = !isScanning) {
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Scan receipt", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onChooseImage, enabled = !isScanning) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Choose image", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReceiptStatus(
    isScanning: Boolean,
    message: String?,
    isError: Boolean,
    onDismissMessage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isError) Icons.Default.Error else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = message ?: "Reading receipt…",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (!isScanning) {
                IconButton(onClick = onDismissMessage, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss receipt message",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrReviewBanner(onReject: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Receipt scanned",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Check the amount, category and note, then save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onReject) {
                Text("Undo", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The Save button, pinned to the bottom of the screen.
 *
 * It used to be the last item in the scroll, which meant an entry could not be
 * completed without scrolling past the whole form — and on a phone with the
 * keyboard up, that scroll was the only way to reach it.
 */
@Composable
private fun SaveBar(
    isEditing: Boolean,
    isLoading: Boolean,
    isScanning: Boolean,
    hasAmount: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Rides above the keyboard, and clears the navigation bar when it is
            // closed. Whichever is taller — the two overlap, so summing them
            // would leave a nav-bar-sized gap under an open keyboard.
            //
            // MainActivity calls enableEdgeToEdge(), which stops the window
            // resizing for the keyboard even though the manifest still asks for
            // adjustResize, so an app drawing edge to edge has to inset for the
            // IME itself. This one did not: an open keyboard simply lay on top
            // of the form, so the note being typed was underneath it and Save
            // was unreachable. Because this is the Scaffold's bottomBar, the
            // inset reaches the content too — the scroll area now ends above
            // the keyboard, which is what lets the note be scrolled into view.
            .windowInsetsPadding(saveBarInsets())
    ) {
        HairLineDivider()
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(52.dp),
            enabled = !isLoading && !isScanning && hasAmount,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (isEditing) "Update Expense" else "Save Expense",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HairLineDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** The form's text fields share one resting look, so focus is the only change. */
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    cursorColor = MaterialTheme.colorScheme.primary
)

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
