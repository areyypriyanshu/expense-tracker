package com.expensetracker.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
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

    fun createRoute(transactionId: Long? = null): String {
        return if (transactionId != null) "add_transaction?transactionId=$transactionId" else "add_transaction"
    }

    companion object {
        val bottomNavItems = listOf(Dashboard, Transactions, Analytics, Budgets, Settings)
    }
}

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

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    animationSpec = MotionTokens.enterTween(),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(animationSpec = MotionTokens.enterTween(durationMillis = 180)),
                exit = slideOutVertically(
                    animationSpec = MotionTokens.exitTween(durationMillis = 180),
                    targetOffsetY = { it / 2 }
                ) + fadeOut(animationSpec = MotionTokens.exitTween())
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = MotionTokens.spring(),
                            label = "${screen.route}IconScale"
                        )
                        
                        NavigationBarItem(
                            icon = {
                                Crossfade(
                                    targetState = selected,
                                    animationSpec = MotionTokens.fastTween(),
                                    label = "${screen.route}Icon"
                                ) { isSelected ->
                                    Icon(
                                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title,
                                        modifier = Modifier.scale(iconScale)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AppNavHost(
                navController = navController,
                database = database,
                preferencesManager = preferencesManager,
                context = context
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
    context: android.content.Context
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
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
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = DashboardViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onTransactionClick = { navController.navigate(Screen.AddTransaction.createRoute(it)) },
                onAddTransaction = { navController.navigate(Screen.AddTransaction.createRoute()) },
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = TransactionsViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = AnalyticsViewModel.Factory(database, preferencesManager)
                )
            )
        }

        composable(Screen.Budgets.route) {
            BudgetsScreen(
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
                    factory = AddTransactionViewModel.Factory(database)
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
    return slideIntoContainer(
        towards = forwardDirection(),
        animationSpec = MotionTokens.enterTween(durationMillis = 360)
    ) + fadeIn(animationSpec = MotionTokens.enterTween(durationMillis = 220, delayMillis = 60))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appExitTransition(): ExitTransition {
    return slideOutOfContainer(
        towards = forwardDirection(),
        animationSpec = MotionTokens.exitTween(durationMillis = 240)
    ) + fadeOut(animationSpec = MotionTokens.exitTween(durationMillis = 140))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopEnterTransition(): EnterTransition {
    return slideIntoContainer(
        towards = popDirection(),
        animationSpec = MotionTokens.enterTween(durationMillis = 340)
    ) + fadeIn(animationSpec = MotionTokens.enterTween(durationMillis = 200, delayMillis = 50))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopExitTransition(): ExitTransition {
    return slideOutOfContainer(
        towards = popDirection(),
        animationSpec = MotionTokens.exitTween(durationMillis = 220)
    ) + fadeOut(animationSpec = MotionTokens.exitTween(durationMillis = 140))
}
