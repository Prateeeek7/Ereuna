package com.researchradar.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.ResearchRadarTheme

/**
 * Progress log — monospace timestamped lines like a lab log.
 *
 * Format: "00:04  retrieved 187 candidates"
 * Grows from bottom, auto-scrolls to latest entry.
 */
@Composable
fun ProgressLog(
    entries: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .padding(horizontal = RadarSpacing.gutter),
    ) {
        items(entries) { (timestamp, message) ->
            Text(
                text = "$timestamp  $message",
                style = type.mono,
                color = colors.ink,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val sampleLogEntries = listOf(
    "00:00" to "starting query expansion",
    "00:01" to "generated 5 search queries",
    "00:02" to "querying OpenAlex",
    "00:02" to "querying Semantic Scholar",
    "00:02" to "querying arXiv",
    "00:04" to "retrieved 187 candidates",
    "00:05" to "deduplication: 187 → 142 unique",
    "00:06" to "computing embeddings",
    "00:08" to "ranking candidates",
    "00:10" to "selected 25 papers",
    "00:11" to "fetching full text for 19 papers",
    "00:18" to "extracting paper 1/25: Kim '23",
    "00:22" to "extracting paper 2/25: Park '22",
)

@Preview(name = "ProgressLog — Light", showBackground = true)
@Composable
private fun ProgressLogLightPreview() {
    ResearchRadarTheme(darkTheme = false) {
        ProgressLog(
            entries = sampleLogEntries,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Preview(name = "ProgressLog — Dark", showBackground = true)
@Composable
private fun ProgressLogDarkPreview() {
    ResearchRadarTheme(darkTheme = true) {
        ProgressLog(
            entries = sampleLogEntries,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}
