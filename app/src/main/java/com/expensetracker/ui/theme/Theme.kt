package com.expensetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Primary = Color(0xFF0F3D34)
val PrimaryVariant = Color(0xFF155347)
val Secondary = Color(0xFF345E7D)
val Accent = Color(0xFFDFAF3F)

val BackgroundLight = Color(0xFFFAF8F2)
val SurfaceLight = Color(0xFFFFFCF7)
val SurfaceVariantLight = Color(0xFFF1ECE1)
val OnBackgroundLight = Color(0xFF1D211F)
val OnSurfaceLight = Color(0xFF1D211F)
val OnSurfaceVariantLight = Color(0xFF69736E)

val BackgroundDark = Color(0xFF101412)
val SurfaceDark = Color(0xFF171D1A)
val SurfaceVariantDark = Color(0xFF222B27)
val OnBackgroundDark = Color(0xFFE8E5DC)
val OnSurfaceDark = Color(0xFFE8E5DC)
val OnSurfaceVariantDark = Color(0xFFA8B0AA)

val Positive = Color(0xFF2F8F6B)
val Negative = Color(0xFFC75151)
val Warning = Color(0xFFC58A24)

val CategoryColors = listOf(
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
    Color(0xFF84CC16),
    Color(0xFFF97316),
    Color(0xFF6366F1),
    Color(0xFF14B8A6),
    Color(0xFF64748B)
)

fun getCategoryColor(category: String): Color {
    val index = kotlin.math.abs(category.hashCode()) % CategoryColors.size
    return CategoryColors[index]
}

fun getCategoryIcon(category: String): ImageVector {
    return when {
        category.contains("Food", ignoreCase = true) -> Icons.Default.Restaurant
        category.contains("Transport", ignoreCase = true) -> Icons.Default.DirectionsCar
        category.contains("Shop", ignoreCase = true) -> Icons.Default.ShoppingBag
        category.contains("Entertainment", ignoreCase = true) -> Icons.Default.Movie
        category.contains("Bill", ignoreCase = true) -> Icons.Default.Receipt
        category.contains("Health", ignoreCase = true) -> Icons.Default.LocalHospital
        category.contains("Education", ignoreCase = true) -> Icons.Default.School
        category.contains("Personal", ignoreCase = true) -> Icons.Default.Spa
        category.contains("Travel", ignoreCase = true) -> Icons.Default.Flight
        category.contains("Grocery", ignoreCase = true) -> Icons.Default.LocalGroceryStore
        category.contains("Income", ignoreCase = true) -> Icons.Default.Payments
        category.contains("Salary", ignoreCase = true) -> Icons.Default.AccountBalance
        category.contains("Investment", ignoreCase = true) -> Icons.AutoMirrored.Filled.TrendingUp
        category.contains("Gift", ignoreCase = true) -> Icons.Default.CardGiftcard
        category.contains("Refund", ignoreCase = true) -> Icons.AutoMirrored.Filled.ReceiptLong
        else -> Icons.Default.MoreHoriz
    }
}

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBE4),
    onPrimaryContainer = Primary,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8F0),
    onSecondaryContainer = Secondary,
    tertiary = Accent,
    onTertiary = Color(0xFF2A2210),
    tertiaryContainer = Color(0xFFF4E7C2),
    onTertiaryContainer = Color(0xFF5D4612),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFFE1DACE),
    outlineVariant = Color(0xFFECE6DA),
    error = Negative,
    onError = Color.White,
    errorContainer = Negative.copy(alpha = 0.1f),
    onErrorContainer = Negative,
    inverseSurface = Color(0xFF1D211F),
    inverseOnSurface = Color(0xFFFAF8F2),
    inversePrimary = Accent
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9FD6C1),
    onPrimary = Color(0xFF0B2D26),
    primaryContainer = Color(0xFF1B4F43),
    onPrimaryContainer = Color(0xFFDCEBE4),
    secondary = Color(0xFFB7D0E3),
    onSecondary = Color(0xFF143247),
    secondaryContainer = Color(0xFF264A64),
    onSecondaryContainer = Color(0xFFDCE8F0),
    tertiary = Accent,
    onTertiary = Color(0xFF2A2210),
    tertiaryContainer = Color(0xFF5D4612),
    onTertiaryContainer = Color(0xFFF4E7C2),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = Color(0xFF52525B),
    outlineVariant = Color(0xFF3F3F46),
    error = Color(0xFFF87171),
    onError = Color(0xFF1A1A1A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    inverseSurface = Color(0xFFE4E4E4),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF6D28D9)
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val isDark = colorScheme.background == BackgroundDark
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
