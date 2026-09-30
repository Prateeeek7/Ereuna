package com.researchradar.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Material 3 color scheme mapped from our RadarColors.
 * We override background, surface, onBackground, onSurface, primary, etc.
 */
private val LightMaterialColors = lightColorScheme(
    background = LightRadarColors.paper,
    surface = LightRadarColors.surface,
    onBackground = LightRadarColors.ink,
    onSurface = LightRadarColors.ink,
    primary = LightRadarColors.accent,
    onPrimary = LightRadarColors.surface,
    outline = LightRadarColors.rule,
    surfaceVariant = LightRadarColors.accentWash,
)

private val DarkMaterialColors = darkColorScheme(
    background = DarkRadarColors.paper,
    surface = DarkRadarColors.surface,
    onBackground = DarkRadarColors.ink,
    onSurface = DarkRadarColors.ink,
    primary = DarkRadarColors.accent,
    onPrimary = DarkRadarColors.surface,
    outline = DarkRadarColors.rule,
    surfaceVariant = DarkRadarColors.accentWash,
)

/**
 * Ereuna theme — "lab notebook" look.
 *
 * Wraps Material 3 with our own color tokens, typography, and CompositionLocals.
 * Access custom tokens via [RadarTheme.colors] and [RadarTheme.typography].
 */
@Composable
fun ResearchRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val radarColors = if (darkTheme) DarkRadarColors else LightRadarColors
    val materialColors = if (darkTheme) DarkMaterialColors else LightMaterialColors

    CompositionLocalProvider(
        LocalRadarColors provides radarColors,
        LocalRadarTypography provides RadarType,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content,
        )
    }
}

/**
 * Convenience accessor for Ereuna design tokens.
 */
object RadarTheme {
    val colors: RadarColors
        @Composable get() = LocalRadarColors.current

    val typography: RadarTypography
        @Composable get() = LocalRadarTypography.current

    val shapes: RadarShape
        get() = RadarShape

    val spacing: RadarSpacing
        get() = RadarSpacing
}

