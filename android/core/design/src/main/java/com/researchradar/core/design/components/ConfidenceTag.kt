package com.researchradar.core.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarTheme
import kotlinx.coroutines.delay

/**
 * Confidence: word plus a 3-segment meter whose segments fill in sequence
 * when it first appears. Never colour-only.
 */
@Composable
fun ConfidenceTag(
    level: String = "HIGH",
    modifier: Modifier = Modifier,
    confidence: String = level,
) {
    val effectiveLevel = (if (confidence != "HIGH" && level == "HIGH") confidence else level).uppercase()
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    val filled = when (effectiveLevel) {
        "HIGH" -> 3
        "MED" -> 2
        "LOW" -> 1
        else -> 0
    }
    val tint = when (effectiveLevel) {
        "HIGH" -> colors.positive
        "MED" -> colors.conflict
        "LOW" -> colors.accent
        else -> colors.ink2
    }
    val word = when (effectiveLevel) {
        "HIGH" -> "High"
        "MED" -> "Medium"
        "LOW" -> "Low"
        else -> effectiveLevel.lowercase().replaceFirstChar { it.uppercase() }
    }

    Row(
        modifier = modifier
            .clip(RadarShape.pill)
            .border(1.dp, colors.rule, RadarShape.pill)
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .semantics { contentDescription = "Confidence $word" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { i ->
                val fill = remember { Animatable(0f) }
                LaunchedEffect(filled) {
                    delay(120L + i * 90L)
                    fill.animateTo(if (i < filled) 1f else 0f, tween(260))
                }
                Box(
                    Modifier
                        .width(7.dp)
                        .height(9.dp)
                        .clip(RadarShape.chip)
                        .background(colors.rule),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fill.value).background(tint))
                }
            }
        }
        Text(word, style = type.bodySmall, color = colors.ink2)
    }
}
