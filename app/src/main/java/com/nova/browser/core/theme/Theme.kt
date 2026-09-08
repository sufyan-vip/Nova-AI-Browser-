package com.nova.browser.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaColors.Primary,
    onPrimary = Color.White,
    primaryContainer = NovaColors.PrimaryDim,
    onPrimaryContainer = Color.White,
    secondary = NovaColors.Secondary,
    onSecondary = Color(0xFF00202A),
    secondaryContainer = Color(0xFF10394A),
    onSecondaryContainer = NovaColors.Secondary,
    tertiary = NovaColors.Tertiary,
    onTertiary = Color(0xFF20123A),
    background = NovaColors.Background,
    onBackground = NovaColors.TextPrimary,
    surface = NovaColors.Surface,
    onSurface = NovaColors.TextPrimary,
    surfaceVariant = NovaColors.SurfaceVariant,
    onSurfaceVariant = NovaColors.TextSecondary,
    error = NovaColors.Error,
    onError = Color.White,
    outline = NovaColors.GlassBorder,
    outlineVariant = NovaColors.GlassWhite08,
    scrim = NovaColors.Scrim
)

private val NovaLightColorScheme = lightColorScheme(
    primary = NovaColors.PrimaryDim,
    onPrimary = Color.White,
    secondary = Color(0xFF0091A7),
    tertiary = Color(0xFF6B45C9),
    background = NovaColors.LightBackground,
    onBackground = NovaColors.LightTextPrimary,
    surface = NovaColors.LightSurface,
    onSurface = NovaColors.LightTextPrimary,
    surfaceVariant = Color(0xFFE6EAF3),
    onSurfaceVariant = NovaColors.LightTextSecondary,
    error = Color(0xFFC62828),
    outline = NovaColors.LightBorder
)

/** Extra tokens Material3 has no slot for. */
data class NovaExtendedColors(
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val accent: Color,
    val warning: Color,
    val success: Color,
    val textTertiary: Color,
    val isDark: Boolean
)

val LocalNovaColors = staticCompositionLocalOf {
    NovaExtendedColors(
        glass = NovaColors.GlassWhite08,
        glassStrong = NovaColors.GlassWhite16,
        glassBorder = NovaColors.GlassBorder,
        accent = NovaColors.Accent,
        warning = NovaColors.Warning,
        success = NovaColors.Success,
        textTertiary = NovaColors.TextTertiary,
        isDark = true
    )
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> NovaDarkColorScheme
        else -> NovaLightColorScheme
    }

    val extended = if (darkTheme) {
        NovaExtendedColors(
            glass = NovaColors.GlassWhite08,
            glassStrong = NovaColors.GlassWhite16,
            glassBorder = NovaColors.GlassBorder,
            accent = NovaColors.Accent,
            warning = NovaColors.Warning,
            success = NovaColors.Success,
            textTertiary = NovaColors.TextTertiary,
            isDark = true
        )
    } else {
        NovaExtendedColors(
            glass = NovaColors.LightGlass,
            glassStrong = Color(0x1F0A0E1A),
            glassBorder = NovaColors.LightBorder,
            accent = Color(0xFF00A160),
            warning = Color(0xFFB26A00),
            success = Color(0xFF00A160),
            textTertiary = NovaColors.LightTextTertiary,
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalNovaColors provides extended,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NovaTypography,
            shapes = NovaShapes,
            content = content
        )
    }
}

object NovaTheme {
    val extended: NovaExtendedColors
        @Composable get() = LocalNovaColors.current
}
