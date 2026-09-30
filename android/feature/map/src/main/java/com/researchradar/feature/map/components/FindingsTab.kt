package com.researchradar.feature.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.ConfidenceTag
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.EvidenceQuote
import com.researchradar.core.design.components.EvidenceSheetData
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Finding
import com.researchradar.core.model.Paper

@Composable
fun FindingsTab(
    findings: List<Finding>,
    papers: List<Paper>,
    onCitationClick: (EvidenceSheetData) -> Unit,
    onNavigateToPaper: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = RadarTheme.typography
    val colors = RadarTheme.colors

    val allFindings = remember(findings, papers) {
        findings.ifEmpty { papers.flatMap { it.extraction?.findings.orEmpty() } }
    }

    if (allFindings.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No findings",
                body = "No result statements could be extracted and verified for these papers.",
                icon = RadarIcons.Quote,
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
                "Each finding is a result stated in its paper. Tap one to read the exact passage.",
                style = type.bodySmall,
                color = colors.ink2,
                modifier = Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
            )
        }
        itemsIndexed(allFindings, key = { i, f -> f.id.ifBlank { "f$i" } }) { idx, finding ->
            val paperId = finding.evidence.paperId.ifBlank { finding.paperIds.firstOrNull().orEmpty() }
            val paper = papers.find { it.id == paperId }
            FindingCard(
                index = idx + 1,
                finding = finding,
                paper = paper,
                onOpenPaper = { if (paper != null) onNavigateToPaper(paper.id) },
                modifier = Modifier.staggeredEnter(idx),
            )
        }
    }
}

@Composable
private fun FindingCard(
    index: Int,
    finding: Finding,
    paper: Paper?,
    onOpenPaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, RadarMotion.snappy(), label = "findingChevron")

    RadarCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RadarSpacing.gutter, vertical = 5.dp),
        onClick = { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(index.toString().padStart(2, '0'), style = type.mono, color = colors.accent)
            Spacer(Modifier.width(RadarSpacing.sm))
            if (paper != null) {
                Text(
                    text = listOfNotNull(paper.shortLabel.takeIf { it.isNotBlank() }, paper.venue.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = type.bodySmall,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            ConfidenceTag(level = finding.confidence)
        }
        Spacer(Modifier.height(RadarSpacing.sm))
        Text(finding.text, style = type.body.copy(fontWeight = type.title.fontWeight), color = colors.ink)
        if (finding.theme.isNotBlank()) {
            Spacer(Modifier.height(RadarSpacing.sm))
            Text(
                finding.theme,
                style = type.bodySmall,
                color = colors.ink2,
                modifier = Modifier.clip(RadarShape.pill).background(colors.raised).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(RadarSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(RadarIcons.Quote, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "Hide source passage" else "Show source passage", style = type.bodySmall, color = colors.ink2, modifier = Modifier.weight(1f))
            Icon(RadarIcons.ChevronDown, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(18.dp).rotate(chevron))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(RadarMotion.snappy()) + fadeIn(),
            exit = shrinkVertically(RadarMotion.snappy()) + fadeOut(),
        ) {
            Column(Modifier.padding(top = RadarSpacing.md)) {
                EvidenceQuote(
                    quote = finding.evidence.quote,
                    section = finding.evidence.section,
                    page = finding.evidence.page,
                    shortLabel = paper?.shortLabel.orEmpty(),
                )
                if (paper != null) {
                    Spacer(Modifier.height(RadarSpacing.md))
                    RadarButton(
                        text = "Open paper",
                        trailingIcon = RadarIcons.ArrowRight,
                        onClick = onOpenPaper,
                        style = RadarButtonStyle.Secondary,
                        height = 42.dp,
                    )
                }
            }
        }
    }
}
