package com.researchradar.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Font families — bundled Google Fonts
// ---------------------------------------------------------------------------

val NewsreaderFamily = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.newsreader_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

val IbmPlexSansFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

val IbmPlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

// ---------------------------------------------------------------------------
// Type scale
// ---------------------------------------------------------------------------

/**
 * Ereuna typography — scientific journal / field notebook feel.
 *
 * Newsreader (serif) for display, headings, and paper titles.
 * IBM Plex Sans for UI/body text.
 * IBM Plex Mono for numbers, units, DOIs, years, citation markers.
 * All numbers use tabular figures.
 */
@Immutable
data class RadarTypography(
    /** 36/40 sp, Newsreader SemiBold — hero headlines */
    val hero: TextStyle,
    /** 28/34 sp, Newsreader SemiBold — topic titles */
    val display: TextStyle,
    /** 21/27 sp, Newsreader Medium — section headings */
    val heading: TextStyle,
    /** 17/23 sp, Newsreader Medium — paper titles in lists */
    val paperTitle: TextStyle,
    /** 16/22 sp, IBM Plex Sans SemiBold — UI titles, buttons */
    val title: TextStyle,
    /** 15/22 sp, IBM Plex Sans Regular — body text */
    val body: TextStyle,
    /** 13/19 sp, IBM Plex Sans Regular — secondary body text */
    val bodySmall: TextStyle,
    /** 11/16 sp, IBM Plex Sans SemiBold, uppercase tracking */
    val label: TextStyle,
    /** 13/18 sp, IBM Plex Mono — numbers, DOIs, years */
    val mono: TextStyle,
    /** 26/30 sp, IBM Plex Mono Medium — animated stat figures */
    val numeric: TextStyle,
    /** Uppercase section headers */
    val sectionLabel: TextStyle = label,
    /** Button labels */
    val button: TextStyle = title,
    /** Tab indicators */
    val tabLabel: TextStyle = title,
    /** Caption / small metadata */
    val caption: TextStyle = label,
    /** Section header alias */
    val sectionHeader: TextStyle = label,
    /** Subheading alias */
    val subheading: TextStyle = paperTitle,
    /** Editorial quote in Newsreader serif */
    val quote: TextStyle = paperTitle,
)

private val tabular = "tnum"

// Never split words: without a hyphen glyph drawn, "compression" became "comp / ression".
// Serif titles use balanced breaks so two-line titles don't leave a lone word.
private val headingBreak = LineBreak.Heading
private val paragraphBreak = LineBreak.Paragraph

val RadarType = RadarTypography(
    hero = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
        lineBreak = headingBreak,
        hyphens = Hyphens.None,
    ),
    display = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
        lineBreak = headingBreak,
        hyphens = Hyphens.None,
    ),
    heading = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        lineBreak = headingBreak,
        hyphens = Hyphens.None,
    ),
    paperTitle = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        lineBreak = headingBreak,
        hyphens = Hyphens.None,
    ),
    title = TextStyle(
        fontFamily = IbmPlexSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        lineBreak = paragraphBreak,
        hyphens = Hyphens.None,
    ),
    body = TextStyle(
        fontFamily = IbmPlexSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        lineBreak = paragraphBreak,
        hyphens = Hyphens.None,
    ),
    bodySmall = TextStyle(
        fontFamily = IbmPlexSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        lineBreak = paragraphBreak,
        hyphens = Hyphens.None,
    ),
    label = TextStyle(
        fontFamily = IbmPlexSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.0.sp,
    ),
    mono = TextStyle(
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = tabular,
    ),
    numeric = TextStyle(
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = tabular,
    ),
)

val LocalRadarTypography = staticCompositionLocalOf { RadarType }
