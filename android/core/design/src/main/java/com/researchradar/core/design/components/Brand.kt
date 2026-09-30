package com.researchradar.core.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.researchradar.core.design.RadarTheme

/** Product name as written in sentences, and as the wordmark. */
const val APP_NAME = "Ereuna"
const val APP_WORDMARK = "EREUNA"
const val APP_TAGLINE = "Research, on your radar."

/** "EREUNA" in spaced serif capitals, optionally with the tagline beneath. */
@Composable
fun Wordmark(
    color: Color,
    modifier: Modifier = Modifier,
    showTagline: Boolean = false,
    taglineColor: Color = color.copy(alpha = 0.65f),
) {
    val type = RadarTheme.typography
    Column(modifier) {
        Text(APP_WORDMARK, style = type.heading.copy(letterSpacing = 3.sp), color = color)
        if (showTagline) {
            Text(APP_TAGLINE, style = type.bodySmall.copy(fontFamily = type.heading.fontFamily, fontStyle = FontStyle.Italic), color = taglineColor)
        }
    }
}
