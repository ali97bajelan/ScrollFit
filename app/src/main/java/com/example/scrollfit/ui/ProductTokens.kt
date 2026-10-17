package com.example.scrollfit.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class ProductSemanticColors(
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val error: Color,
    val onError: Color,
    val outline: Color,
)

object ProductTokens {
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val minimumInteractiveHeight = 48.dp

    fun semanticColors(darkTheme: Boolean): ProductSemanticColors = if (darkTheme) {
        ProductSemanticColors(
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E1E5),
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            outline = Color(0xFF938F99),
        )
    } else {
        ProductSemanticColors(
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            primary = Color(0xFF6750A4),
            onPrimary = Color.White,
            secondary = Color(0xFF625B71),
            onSecondary = Color.White,
            error = Color(0xFFB3261E),
            onError = Color.White,
            outline = Color(0xFF79747E),
        )
    }
}
