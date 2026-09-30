package com.researchradar.feature.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.ConfidenceTag
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.EvidenceSheetData
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Contradiction
import com.researchradar.core.model.Paper
import com.researchradar.core.model.reportedRanges
import com.researchradar.core.model.ReportedRange
import com.researchradar.core.model.formatReported
import kotlinx.coroutines.delay
import kotlin.math.ln

@Composable
fun ConflictsTab(
    contradictions: List<Contradiction>,
    papers: List<Paper>,
    onNavigateToPaper: (String) -> Unit,
    onCitationClick: (EvidenceSheetData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranges = remember(papers) { reportedRanges(papers) }
    if (contradictions.isEmpty() && ranges.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No disagreements found",
                body = "No two papers report clearly different results for the same thing under comparable conditions, " +
                    "and no quantity is reported by two or more papers to compare.",
                icon = RadarIcons.Graph,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = RadarSpacing.xxxl),
    ) {
        item { SectionTitle("Disagreements") }
        if (contradictions.isEmpty()) {
            item {
                Text(
                    "None found. No two papers report clearly different results for the same thing under comparable " +
                        "conditions; values that differ because the materials or setups differ are not counted.",
                    style = RadarTheme.typography.bodySmall,
                    color = RadarTheme.colors.ink2,
                    modifier = Modifier.padding(horizontal = RadarSpacing.gutter).padding(bottom = RadarSpacing.sm),
                )
            }
        } else {
            item {
                Text(
                    "Different values for the same quantity on a comparable system, or opposing claims. Tap a source to read each paper's own words.",
                    style = RadarTheme.typography.bodySmall,
                    color = RadarTheme.colors.ink2,
                    modifier = Modifier.padding(horizontal = RadarSpacing.gutter).padding(bottom = RadarSpacing.sm),
                )
            }
            itemsIndexed(contradictions, key = { _, c -> c.id }) { index, c ->
                ContradictionCard(
                    contradiction = c,
                    papers = papers,
                    onNavigateToPaper = onNavigateToPaper,
                    onCitationClick = onCitationClick,
                    modifier = Modifier.staggeredEnter(index),
                )
            }
        }

        if (ranges.isNotEmpty()) {
            item { SectionTitle("How reported values compare") }
            item {
                Text(
                    "Every value the papers report for the same quantity, lowest to highest, with what each was measured on. " +
                        "Tap a value to see its source.",
                    style = RadarTheme.typography.bodySmall,
                    color = RadarTheme.colors.ink2,
                    modifier = Modifier.padding(horizontal = RadarSpacing.gutter).padding(bottom = RadarSpacing.sm),
                )
            }
            itemsIndexed(ranges, key = { _, r -> "range:${r.title}|${r.unit}" }) { index, range ->
                RangeCard(range, onCitationClick, Modifier.staggeredEnter(contradictions.size + index))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = RadarTheme.typography.heading,
        color = RadarTheme.colors.ink,
        modifier = Modifier.padding(horizontal = RadarSpacing.gutter).padding(top = RadarSpacing.lg, bottom = 6.dp),
    )
}

private const val MAX_RANGE_ROWS = 8

@Composable
private fun RangeCard(range: ReportedRange, onCitationClick: (EvidenceSheetData) -> Unit, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    fun position(v: Double): Float {
        if (range.max <= range.min) return 0.5f
        val f = if (range.logScale) {
            (ln(v) - ln(range.min)) / (ln(range.max) - ln(range.min))
        } else {
            (v - range.min) / (range.max - range.min)
        }
        return f.toFloat().coerceIn(0f, 1f)
    }

    RadarCard(modifier.fillMaxWidth().padding(horizontal = RadarSpacing.gutter, vertical = 5.dp)) {
        Text(range.title, style = type.title, color = colors.ink)
        Spacer(Modifier.height(2.dp))
        Text(
            "${range.paperCount} papers · ${formatReported(range.min)} – ${formatReported(range.max)} ${range.unit}".trim() +
                if (range.logScale) " · log scale" else "",
            style = type.bodySmall,
            color = colors.ink2,
        )
        Spacer(Modifier.height(RadarSpacing.sm))
        range.points.take(MAX_RANGE_ROWS).forEachIndexed { i, point ->
            val label = point.paper.shortLabel.ifBlank { point.paper.title.take(18) }
            val target = position(point.value)
            val slide = remember { Animatable(0f) }
            LaunchedEffect(target) {
                delay(120L + i * 70L)
                slide.animateTo(target, tween(650))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RadarShape.control)
                    .clickable {
                        onCitationClick(
                            EvidenceSheetData(
                                citationIndex = 0,
                                quote = point.evidence.quote,
                                section = point.evidence.section,
                                page = point.evidence.page,
                                shortLabel = label,
                                paperTitle = point.paper.title,
                                paperId = point.paper.id,
                                venue = point.paper.venue,
                                year = point.paper.year.takeIf { it > 0 },
                                doi = point.paper.doi,
                            ),
                        )
                    }
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = type.bodySmall, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatReported(point.value), style = type.mono, color = colors.ink)
                }
                point.context?.let {
                    Text(it, style = type.bodySmall.copy(fontSize = type.label.fontSize), color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(5.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp).clip(RadarShape.pill).background(colors.rule))
                    Box(
                        Modifier
                            .offset(x = (maxWidth - 10.dp) * slide.value)
                            .size(10.dp)
                            .clip(RadarShape.pill)
                            .background(colors.accent),
                    )
                }
            }
        }
        val hidden = range.points.size - MAX_RANGE_ROWS
        if (hidden > 0) {
            Text("+$hidden more values", style = type.bodySmall, color = colors.ink3, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ContradictionCard(
    contradiction: Contradiction,
    papers: List<Paper>,
    onNavigateToPaper: (String) -> Unit,
    onCitationClick: (EvidenceSheetData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val values = contradiction.entries.mapNotNull { it.value }
    val maxValue = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    RadarCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RadarSpacing.gutter, vertical = 5.dp),
    ) {
        Text(
            if (contradiction.kind == "claim") "OPPOSING CLAIMS" else "DIFFERENT VALUES",
            style = type.label,
            color = colors.conflict,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                contradiction.metric.replaceFirstChar { it.uppercase() },
                style = type.heading,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(RadarSpacing.sm))
            ConfidenceTag(level = contradiction.confidence)
        }
        Spacer(Modifier.height(RadarSpacing.md))

        contradiction.entries.forEachIndexed { i, entry ->
            val paper = papers.find { it.id == entry.paperId }
            val label = paper?.shortLabel?.ifBlank { null } ?: entry.paperId
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RadarShape.control)
                    .clickable(enabled = paper != null) { paper?.let { onNavigateToPaper(it.id) } }
                    .padding(vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = type.title.copy(fontSize = type.bodySmall.fontSize),
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    entry.value?.let { v ->
                        Text("${formatReported(v)} ${entry.unit}".trim(), style = type.mono, color = colors.ink)
                    }
                }
                entry.value?.let { v ->
                    val fraction = (v / maxValue).toFloat().coerceIn(0.04f, 1f)
                    val grow = remember { Animatable(0f) }
                    LaunchedEffect(fraction) {
                        delay(150L + i * 110L)
                        grow.animateTo(fraction, tween(700))
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RadarShape.pill).background(colors.raised)) {
                        Box(
                            Modifier
                                .fillMaxWidth(grow.value)
                                .fillMaxHeight()
                                .clip(RadarShape.pill)
                                .background(if (v == maxValue) colors.accent else colors.ink),
                        )
                    }
                }
                if (entry.value == null && entry.statement.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(entry.statement, style = type.body, color = colors.ink)
                }
                val context = (listOf(entry.subject) + entry.conditions.values).filter { it.isNotBlank() }.joinToString(" · ")
                if (context.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(context, style = type.bodySmall, color = colors.ink2)
                }
                if (entry.quote.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier
                            .clip(RadarShape.pill)
                            .clickable {
                                onCitationClick(
                                    EvidenceSheetData(
                                        citationIndex = 0,
                                        quote = entry.quote,
                                        section = entry.section,
                                        page = entry.page,
                                        shortLabel = label,
                                        paperTitle = paper?.title.orEmpty(),
                                        paperId = entry.paperId,
                                        venue = paper?.venue.orEmpty(),
                                        year = paper?.year?.takeIf { it > 0 },
                                        doi = paper?.doi,
                                    ),
                                )
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(RadarIcons.Quote, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Source", style = type.bodySmall, color = colors.ink3)
                    }
                }
            }
        }

        if (contradiction.likelyReason.isNotBlank()) {
            Spacer(Modifier.height(RadarSpacing.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RadarShape.control)
                    .background(colors.raised)
                    .padding(RadarSpacing.md),
            ) {
                Box(Modifier.width(3.dp).height(18.dp).clip(RadarShape.pill).background(colors.conflict))
                Spacer(Modifier.width(RadarSpacing.sm))
                Text(contradiction.likelyReason, style = type.bodySmall, color = colors.ink)
            }
        }
    }
}
