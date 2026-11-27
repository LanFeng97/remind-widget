package com.remind.widget.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** 主色沿用原型的小米橙；三套颜色只是给栏目用的标识色，不进主题 */
val AccentOrange = Color(0xFFFF6A00)
val AccentMint = Color(0xFF00B39B)
val AccentIndigo = Color(0xFF3D5AFE)
val InkPrimary = Color(0xFF141517)
val InkSecondary = Color(0xFF6B7078)
val InkTertiary = Color(0xFF9AA0A8)

/** 栏目可选标识色，顺序即界面上的排列 */
val GroupPalette = listOf(
    AccentOrange, AccentMint, AccentIndigo,
    Color(0xFFE5484D), Color(0xFFF5A623), Color(0xFF8E4EC6),
    Color(0xFF14A3C7), Color(0xFF5F6B7A)
)

private val LightScheme = lightColorScheme(
    primary = AccentOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEEDF),
    onPrimaryContainer = Color(0xFF7A2E00),
    secondary = InkSecondary,
    background = Color(0xFFF7F8FA),
    onBackground = InkPrimary,
    surface = Color.White,
    onSurface = InkPrimary,
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = InkSecondary,
    outline = Color(0xFFE8EAEE),
    error = Color(0xFFE5484D)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A33),
    onPrimary = Color(0xFF3A1400),
    primaryContainer = Color(0xFF4A2200),
    onPrimaryContainer = Color(0xFFFFD9BC),
    secondary = Color(0xFFA0A6AE),
    background = Color(0xFF0D0E10),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF1A1C1F),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF22252A),
    onSurfaceVariant = Color(0xFFA0A6AE),
    outline = Color(0xFF2C3036),
    error = Color(0xFFFF6B6F)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.5.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun RemindTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}
