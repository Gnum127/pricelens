package com.pricelens.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = PriceLensPrimary,
        onPrimary = PriceLensOnPrimary,
        secondary = PriceLensSecondary,
        onSecondary = PriceLensOnSecondary,
        background = PriceLensBackground,
        onBackground = PriceLensOnBackground,
        surface = PriceLensSurface,
        onSurface = PriceLensOnSurface,
        surfaceVariant = PriceLensSurfaceVariant,
        onSurfaceVariant = PriceLensOnSurfaceVariant,
        outline = PriceLensOutline,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PriceLensTypography,
        shapes = PriceLensShapes,
        content = content,
    )
}
