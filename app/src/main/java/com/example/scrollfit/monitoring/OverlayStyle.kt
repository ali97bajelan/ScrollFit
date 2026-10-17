package com.example.scrollfit.monitoring

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.ui.ProductTokens

data class OverlayStyle(
    val surface: Int,
    val onSurface: Int,
    val primary: Int,
    val onPrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val error: Int,
    val outline: Int,
    val minimumInteractiveHeightDp: Int,
    val titleTextSizeSp: Float,
    val bodyTextSizeSp: Float,
    val buttonTextSizeSp: Float,
) {
    companion object {
        fun resolve(context: Context): OverlayStyle {
            val uiMode = context.resources.configuration.uiMode
            val darkTheme = (uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val colors = ProductTokens.semanticColors(darkTheme)
            return OverlayStyle(
                surface = colors.surface.toArgb(),
                onSurface = colors.onSurface.toArgb(),
                primary = colors.primary.toArgb(),
                onPrimary = colors.onPrimary.toArgb(),
                secondary = colors.secondary.toArgb(),
                onSecondary = colors.onSecondary.toArgb(),
                error = colors.error.toArgb(),
                outline = colors.outline.toArgb(),
                minimumInteractiveHeightDp = ProductTokens.minimumInteractiveHeight.value.toInt(),
                titleTextSizeSp = 24f,
                bodyTextSizeSp = 16f,
                buttonTextSizeSp = 16f,
            )
        }
    }
}

data class OverlayActions(
    val onTakeBreak: () -> Unit,
    val onEarn: (exercise: Exercise?) -> Unit,
    val onContinue: (() -> Unit)?,
    val onOpenPanel: () -> Unit,
    val onStop: () -> Unit,
)
