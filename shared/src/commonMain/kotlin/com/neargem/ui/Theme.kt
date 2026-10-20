package com.neargem.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.neargem.data.ThemeMode

// Palette from the NearGem screen designs: warm off-white ground, deep teal accent.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F6E6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBE8),
    onPrimaryContainer = Color(0xFF052E2C),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1E1B18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1B18),
    onSurfaceVariant = Color(0xFF6B645C),
    outline = Color(0xFFB9B1A4),
    outlineVariant = Color(0xFFEAE3D8),
    surfaceVariant = Color(0xFFEAE3D8),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5FBDB7),
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF0A4F4C),
    onPrimaryContainer = Color(0xFFCDEBE8),
    background = Color(0xFF1A1815),
    onBackground = Color(0xFFF4EFE6),
    surface = Color(0xFF25221E),
    onSurface = Color(0xFFF4EFE6),
    onSurfaceVariant = Color(0xFFA39B8E),
    outline = Color(0xFF5A5249),
    outlineVariant = Color(0xFF37332D),
    surfaceVariant = Color(0xFF37332D),
)

/** Applies the colour scheme the user chose; [ThemeMode.SYSTEM] follows the device setting. */
@Composable
fun NearGemTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colors,
    ) {
        // Text defaults to the scheme's on-background colour, so it never falls back to black on dark.
        // Body text defaults to Source Sans 3; titles pick displayFont() explicitly.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            ProvideTextStyle(TextStyle(fontFamily = bodyFont()), content)
        }
    }
}
