package com.researchradar.core.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme

/**
 * Paper card: year and venue above a serif title, then citations, text
 * availability, and the ranking reason. Papers the user uploaded carry a
 * "Your PDF" tag instead of a ranking reason. Springs on press.
 */
@Composable
fun PaperRow(
    title: String,
    year: Int,
    venue: String,
    citationCount: Int,
    relevanceReason: String,
    hasFullText: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    isUploaded: Boolean = false,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val borderColor by animateColorAsState(if (selected) colors.accent else colors.rule, label = "paperBorder")

    RadarCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RadarSpacing.gutter, vertical = 5.dp),
        onClick = onClick,
        onLongClick = onLongClick,
        border = borderColor,
        borderWidth = if (selected) 2.dp else 1.dp,
        color = if (selected) colors.accentWash.copy(alpha = 0.35f).compositeOver(colors.surface) else colors.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isUploaded) {
                Row(
                    modifier = Modifier
                        .clip(RadarShape.pill)
                        .background(colors.accentWash)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RadarIcons.Upload, contentDescription = null, tint = colors.accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("YOUR PDF", style = type.label, color = colors.accent)
                }
                Spacer(Modifier.width(RadarSpacing.sm))
            }
            if (year > 0) {
                Text(
                    text = year.toString(),
                    style = type.mono,
                    color = colors.ink,
                    modifier = Modifier
                        .clip(RadarShape.pill)
                        .background(colors.raised)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(RadarSpacing.sm))
            }
            Text(
                text = venue,
                style = type.bodySmall,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(targetState = selected, label = "paperSelect") { isSelected ->
                if (isSelected) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(RadarIcons.Check, contentDescription = "Selected for comparison", tint = colors.onAccent, modifier = Modifier.size(14.dp)) }
                } else {
                    Icon(RadarIcons.ArrowUpRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(RadarSpacing.sm))

        Text(
            text = title,
            style = type.paperTitle,
            color = colors.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(RadarSpacing.md))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RadarSpacing.md)) {
            if (!isUploaded || citationCount > 0) {
                Text("$citationCount citations", style = type.mono, color = colors.ink2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (hasFullText) colors.positive else colors.ink3),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (hasFullText) "Full text" else "Abstract only",
                    style = type.bodySmall,
                    color = if (hasFullText) colors.positive else colors.ink2,
                )
            }
        }

        if (relevanceReason.isNotBlank() && !isUploaded) {
            Spacer(Modifier.height(RadarSpacing.sm))
            Text(
                text = relevanceReason,
                style = type.bodySmall,
                color = colors.ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Cards are separated by their own spacing; kept for existing call sites. */
@Composable
fun PaperRowDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.height(0.dp))
}
