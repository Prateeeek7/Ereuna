package com.researchradar.feature.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.PaperRow
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarChip
import com.researchradar.core.design.components.RadarIconButton
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Paper
import com.researchradar.feature.map.PaperSort

@Composable
fun PapersTab(
    papers: List<Paper>,
    activeFilter: String,
    activeSort: PaperSort,
    activeToolFilter: String?,
    selectedPaperIdsForCompare: Set<String>,
    onFilterChange: (String) -> Unit,
    onSortChange: (PaperSort) -> Unit,
    onClearToolFilter: () -> Unit,
    onToggleSelectPaperForCompare: (String) -> Unit,
    onNavigateToPaper: (String) -> Unit,
    onNavigateToCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    val filteredPapers = remember(papers, activeFilter, activeSort, activeToolFilter) {
        var list = papers
        if (!activeToolFilter.isNullOrBlank()) {
            list = list.filter { paper ->
                paper.extraction?.tools?.any { it.name.equals(activeToolFilter, ignoreCase = true) } == true ||
                    paper.extraction?.datasets?.any { it.name.equals(activeToolFilter, ignoreCase = true) } == true
            }
        }
        if (activeFilter == "FULL_TEXT") list = list.filter { it.hasFullText }
        when (activeSort) {
            PaperSort.RELEVANCE -> list.sortedByDescending { it.relevanceScore }
            PaperSort.CITATIONS -> list.sortedByDescending { it.citationCount }
            PaperSort.RECENCY -> list.sortedByDescending { it.year }
        }
    }
    val selecting = selectedPaperIdsForCompare.isNotEmpty()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selecting) 120.dp else RadarSpacing.xxxl),
        ) {
            // Filters and sort
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = RadarSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = RadarSpacing.sm),
                ) {
                    item { RadarChip("All", activeFilter == "ALL", { onFilterChange("ALL") }, count = papers.size) }
                    item { RadarChip("Full text", activeFilter == "FULL_TEXT", { onFilterChange("FULL_TEXT") }, count = papers.count { it.hasFullText }) }
                    item { Box(Modifier.width(1.dp).height(24.dp).background(colors.rule)) }
                    item { RadarChip("Best match", activeSort == PaperSort.RELEVANCE, { onSortChange(PaperSort.RELEVANCE) }) }
                    item { RadarChip("Most cited", activeSort == PaperSort.CITATIONS, { onSortChange(PaperSort.CITATIONS) }) }
                    item { RadarChip("Newest", activeSort == PaperSort.RECENCY, { onSortChange(PaperSort.RECENCY) }) }
                }
            }

            if (!activeToolFilter.isNullOrBlank()) {
                item {
                    Row(
                        Modifier
                            .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.xs)
                            .fillMaxWidth()
                            .clip(RadarShape.control)
                            .background(colors.accentWash)
                            .padding(start = RadarSpacing.md, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Papers using $activeToolFilter", style = type.bodySmall, color = colors.accent, modifier = Modifier.weight(1f))
                        RadarIconButton(RadarIcons.Close, "Clear tool filter", onClearToolFilter, size = 34.dp, border = null, background = colors.accentWash, tint = colors.accent)
                    }
                }
            }

            if (!selecting) {
                item {
                    Text(
                        "Press and hold papers to compare them side by side.",
                        style = type.bodySmall,
                        color = colors.ink3,
                        modifier = Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.xs),
                    )
                }
            }

            if (filteredPapers.isEmpty()) {
                item {
                    EmptyState(
                        title = "No papers match",
                        body = "Try showing all papers instead of full text only.",
                        icon = RadarIcons.Document,
                    )
                }
            }

            items(filteredPapers.size, key = { filteredPapers[it].id }) { i ->
                val paper = filteredPapers[i]
                val isSelected = paper.id in selectedPaperIdsForCompare
                PaperRow(
                    title = paper.title,
                    year = paper.year,
                    venue = paper.venue,
                    citationCount = paper.citationCount,
                    relevanceReason = paper.relevanceReason,
                    hasFullText = paper.hasFullText,
                    isUploaded = paper.isUploaded,
                    selected = isSelected,
                    onClick = {
                        if (selecting) onToggleSelectPaperForCompare(paper.id) else onNavigateToPaper(paper.id)
                    },
                    onLongClick = { onToggleSelectPaperForCompare(paper.id) },
                    modifier = Modifier.animateItem().staggeredEnter(i),
                )
            }
        }

        // Compare bar
        AnimatedVisibility(
            visible = selecting,
            enter = slideInVertically(RadarMotion.snappy()) { it } + fadeIn(),
            exit = slideOutVertically(RadarMotion.snappy()) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .padding(RadarSpacing.gutter)
                    .fillMaxWidth()
                    .clip(RadarShape.pill)
                    .background(colors.night)
                    .padding(start = RadarSpacing.lg, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${selectedPaperIdsForCompare.size} selected",
                    style = type.title,
                    color = colors.onNight,
                    modifier = Modifier.weight(1f),
                )
                RadarIconButton(
                    RadarIcons.Close,
                    "Clear selection",
                    onClick = { selectedPaperIdsForCompare.toList().forEach(onToggleSelectPaperForCompare) },
                    background = colors.night,
                    border = colors.nightRule,
                    tint = colors.onNight,
                    size = 42.dp,
                )
                Spacer(Modifier.width(RadarSpacing.sm))
                RadarButton(
                    text = "Compare",
                    trailingIcon = RadarIcons.ArrowRight,
                    onClick = onNavigateToCompare,
                    style = RadarButtonStyle.Accent,
                    enabled = selectedPaperIdsForCompare.size >= 2,
                    height = 44.dp,
                )
            }
        }
    }
}
