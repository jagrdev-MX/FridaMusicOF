package com.jagr.fridamusic.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LiquidPrimary,
    secondary = LiquidSecondary,
    background = LiquidBackgroundDark,
    surface = LiquidSurfaceDark,
    onBackground = LiquidTextDark,
    onSurface = LiquidTextDark,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = LiquidTextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = LiquidPrimary,
    secondary = LiquidSecondary,
    background = LiquidBackgroundLight,
    surface = LiquidSurfaceLight,
    onBackground = LiquidTextLight,
    onSurface = LiquidTextLight,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = LiquidTextSecondaryLight
)

@Composable
fun FridaMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            window.navigationBarColor = colorScheme.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LiquidTypography,
        content = content
    )
}