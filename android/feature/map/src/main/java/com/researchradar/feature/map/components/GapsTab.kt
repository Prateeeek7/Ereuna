package com.researchradar.feature.map.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.GapItem
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Gap
import com.researchradar.core.model.Paper

@Composable
fun GapsTab(
    gaps: List<Gap>,
    papers: List<Paper>,
    onNavigateToPaper: (String) -> Unit = {},
    onViewExperimentForGap: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (gaps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No research gaps",
                body = "Gaps are built only from limitations the papers state themselves, and none could be verified in this map's text.",
                icon = RadarIcons.Gap,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = RadarSpacing.xxxl),
    ) {
        item {
            Text(
                "Built from limitations the authors state about their own work. The count shows how many papers in this map report each one.",
                style = RadarTheme.typography.bodySmall,
                color = RadarTheme.colors.ink2,
                modifier = Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
            )
        }
        itemsIndexed(gaps, key = { _, g -> g.id }) { index, gap ->
            GapItem(
                number = index + 1,
                statement = gap.statement,
                pattern = gap.pattern,
                paperLabels = gap.supportingPaperIds.mapNotNull { id -> papers.find { it.id == id }?.shortLabel },
                whyItMatters = gap.whyItMatters,
                confidence = gap.confidence,
                modifier = Modifier.staggeredEnter(index),
            )
        }
    }
}
