package com.expensetracker.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.ui.screens.analytics.AnalyticsScreen
import com.expensetracker.ui.screens.analytics.AnalyticsViewModel
import com.expensetracker.ui.screens.budgets.BudgetsScreen
import com.expensetracker.ui.screens.budgets.BudgetsViewModel
import com.expensetracker.ui.screens.dashboard.DashboardScreen
import com.expensetracker.ui.screens.dashboard.DashboardViewModel
import com.expensetracker.ui.screens.recurring.RecurringScreen
import com.expensetracker.ui.screens.recurring.RecurringViewModel
import com.expensetracker.ui.screens.reports.ReportsScreen
import com.expensetracker.ui.screens.reports.ReportsViewModel
import com.expensetracker.ui.screens.chatbot.ChatbotScreen
import com.expensetracker.ui.screens.chatbot.ChatbotViewModel
import com.expensetracker.ui.screens.import.ImportScreen
import com.expensetracker.ui.screens.import.ImportViewModel
import com.expensetracker.ui.screens.upisync.UpiSyncScreen
import com.expensetracker.ui.screens.upisync.UpiSyncViewModel
import com.expensetracker.ui.screens.settings.SettingsScreen
import com.expensetracker.ui.screens.settings.SettingsViewModel
import com.expensetracker.ui.screens.transaction.AddTransactionScreen
import com.expensetracker.ui.screens.transaction.AddTransactionViewModel
import com.expensetracker.ui.screens.transaction.TransactionsScreen
import com.expensetracker.ui.screens.transaction.TransactionsViewModel
import com.expensetracker.ui.theme.MotionTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Transactions : Screen("transactions", "Transactions", Icons.Filled.Receipt, Icons.Outlined.Receipt)
    data object Analytics : Screen("analytics", "Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart)
    data object Budgets : Screen("budgets", "Budgets", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object AddTransaction : Screen("add_transaction?transactionId={transactionId}", "Add", Icons.Filled.Add, Icons.Outlined.Add)
    data object Recurring : Screen("recurring", "Recurring", Icons.Filled.Repeat, Icons.Outlined.Repeat)
    data object Reports : Screen("reports", "Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment)
    data object Import : Screen("import", "Import", Icons.Filled.FileUpload, Icons.Outlined.FileUpload)
    data object UpiSync : Screen("upi_sync", "UPI Sync", Icons.Filled.Sync, Icons.Outlined.Sync)
    data object Chatbot : Screen("chatbot", "Assistant", Icons.Filled.SmartToy, Icons.Outlined.SmartToy)

    fun createRoute(transactionId: Long? = null): String {
        return if (transactionId != null) "add_transaction?transactionId=$transactionId" else "add_transaction"
    }

    companion object {
        val bottomNavItems = listOf(Dashboard, Transactions, Analytics, Budgets, Settings)
    }
}

// Floating pill: 64.dp tall + 12.dp gap below it, so scrollable content must
// clear 64 + 12 dp to stay fully visible above the glass.
private val NavBarHeight = 76.dp

@Composable
fun ExpenseTrackerApp(
    database: ExpenseDatabase,
    preferencesManager: PreferencesManager,
    context: android.content.Context
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val coroutineScope = rememberCoroutineScope()
    val hasSeenUpiPermissionPrompt by preferencesManager.hasSeenUpiPermissionPrompt.collectAsState(initial = false)
    val hasSmsPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED
    var showUpiPermissionPrompt by remember { mutableStateOf(false) }

    val showBottomBar = Screen.bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    LaunchedEffect(hasSeenUpiPermissionPrompt, hasSmsPermission) {
        showUpiPermissionPrompt = !hasSeenUpiPermissionPrompt && !hasSmsPermission
    }

    // Haze state — the source and blur consumer share this handle
    val hazeState = remember { HazeState() }

    // Bottom system gesture inset so the glass bar sits above the gesture pill
    val navBarsBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Total bottom content padding that scrollable screens need so their last
    // item isn't hidden behind the glass bar.
    val bottomContentPadding: Dp =
        if (showBottomBar) NavBarHeight + navBarsBottomDp else 0.dp

    // HazeMaterials.thin() is @Composable — call it directly in composition.
    val isDark = isSystemInDarkTheme()
    @OptIn(ExperimentalHazeMaterialsApi::class)
    val hazeStyle = HazeMaterials.thin()

    // Full-screen Box: content draws edge-to-edge; glass nav bar floats on top
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Scrollable content layer (blur SOURCE) ────────────────────────
        AppNavHost(
            navController = navController,
            database = database,
            preferencesManager = preferencesManager,
            context = context,
            bottomContentPadding = bottomContentPadding,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        )

        // ── Floating glass navigation bar (blur CONSUMER) ────────────────
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = MotionTokens.navbarEnter(),
                initialOffsetY = { it / 2 }
            ) + fadeIn(animationSpec = MotionTokens.navbarFade()),
            exit = slideOutVertically(
                animationSpec = MotionTokens.navbarExit(),
                targetOffsetY = { it / 2 }
            ) + fadeOut(animationSpec = MotionTokens.navbarFade())
        ) {
            FloatingGlassNavBar(
                hazeState = hazeState,
                hazeStyle = hazeStyle,
                isDark = isDark,
                currentDestination = currentDestination,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }

    if (showUpiPermissionPrompt && currentDestination?.route != Screen.UpiSync.route) {
        UpiPermissionOnboardingDialog(
            onGoToSetup = {
                showUpiPermissionPrompt = false
                coroutineScope.launch {
                    preferencesManager.markUpiPermissionPromptSeen()
                }
                navController.navigate(Screen.UpiSync.route) {
                    launchSingleTop = true
                }
            },
            onDismiss = {
                showUpiPermissionPrompt = false
                coroutineScope.launch {
                    preferencesManager.markUpiPermissionPromptSeen()
                }
            }
        )
    }
}

/**
 * Telegram-style floating navigation bar: a rounded, inset "pill" that hovers
 * above the content with a frosted-glass (Haze) background, a soft drop shadow
 * and a subtle hairline border, rather than a full-bleed NavigationBar.
 */
@Composable
private fun FloatingGlassNavBar(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    isDark: Boolean,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f)
            )
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = hazeStyle,
            ) {
                blurRadius = 20.dp
                noiseFactor = 0.08f
                if (!isDark) {
                    tints = listOf(HazeTint(Color(0xFFFFFCF7).copy(alpha = 0.55f)))
                }
            }
            .border(width = 1.dp, color = borderColor, shape = shape),
        shape = shape,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Screen.bottomNavItems.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                // Animate the indicator colour and size so the selection
                // transitions smoothly instead of snapping between tabs.
                val indicatorColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = MotionTokens.navSelection(),
                    label = "${screen.route}Indicator"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = MotionTokens.navSelection(),
                    label = "${screen.route}Content"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            // fillMaxWidth keeps every chip the same size, so the
                            // indicator does not jump between short and long labels.
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(indicatorColor)
                            .selectable(
                                selected = selected,
                                onClick = { onNavigate(screen.route) },
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null,
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.06f else 1f,
                            animationSpec = MotionTokens.spring(),
                            label = "${screen.route}IconScale"
                        )
                        Crossfade(
                            targetState = selected,
                            animationSpec = MotionTokens.navSelection(),
                            label = "${screen.route}Icon"
                        ) { isSelected ->
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(23.dp)
                                    .scale(iconScale)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpiPermissionOnboardingDialog(
    onGoToSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Enable UPI Auto-Sync")
        },
        text = {
            Text(
                "To show your UPI transactions automatically, grant SMS permission from the UPI Auto-Sync screen. Your messages stay on this device."
            )
        },
        confirmButton = {
            TextButton(onClick = onGoToSetup) {
                Text("Open UPI Auto-Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        },
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    database: ExpenseDatabase,
    preferencesManager: PreferencesManager,
    context: android.content.Context,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
        enterTransition = { appEnterTransition() },
        exitTransition = { appExitTransition() },
        popEnterTransition = { appPopEnterTransition() },
        popExitTransition = { appPopExitTransition() }
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddTransaction = { navController.navigate(Screen.AddTransaction.createRoute()) },
                onViewAllTransactions = { navController.navigate(Screen.Transactions.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                onNavigateToChatbot = { navController.navigate(Screen.Chatbot.route) },
                bottomContentPadding = bottomContentPadding,
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = DashboardViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onTransactionClick = { navController.navigate(Screen.AddTransaction.createRoute(it)) },
                onAddTransaction = { navController.navigate(Screen.AddTransaction.createRoute()) },
                bottomContentPadding = bottomContentPadding,
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = TransactionsViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                bottomContentPadding = bottomContentPadding,
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = AnalyticsViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Budgets.route) {
            BudgetsScreen(
                bottomContentPadding = bottomContentPadding,
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = BudgetsViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                onNavigateToImport = { navController.navigate(Screen.Import.route) },
                onNavigateToUpiSync = { navController.navigate(Screen.UpiSync.route) },
                onNavigateToChatbot = { navController.navigate(Screen.Chatbot.route) },
                bottomContentPadding = bottomContentPadding,
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = SettingsViewModel.Factory(preferencesManager)
                )
            )
        }

        composable(Screen.AddTransaction.route) { backStackEntry ->
            val transactionIdArg = backStackEntry.arguments?.getString("transactionId")
            val transactionId = transactionIdArg?.toLongOrNull()?.takeIf { it > 0 }
            AddTransactionScreen(
                transactionId = transactionId,
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() },
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = AddTransactionViewModel.Factory(database, context)
                )
            )
        }

        composable(Screen.Recurring.route) {
            RecurringScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = RecurringViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Reports.route) {
            ReportsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = ReportsViewModel.Factory(database, context)
                )
            )
        }

        composable(Screen.Import.route) {
            ImportScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = ImportViewModel.Factory(database)
                )
            )
        }

        composable(Screen.UpiSync.route) {
            UpiSyncScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = UpiSyncViewModel.Factory(database, context)
                )
            )
        }

        composable(Screen.Chatbot.route) {
            ChatbotScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = ChatbotViewModel.Factory(database, preferencesManager)
                )
            )
        }
    }
}

private fun topLevelRouteIndex(route: String?): Int {
    return Screen.bottomNavItems.indexOfFirst { it.route == route }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardDirection():
    AnimatedContentTransitionScope.SlideDirection {
    val initialIndex = topLevelRouteIndex(initialState.destination.route)
    val targetIndex = topLevelRouteIndex(targetState.destination.route)

    return if (initialIndex >= 0 && targetIndex >= 0 && targetIndex < initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.End
    } else {
        AnimatedContentTransitionScope.SlideDirection.Start
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popDirection():
    AnimatedContentTransitionScope.SlideDirection {
    val initialIndex = topLevelRouteIndex(initialState.destination.route)
    val targetIndex = topLevelRouteIndex(targetState.destination.route)

    return if (initialIndex >= 0 && targetIndex >= 0) {
        if (targetIndex < initialIndex) {
            AnimatedContentTransitionScope.SlideDirection.End
        } else {
            AnimatedContentTransitionScope.SlideDirection.Start
        }
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appEnterTransition(): EnterTransition {
    val direction = forwardDirection()
    return slideInHorizontally(
        animationSpec = MotionTokens.pageEnter(),
        initialOffsetX = { fullWidth ->
            if (direction == AnimatedContentTransitionScope.SlideDirection.Start) fullWidth
            else -fullWidth
        }
    ) + fadeIn(
        animationSpec = MotionTokens.pageFade(),
        initialAlpha = 0f
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appExitTransition(): ExitTransition {
    val direction = forwardDirection()
    return slideOutHorizontally(
        animationSpec = MotionTokens.pageExit(),
        // Parallax: the outgoing page only travels a fraction of the incoming
        // page's distance, which reads as depth instead of a flat swap.
        targetOffsetX = { fullWidth ->
            val travel = (fullWidth * MotionTokens.PageParallax).toInt()
            if (direction == AnimatedContentTransitionScope.SlideDirection.Start) -travel
            else travel
        }
    ) + fadeOut(
        animationSpec = MotionTokens.pageFade(),
        targetAlpha = 0f
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopEnterTransition(): EnterTransition {
    val direction = popDirection()
    return slideInHorizontally(
        animationSpec = MotionTokens.pageEnter(),
        initialOffsetX = { fullWidth ->
            if (direction == AnimatedContentTransitionScope.SlideDirection.Start) fullWidth
            else -fullWidth
        }
    ) + fadeIn(
        animationSpec = MotionTokens.pageFade(),
        initialAlpha = 0f
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopExitTransition(): ExitTransition {
    val direction = popDirection()
    return slideOutHorizontally(
        animationSpec = MotionTokens.pageExit(),
        targetOffsetX = { fullWidth ->
            val travel = (fullWidth * MotionTokens.PageParallax).toInt()
            if (direction == AnimatedContentTransitionScope.SlideDirection.Start) -travel
            else travel
        }
    ) + fadeOut(
        animationSpec = MotionTokens.pageFade(),
        targetAlpha = 0f
    )
}
