package com.protctus.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentColor,
    background = DarkBackground,
    surface = SurfaceColor,
    surfaceVariant = SurfaceColorLight,
    onPrimary = PrimaryText,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
    outline = OutlineColor,
    error = ErrorColor,
    tertiary = PremiumGold
)

@Composable
fun ProtctusTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}