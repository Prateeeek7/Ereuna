package com.researchradar.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.pressScale

/**
 * Citation pill: index and short label ("1  Kim '23"). Tapping opens the
 * quoted evidence. 48dp touch target; TalkBack reads "Source 1: Kim '23".
 */
@Composable
fun CitationMarker(
    index: Int,
    shortLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .pressScale(interaction, 0.92f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Source $index: $shortLabel" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(end = 6.dp)
                .clip(RadarShape.pill)
                .background(colors.accentWash)
                .padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RadarShape.pill)
                    .background(colors.accent)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(index.toString(), style = type.mono, color = colors.onAccent)
            }
            if (shortLabel.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(shortLabel, style = type.mono, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
