package com.example.scrollfit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun ScrollFitTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colors = ProductTokens.semanticColors(darkTheme)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            error = colors.error,
            onError = colors.onError,
            outline = colors.outline,
            surface = colors.surface,
            onSurface = colors.onSurface,
            background = colors.surface,
            onBackground = colors.onSurface,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            error = colors.error,
            onError = colors.onError,
            outline = colors.outline,
            surface = colors.surface,
            onSurface = colors.onSurface,
            background = colors.surface,
            onBackground = colors.onSurface,
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
