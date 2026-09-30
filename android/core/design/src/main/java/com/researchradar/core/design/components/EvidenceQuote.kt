package com.researchradar.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarTheme

/**
 * Verbatim quote: accent spine, serif italic text on a tinted block, and a
 * source line with only the parts that are known (label · section · page).
 */
@Composable
fun EvidenceQuote(
    quote: String,
    section: String,
    page: Int?,
    shortLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RadarShape.control)
            .background(colors.raised),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.accent),
        )
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Icon(RadarIcons.Quote, contentDescription = null, tint = colors.accent.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = quote,
                style = type.quote.copy(fontStyle = FontStyle.Italic),
                color = colors.ink,
            )
            val sourceLine = listOfNotNull(
                shortLabel.takeIf { it.isNotBlank() },
                section.takeIf { it.isNotBlank() },
                page?.let { "p. $it" },
            ).joinToString("  ·  ")
            if (sourceLine.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 14.dp, height = 1.dp).background(colors.ink3))
                    Spacer(Modifier.width(8.dp))
                    Text(text = sourceLine, style = type.mono, color = colors.ink2)
                }
            }
        }
    }
}
