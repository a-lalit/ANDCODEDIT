package com.andcodedit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = BrandTeal,
    onPrimary = EditorBackground,
    secondary = BrandBlue,
    tertiary = BrandPurple,
    background = EditorBackground,
    onBackground = EditorForeground,
    surface = EditorSurface,
    onSurface = EditorForeground,
    surfaceVariant = EditorSurfaceVariant,
    outline = EditorBorder,
    error = ErrorRed
)

private val LightColors = lightColorScheme(
    primary = BrandTeal,
    secondary = BrandBlue,
    tertiary = BrandPurple,
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightSurface,
    onSurface = LightForeground,
    error = ErrorRed
)

/**
 * Root theme for ANDCODEDIT. Defaults to dark (the IDE identity) but honours the
 * system setting when [darkTheme] is left at its default.
 */
@Composable
fun ANDCODEDITTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
