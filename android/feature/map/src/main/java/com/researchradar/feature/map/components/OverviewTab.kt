package com.researchradar.feature.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.CitationMarker
import com.researchradar.core.design.components.EvidenceSheetData
import com.researchradar.core.design.components.GapItem
import com.researchradar.core.design.components.PaperRow
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.components.SectionHeader
import com.researchradar.core.design.components.StatTile
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
    map: ResearchMap,
    onNavigateToPaper: (String) -> Unit,
    onCitationClick: (EvidenceSheetData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = RadarSpacing.xxxl),
    ) {
        // Topic hero
        item {
            Column(Modifier.padding(horizontal = RadarSpacing.gutter).padding(top = RadarSpacing.sm, bottom = RadarSpacing.lg)) {
                Text("RESEARCH MAP", style = type.label, color = colors.accent, modifier = Modifier.staggeredEnter(0, 10f))
                Spacer(Modifier.height(RadarSpacing.xs))
                Text(map.topic, style = type.display, color = colors.ink, modifier = Modifier.staggeredEnter(1))
            }
        }

        // Stats card: counts animate up, coverage bar fills
        item {
            val stats = map.stats
            val total = stats.totalPapers.takeIf { it > 0 } ?: map.papers.size
            val fullText = stats.fullTextPapers
            RadarCard(
                modifier = Modifier
                    .padding(horizontal = RadarSpacing.gutter)
                    .fillMaxWidth()
                    .staggeredEnter(2),
                shape = RadarShape.hero,
                color = colors.night,
                border = null,
                contentPadding = PaddingValues(RadarSpacing.lg),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Papers", total, Modifier.weight(1f), valueColor = colors.onNight, labelColor = colors.onNight.copy(alpha = 0.55f))
                    StatTile("Findings", map.findings.size, Modifier.weight(1f), valueColor = colors.onNight, labelColor = colors.onNight.copy(alpha = 0.55f))
                    StatTile("Gaps", map.gaps.size, Modifier.weight(1f), valueColor = colors.onNight, labelColor = colors.onNight.copy(alpha = 0.55f))
                }
                Spacer(Modifier.height(RadarSpacing.lg))
                CoverageBar(fullText = fullText, total = total)
                Spacer(Modifier.height(RadarSpacing.sm))
                val years = if (stats.yearRange.size >= 2) "${stats.yearRange.first()}–${stats.yearRange.last()}" else null
                Text(
                    text = listOfNotNull(
                        "$fullText of $total read in full text",
                        years?.let { "published $it" },
                    ).joinToString(" · "),
                    style = type.bodySmall,
                    color = colors.onNight.copy(alpha = 0.7f),
                )
            }
        }

        // Synthesis
        item {
            Column(
                Modifier
                    .padding(horizontal = RadarSpacing.gutter)
                    .padding(top = RadarSpacing.xl)
                    .staggeredEnter(3),
            ) {
                SectionHeader("Synthesis")
                Spacer(Modifier.height(RadarSpacing.md))
                RadarCard(modifier = Modifier.fillMaxWidth()) {
                    if (map.synthesis.text.isBlank()) {
                        Text("No synthesis was generated for this map.", style = type.body, color = colors.ink2)
                    } else {
                        Text(map.synthesis.text, style = type.body.copy(lineHeight = type.body.lineHeight * 1.08f), color = colors.ink)
                    }
                    val cited = map.synthesis.citationIds.mapNotNull { id -> map.papers.find { it.id == id } }
                    if (cited.isNotEmpty()) {
                        Spacer(Modifier.height(RadarSpacing.md))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                        Spacer(Modifier.height(RadarSpacing.sm))
                        Text("SOURCES", style = type.label, color = colors.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            cited.forEachIndexed { idx, paper ->
                                CitationMarker(
                                    index = idx + 1,
                                    shortLabel = paper.shortLabel.ifBlank { paper.authors.firstOrNull()?.name ?: paper.title.take(16) },
                                    onClick = { onCitationClick(evidenceFor(paper, idx + 1)) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Gaps
        if (map.gaps.isNotEmpty()) {
            item {
                SectionHeader(
                    "Research gaps",
                    count = map.gaps.size,
                    modifier = Modifier
                        .padding(horizontal = RadarSpacing.gutter)
                        .padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)
                        .staggeredEnter(4),
                )
            }
            items(map.gaps.take(3).size) { index ->
                val gap = map.gaps[index]
                GapItem(
                    number = index + 1,
                    statement = gap.statement,
                    pattern = gap.pattern,
                    paperLabels = gap.supportingPaperIds.mapNotNull { id -> map.papers.find { it.id == id }?.shortLabel },
                    whyItMatters = gap.whyItMatters,
                    confidence = gap.confidence,
                    modifier = Modifier.staggeredEnter(5 + index),
                )
            }
        }

        // Notable papers
        if (map.papers.isNotEmpty()) {
            item {
                SectionHeader(
                    "Notable papers",
                    modifier = Modifier
                        .padding(horizontal = RadarSpacing.gutter)
                        .padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm),
                )
            }
            val mostCited = map.papers.maxByOrNull { it.citationCount }
            val newest = map.papers.maxByOrNull { it.year }?.takeIf { it.id != mostCited?.id }
            val topRanked = map.papers.firstOrNull()?.takeIf { it.id != mostCited?.id && it.id != newest?.id }
            listOfNotNull(
                topRanked?.let { "Best match" to it },
                mostCited?.let { "Most cited" to it },
                newest?.let { "Newest" to it },
            ).forEachIndexed { i, (badge, paper) ->
                item {
                    Column(Modifier.staggeredEnter(8 + i)) {
                        Row(
                            Modifier.padding(horizontal = RadarSpacing.gutter).padding(top = RadarSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).clip(RadarShape.pill).background(colors.accent))
                            Spacer(Modifier.width(6.dp))
                            Text(badge.uppercase(), style = type.label, color = colors.accent)
                        }
                        PaperRow(
                            title = paper.title,
                            year = paper.year,
                            venue = paper.venue,
                            citationCount = paper.citationCount,
                            relevanceReason = "",
                            hasFullText = paper.hasFullText,
                            isUploaded = paper.isUploaded,
                            onClick = { onNavigateToPaper(paper.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Full-text share of the corpus as a bar that fills on first appearance. */
@Composable
private fun CoverageBar(fullText: Int, total: Int) {
    val colors = RadarTheme.colors
    val target = if (total > 0) fullText / total.toFloat() else 0f
    val fill = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(250)
        fill.animateTo(target, tween(900))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RadarShape.pill)
            .background(colors.nightRule),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill.value)
                .fillMaxHeight()
                .clip(RadarShape.pill)
                .background(colors.accent),
        )
    }
}

private fun evidenceFor(paper: Paper, index: Int): EvidenceSheetData {
    val evidence = paper.extraction?.findings?.firstOrNull()?.evidence
        ?: paper.extraction?.metrics?.firstOrNull()?.evidence
    val quote = evidence?.quote
        ?: paper.abstract.takeIf { it.isNotBlank() }?.let { if (it.length > 300) it.take(300) + "…" else it }
        ?: paper.title
    return EvidenceSheetData(
        citationIndex = index,
        quote = quote,
        section = evidence?.section ?: if (paper.abstract.isNotBlank()) "Abstract" else "",
        page = evidence?.page,
        shortLabel = paper.shortLabel,
        paperTitle = paper.title,
        paperId = paper.id,
        venue = paper.venue,
        year = paper.year,
        doi = paper.doi,
    )
}
