package com.finances.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AppThemePreset(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val headlineWeight: FontWeight = FontWeight.Bold,
    val bodyWeight: FontWeight = FontWeight.Normal,
)

private val sharedBg = Color(0xFF0B0F14)
private val sharedSurface = Color(0xFF111820)
private val sharedSurfaceVar = Color(0xFF1A2332)
private val sharedOnSurface = Color(0xFFE8EAED)
private val sharedOnSurfaceVar = Color(0xFFCDD3DC)

val themePresets = listOf(
    AppThemePreset(
        name = "Ocean",
        primary = Color(0xFF6EE7B7),
        secondary = Color(0xFF93C5FD),
        tertiary = Color(0xFFFCA5A5),
        background = sharedBg, surface = sharedSurface, surfaceVariant = sharedSurfaceVar,
        onSurface = sharedOnSurface, onSurfaceVariant = sharedOnSurfaceVar,
    ),
    AppThemePreset(
        name = "Solar",
        primary = Color(0xFFFBBF24),
        secondary = Color(0xFF60A5FA),
        tertiary = Color(0xFFF87171),
        background = sharedBg, surface = sharedSurface, surfaceVariant = sharedSurfaceVar,
        onSurface = sharedOnSurface, onSurfaceVariant = sharedOnSurfaceVar,
    ),
    AppThemePreset(
        name = "Neon",
        primary = Color(0xFFBF5AF2),
        secondary = Color(0xFF64D2FF),
        tertiary = Color(0xFFFF6B6B),
        background = sharedBg, surface = sharedSurface, surfaceVariant = sharedSurfaceVar,
        onSurface = sharedOnSurface, onSurfaceVariant = sharedOnSurfaceVar,
        headlineWeight = FontWeight.ExtraBold,
    ),
    AppThemePreset(
        name = "Mint",
        primary = Color(0xFF4ADE80),
        secondary = Color(0xFFA78BFA),
        tertiary = Color(0xFFEF4444),
        background = sharedBg, surface = sharedSurface, surfaceVariant = sharedSurfaceVar,
        onSurface = sharedOnSurface, onSurfaceVariant = sharedOnSurfaceVar,
    ),
    AppThemePreset(
        name = "Frost",
        primary = Color(0xFF38BDF8),
        secondary = Color(0xFFA78BFA),
        tertiary = Color(0xFFFB7185),
        background = sharedBg, surface = sharedSurface, surfaceVariant = sharedSurfaceVar,
        onSurface = sharedOnSurface, onSurfaceVariant = sharedOnSurfaceVar,
        headlineWeight = FontWeight.SemiBold,
        bodyWeight = FontWeight.Light,
    ),
)

val activeTheme: MutableState<AppThemePreset> = mutableStateOf(themePresets[0])

fun AppThemePreset.toColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = background,
    primaryContainer = primary.copy(alpha = 0.15f).compositeOver(background),
    onPrimaryContainer = primary,
    secondary = secondary,
    onSecondary = background,
    secondaryContainer = secondary.copy(alpha = 0.15f).compositeOver(background),
    onSecondaryContainer = secondary,
    tertiary = tertiary,
    onTertiary = background,
    tertiaryContainer = tertiary.copy(alpha = 0.15f).compositeOver(background),
    onTertiaryContainer = tertiary,
    background = background,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerHighest = surface,
    surfaceContainerHigh = surface,
    surfaceContainer = surface,
    surfaceContainerLow = surface,
    surfaceContainerLowest = surface,
    surfaceTint = Color.Transparent,
    outline = onSurfaceVariant.copy(alpha = 0.3f),
    outlineVariant = onSurfaceVariant.copy(alpha = 0.15f),
    error = tertiary,
    onError = Color.White,
    errorContainer = tertiary.copy(alpha = 0.2f).compositeOver(background),
    onErrorContainer = tertiary,
)

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(
        red = red * a + bg.red * (1 - a),
        green = green * a + bg.green * (1 - a),
        blue = blue * a + bg.blue * (1 - a),
        alpha = 1f
    )
}

fun AppThemePreset.toTypography(): Typography {
    return Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = headlineWeight, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = headlineWeight),
        headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = bodyWeight),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = bodyWeight),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = bodyWeight),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    )
}

@Composable
fun FinancesTheme(content: @Composable () -> Unit) {
    val theme = activeTheme.value
    MaterialTheme(
        colorScheme = theme.toColorScheme(),
        typography = theme.toTypography(),
        content = content
    )
}
