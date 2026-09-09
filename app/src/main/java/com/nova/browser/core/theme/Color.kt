package com.nova.browser.core.theme

import androidx.compose.ui.graphics.Color

/** NOVA Glass palette — see spec 04_UI_SYSTEM. */
object NovaColors {
    val Background = Color(0xFF0A0E1A)
    val BackgroundElevated = Color(0xFF0E1424)
    val Surface = Color(0xFF141929)
    val SurfaceVariant = Color(0xFF1B2233)

    val GlassWhite08 = Color(0x14FFFFFF)
    val GlassWhite12 = Color(0x1FFFFFFF)
    val GlassWhite16 = Color(0x29FFFFFF)
    val GlassBorder = Color(0x1AFFFFFF)
    val GlassHighlight = Color(0x33FFFFFF)

    val Primary = Color(0xFF4A9EFF)
    val PrimaryDim = Color(0xFF2B6FCC)
    val Secondary = Color(0xFF00E5FF)
    val Tertiary = Color(0xFFB388FF)
    val Accent = Color(0xFF69F0AE)

    val Error = Color(0xFFFF5252)
    val Warning = Color(0xFFFFD740)
    val Success = Color(0xFF69F0AE)
    val Info = Color(0xFF4A9EFF)

    val TextPrimary = Color(0xF2FFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextTertiary = Color(0x66FFFFFF)
    val TextDisabled = Color(0x3DFFFFFF)

    val Scrim = Color(0x99000000)
    val Shadow = Color(0x4D000000)

    // Light theme counterparts
    val LightBackground = Color(0xFFF4F6FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightGlass = Color(0x0F0A0E1A)
    val LightBorder = Color(0x1A0A0E1A)
    val LightTextPrimary = Color(0xF20A0E1A)
    val LightTextSecondary = Color(0xB30A0E1A)
    val LightTextTertiary = Color(0x660A0E1A)

    /** Score colors used by privacy/security dashboards. */
    fun forScore(score: Int): Color = when {
        score >= 90 -> Success
        score >= 75 -> Accent
        score >= 50 -> Warning
        else -> Error
    }
}
