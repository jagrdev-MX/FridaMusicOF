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
    primary = FridaPink,
    onPrimary = DarkTextInverse,
    secondary = FridaPurple,
    onSecondary = DarkTextPrimary,
    tertiary = FridaBlue,

    background = DarkBgPrimary,
    onBackground = DarkTextPrimary,
    surface = DarkBgSecondary,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkBgElevation1,
    onSurfaceVariant = DarkTextSecondary,

    outline = DarkTextDisabled,
    error = StateError,
    onError = DarkTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = FridaPink,
    onPrimary = LightTextInverse,
    secondary = FridaPurple,
    onSecondary = LightTextInverse,
    tertiary = FridaBlue,

    background = LightBgPrimary,
    onBackground = LightTextPrimary,
    surface = LightBgSecondary,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBgElevation1,
    onSurfaceVariant = LightTextSecondary,

    outline = LightTextDisabled,
    error = StateError,
    onError = LightTextInverse
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
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LiquidTypography,
        content = content
    )
}