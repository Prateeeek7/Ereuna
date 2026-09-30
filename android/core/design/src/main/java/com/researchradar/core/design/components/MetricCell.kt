package com.researchradar.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.ResearchRadarTheme

/**
 * Metric cell — mono value in ink + unit in ink2, condition line below.
 *
 * Used in comparison tables and paper detail metric lists.
 */
@Composable
fun MetricCell(
    value: String,
    unit: String,
    condition: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row {
            // Value — mono ink, underlined if best
            Text(
                text = value,
                style = type.mono,
                color = if (isHighlighted) colors.accent else colors.ink,
                modifier = Modifier.alignByBaseline(),
            )

            Text(
                text = " $unit",
                style = type.mono,
                color = colors.ink2,
                modifier = Modifier.alignByBaseline(),
            )
        }

        if (condition.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = condition,
                style = type.mono.copy(fontSize = type.label.fontSize),
                color = colors.ink2,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "MetricCell — Light", showBackground = true)
@Composable
private fun MetricCellLightPreview() {
    ResearchRadarTheme(darkTheme = false) {
        Row(
            modifier = Modifier
                .background(RadarTheme.colors.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricCell(value = "8.7", unit = "pW/cell", condition = "@ 0.7 V, 25 °C")
            MetricCell(
                value = "12.3",
                unit = "pW/cell",
                condition = "@ 0.6 V, 25 °C",
                isHighlighted = true,
            )
            MetricCell(value = "0.87", unit = "V", condition = "TT corner")
        }
    }
}

@Preview(name = "MetricCell — Dark", showBackground = true)
@Composable
private fun MetricCellDarkPreview() {
    ResearchRadarTheme(darkTheme = true) {
        Row(
            modifier = Modifier
                .background(RadarTheme.colors.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricCell(value = "8.7", unit = "pW/cell", condition = "@ 0.7 V, 25 °C")
            MetricCell(
                value = "12.3",
                unit = "pW/cell",
                condition = "@ 0.6 V, 25 °C",
                isHighlighted = true,
            )
        }
    }
}
