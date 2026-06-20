package com.loansai.unassisted.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light theme colors (we'll primarily use light theme as per bank UI guidelines)
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = PrimaryDark,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryVariant,
    onSecondaryContainer = Primary,
    tertiary = Info,
    onTertiary = OnPrimary,
    tertiaryContainer = Info.copy(alpha = 0.1f),
    onTertiaryContainer = Info,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = Error,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SecondaryVariant,
    onSurfaceVariant = OnSurface.copy(alpha = 0.7f),
    outline = OnSurface.copy(alpha = 0.2f)
)

// Dark color scheme - we may not use it, but good to have for future
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = PrimaryDark,
    primaryContainer = Primary,
    onPrimaryContainer = PrimaryLight,
    secondary = SecondaryVariant,
    onSecondary = Primary,
    secondaryContainer = Secondary,
    onSecondaryContainer = Primary,
    tertiary = Info,
    onTertiary = OnPrimary,
    tertiaryContainer = Info.copy(alpha = 0.1f),
    onTertiaryContainer = Info,
    error = ErrorContainer,
    onError = Error,
    errorContainer = Error,
    onErrorContainer = ErrorContainer,
    background = OnBackground,
    onBackground = Background,
    surface = OnSurface.copy(alpha = 0.1f),
    onSurface = Surface,
    surfaceVariant = OnSurface.copy(alpha = 0.2f),
    onSurfaceVariant = Surface.copy(alpha = 0.8f),
    outline = OnSurface.copy(alpha = 0.6f)
)

@Composable
fun LoanAppTheme(
    darkTheme: Boolean = false, // Default to light theme (as per bank guidelines)
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Modern approach for styling the status bar
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}