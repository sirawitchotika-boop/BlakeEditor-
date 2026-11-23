package com.blake.editor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.blake.editor.model.AppThemeMode

data class BlakePalette(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val hairline: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val searchMatchBg: Color,
    val searchActiveBg: Color,
    val searchActiveText: Color,
    val codeBg: Color,
    val danger: Color
)

val WarmPaperPalette = BlakePalette(
    isDark = false,
    canvas = Color(0xFFFAF8F5),
    surface = Color(0xFFF2EDE4),
    surfaceVariant = Color(0xFFE9E2D6),
    hairline = Color(0xFFE2DACD),
    ink = Color(0xFF1C1B19),
    inkSecondary = Color(0xFF6B655C),
    inkMuted = Color(0xFF9C9488),
    accent = Color(0xFFC85A2B),
    accentSoft = Color(0xFFF6E3D9),
    searchMatchBg = Color(0xFFFCE5B4),
    searchActiveBg = Color(0xFFF0A04B),
    searchActiveText = Color(0xFF1C1B19),
    codeBg = Color(0xFFEFEAE0),
    danger = Color(0xFFB83B3B)
)

val CharcoalInkPalette = BlakePalette(
    isDark = true,
    canvas = Color(0xFF141413),
    surface = Color(0xFF1E1D1B),
    surfaceVariant = Color(0xFF2A2825),
    hairline = Color(0xFF2E2C28),
    ink = Color(0xFFEAE6DF),
    inkSecondary = Color(0xFF9B958B),
    inkMuted = Color(0xFF6E6961),
    accent = Color(0xFFE27D4B),
    accentSoft = Color(0xFF35241C),
    searchMatchBg = Color(0xFF4D3D22),
    searchActiveBg = Color(0xFFC56C2C),
    searchActiveText = Color(0xFFFFFFFF),
    codeBg = Color(0xFF1D1C19),
    danger = Color(0xFFE06666)
)

val LocalBlakePalette = staticCompositionLocalOf { WarmPaperPalette }

private val BlakeTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun BlakeTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.PAPER_LIGHT -> false
        AppThemeMode.CHARCOAL_DARK -> true
    }
    val palette = if (useDark) CharcoalInkPalette else WarmPaperPalette

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.inkSecondary,
            outline = palette.hairline,
            outlineVariant = palette.hairline,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.inkSecondary,
            outline = palette.hairline,
            outlineVariant = palette.hairline,
            error = palette.danger
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
                window.decorView.setBackgroundColor(palette.canvas.toArgb())
            }
        }
    }

    CompositionLocalProvider(LocalBlakePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BlakeTypography,
            content = content
        )
    }
}
