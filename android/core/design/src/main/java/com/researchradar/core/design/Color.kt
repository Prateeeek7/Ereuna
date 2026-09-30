package com.researchradar.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Ereuna color tokens — "editorial instrument" palette.
 *
 * Warm paper and ink with a single vermilion signal colour. Depth comes from
 * tonal layers (paper → surface → raised) and one dark "instrument" surface
 * used for the radar and hero moments, never from gradients or glows.
 * Status is never colour-only: always a word or symbol too.
 */
@Immutable
data class RadarColors(
    /** App background — warm paper tone */
    val paper: Color,
    /** Cards, sheets, table rows */
    val surface: Color,
    /** Hairlines, dividers, table borders */
    val rule: Color,
    /** Primary text */
    val ink: Color,
    /** Secondary text, metadata */
    val ink2: Color,
    /** Signal colour: primary action, selection, gaps */
    val accent: Color,
    /** Tinted background behind selected / highlighted content */
    val accentWash: Color,
    /** Agreement markers */
    val positive: Color,
    /** Divergence markers */
    val conflict: Color,
    /** Slightly lifted layer inside cards (inputs, nested blocks) */
    val raised: Color,
    /** Dark instrument surface (radar, hero, primary buttons) */
    val night: Color,
    /** Text and strokes on [night] */
    val onNight: Color,
    /** Muted strokes on [night] (rings, grid) */
    val nightRule: Color,
    /** Text on [accent] */
    val onAccent: Color,
) {
    /** Alias for paper */
    val background: Color get() = paper
    /** Alias for rule */
    val hairline: Color get() = rule
    /** Alias for surface */
    val surfaceSecondary: Color get() = raised
    /** Subdued metadata text */
    val ink3: Color get() = ink2.copy(alpha = 0.62f)
}

val LightRadarColors = RadarColors(
    paper = Color(0xFFF3F0E8),
    surface = Color(0xFFFFFFFF),
    rule = Color(0xFFE4DED2),
    ink = Color(0xFF16150F),
    ink2 = Color(0xFF5F5A50),
    accent = Color(0xFFC24A1C),
    accentWash = Color(0xFFF8E7DE),
    positive = Color(0xFF2D6A4F),
    conflict = Color(0xFF8C5A00),
    raised = Color(0xFFF8F6F1),
    night = Color(0xFF191814),
    onNight = Color(0xFFF1ECE2),
    nightRule = Color(0xFF3A372F),
    onAccent = Color(0xFFFFFFFF),
)

val DarkRadarColors = RadarColors(
    paper = Color(0xFF12110E),
    surface = Color(0xFF1C1B17),
    rule = Color(0xFF2F2D27),
    ink = Color(0xFFEFEAE0),
    ink2 = Color(0xFFA7A195),
    accent = Color(0xFFE8764A),
    accentWash = Color(0xFF3A2317),
    positive = Color(0xFF86C7A4),
    conflict = Color(0xFFE2B65E),
    raised = Color(0xFF24221D),
    night = Color(0xFF0B0B09),
    onNight = Color(0xFFEFEAE0),
    nightRule = Color(0xFF2C2A24),
    onAccent = Color(0xFF14120E),
)

val LocalRadarColors = staticCompositionLocalOf { LightRadarColors }
