package com.researchradar.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme

/**
 * Research gap card: numbered badge, statement, the evidence count, supporting
 * papers and confidence. Tap to expand "why it matters" (chevron rotates).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GapItem(
    number: Int,
    statement: String,
    pattern: String,
    paperLabels: List<String>,
    whyItMatters: String,
    confidence: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, RadarMotion.snappy(), label = "gapChevron")

    RadarCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RadarSpacing.gutter, vertical = 5.dp),
        onClick = if (whyItMatters.isNotBlank()) ({ expanded = !expanded }) else null,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .clip(RadarShape.control)
                    .background(colors.accentWash)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text("G$number", style = type.mono, color = colors.accent)
            }
            Spacer(Modifier.width(RadarSpacing.md))
            Text(
                text = statement,
                style = type.body.copy(fontWeight = type.title.fontWeight),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (whyItMatters.isNotBlank()) {
                Icon(
                    RadarIcons.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = colors.ink2,
                    modifier = Modifier.size(20.dp).rotate(chevron),
                )
            }
        }

        Spacer(Modifier.height(RadarSpacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(RadarIcons.Gap, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(pattern, style = type.bodySmall, color = colors.ink2)
        }

        if (paperLabels.isNotEmpty() || confidence.isNotBlank()) {
            Spacer(Modifier.height(RadarSpacing.xs))
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                paperLabels.forEachIndexed { idx, label ->
                    CitationMarker(index = idx + 1, shortLabel = label)
                }
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    ConfidenceTag(level = confidence)
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(RadarMotion.snappy()),
            exit = fadeOut() + shrinkVertically(RadarMotion.snappy()),
        ) {
            Column(Modifier.padding(top = RadarSpacing.sm)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                Spacer(Modifier.height(RadarSpacing.md))
                Text("WHY IT MATTERS", style = type.label, color = colors.ink2)
                Spacer(Modifier.height(4.dp))
                Text(whyItMatters, style = type.body, color = colors.ink)
            }
        }
    }
}
