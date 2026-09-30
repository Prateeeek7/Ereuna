package com.researchradar.feature.paper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.ConfidenceTag
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.EvidenceBottomSheet
import com.researchradar.core.design.components.EvidenceSheetData
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.components.RadarIconButton
import com.researchradar.core.design.components.SectionHeader
import com.researchradar.core.design.components.SkeletonBlock
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Evidence
import com.researchradar.core.model.Metric
import com.researchradar.core.model.formatReported
import com.researchradar.core.model.Paper

@Composable
fun PaperScreen(
    paperId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaperViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PaperScreenContent(
        uiState = uiState,
        onBack = onBack,
        onToggleBookmark = viewModel::toggleBookmark,
        onToggleAbstractExpanded = viewModel::toggleAbstractExpanded,
        onOpenPdf = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(context, "No browser or PDF viewer available.", Toast.LENGTH_SHORT).show()
            }
        },
        onCopyDoi = { doi ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("DOI", doi))
            Toast.makeText(context, "DOI copied", Toast.LENGTH_SHORT).show()
        },
        onCitationClick = viewModel::showEvidence,
        onDismissEvidence = viewModel::dismissEvidence,
        onRetry = viewModel::loadPaper,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PaperScreenContent(
    uiState: PaperUiState,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleAbstractExpanded: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onCopyDoi: (String) -> Unit,
    onCitationClick: (EvidenceSheetData) -> Unit,
    onDismissEvidence: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val paper = uiState.paper

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadarIconButton(RadarIcons.ArrowLeft, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (paper != null) SaveButton(saved = uiState.isBookmarked, onToggle = onToggleBookmark)
        }

        Box(Modifier.weight(1f)) {
            when {
                uiState.isLoading -> PaperSkeleton()
                paper == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "Couldn't load this paper",
                        body = uiState.errorMessage ?: "The server has no paper with this id.",
                        icon = RadarIcons.Document,
                        actionLabel = "Try again",
                        onAction = onRetry,
                    )
                }
                else -> PaperBody(paper, uiState, onToggleAbstractExpanded, onOpenPdf, onCopyDoi, onCitationClick)
            }
            // Content fades out under the top bar instead of being cut off at its edge.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(colors.background, colors.background.copy(alpha = 0f)))),
            )
        }
    }

    uiState.activeEvidenceSheet?.let { data ->
        EvidenceBottomSheet(data = data, onDismissRequest = onDismissEvidence)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaperBody(
    paper: Paper,
    uiState: PaperUiState,
    onToggleAbstractExpanded: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onCopyDoi: (String) -> Unit,
    onCitationClick: (EvidenceSheetData) -> Unit,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val ext = paper.extraction

    @Suppress("UNUSED_PARAMETER")
    fun open(index: Int, evidence: Evidence) = onCitationClick(
        EvidenceSheetData(
            citationIndex = 0,
            quote = evidence.quote,
            section = evidence.section,
            page = evidence.page,
            shortLabel = paper.shortLabel,
            paperTitle = paper.title,
            paperId = paper.id,
            venue = paper.venue,
            year = paper.year,
            doi = paper.doi,
        ),
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = RadarSpacing.gutter, end = RadarSpacing.gutter, bottom = RadarSpacing.xxxl),
    ) {
        // Header
        item {
            Column(Modifier.padding(top = RadarSpacing.sm)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.staggeredEnter(0, 10f)) {
                    if (paper.isUploaded) {
                        Text(
                            "YOUR PDF",
                            style = type.label,
                            color = colors.accent,
                            modifier = Modifier.clip(RadarShape.pill).background(colors.accentWash).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    if (paper.year > 0) Pill(paper.year.toString(), mono = true)
                    if (paper.venue.isNotBlank()) Pill(paper.venue)
                }
                Spacer(Modifier.height(RadarSpacing.md))
                Text(paper.title, style = type.display, color = colors.ink, modifier = Modifier.staggeredEnter(1))
                if (paper.authors.isNotEmpty()) {
                    Spacer(Modifier.height(RadarSpacing.sm))
                    Text(
                        paper.authors.joinToString(", ") { it.name },
                        style = type.bodySmall,
                        color = colors.ink2,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.staggeredEnter(2),
                    )
                }
                Spacer(Modifier.height(RadarSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(RadarSpacing.lg), modifier = Modifier.staggeredEnter(3)) {
                    if (!paper.isUploaded || paper.citationCount > 0) Stat("${paper.citationCount}", "citations")
                    Stat(if (paper.hasFullText) "Full" else "Abstract", "text read")
                    ext?.extractedBy?.takeIf { it.isNotBlank() }?.let {
                        Stat(if (it.startsWith("llm:")) "Model" else "Rules", "extraction")
                    }
                }
                Spacer(Modifier.height(RadarSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm), modifier = Modifier.staggeredEnter(4)) {
                    paper.oaPdfUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        RadarButton("Open PDF", { onOpenPdf(url) }, style = RadarButtonStyle.Accent, trailingIcon = RadarIcons.ArrowUpRight, height = 46.dp)
                    }
                    paper.doi?.takeIf { it.isNotBlank() }?.let { doi ->
                        RadarButton("Copy DOI", { onCopyDoi(doi) }, style = RadarButtonStyle.Secondary, height = 46.dp)
                    }
                }
                if (!paper.hasFullText) {
                    Row(
                        Modifier
                            .padding(top = RadarSpacing.lg)
                            .fillMaxWidth()
                            .clip(RadarShape.control)
                            .background(colors.raised)
                            .padding(RadarSpacing.md),
                    ) {
                        Icon(RadarIcons.Lock, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(RadarSpacing.sm))
                        Text(
                            "No open full text was available, so only the abstract was analysed.",
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                    }
                }
                ext?.extractedBy?.takeIf { it.isNotBlank() }?.let { by ->
                    Text(
                        if (by.startsWith("llm:")) "Extracted with ${by.removePrefix("llm:")}. Each item's quote was found in the paper text."
                        else "Extracted with text rules (no language model). Each item is a sentence quoted from the paper.",
                        style = type.bodySmall,
                        color = colors.ink3,
                        modifier = Modifier.padding(top = RadarSpacing.md),
                    )
                }
            }
        }

        // Abstract
        if (paper.abstract.isNotBlank()) {
            item { SectionHeader("Abstract", modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)) }
            item {
                RadarCard(Modifier.fillMaxWidth().animateContentSize(RadarMotion.snappy()), onClick = onToggleAbstractExpanded) {
                    Text(
                        paper.abstract,
                        style = type.body,
                        color = colors.ink,
                        maxLines = if (uiState.isAbstractExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (paper.abstract.length > 320) {
                        Spacer(Modifier.height(RadarSpacing.sm))
                        Text(if (uiState.isAbstractExpanded) "Show less" else "Read full abstract", style = type.title.copy(fontSize = type.bodySmall.fontSize), color = colors.accent)
                    }
                }
            }
        }

        // Research question and method
        if (ext?.problem != null || ext?.method != null) {
            item { SectionHeader("What the paper does", modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)) }
            item {
                RadarCard(Modifier.fillMaxWidth()) {
                    ext.problem?.let {
                        LabeledText("Problem", it.text) { open(1, it.evidence) }
                    }
                    if (ext.problem != null && ext.method != null) {
                        Box(Modifier.padding(vertical = RadarSpacing.md).fillMaxWidth().height(1.dp).background(colors.rule))
                    }
                    ext.method?.let {
                        LabeledText("Method", it.text) { open(2, it.evidence) }
                    }
                }
            }
        }

        // Technology
        ext?.technology?.let { tech ->
            val fields = listOfNotNull(
                tech.device?.takeIf { it.isNotBlank() }?.let { "Platform" to it },
                tech.cellType?.takeIf { it.isNotBlank() }?.let { "Configuration" to it },
                tech.nodeNm?.let { "Process node" to "$it nm" },
            )
            if (fields.isNotEmpty()) {
                item { SectionHeader("Technology", modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)) }
                item {
                    RadarCard(Modifier.fillMaxWidth(), onClick = { open(3, tech.evidence) }) {
                        fields.forEach { (label, value) ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text(label, style = type.bodySmall, color = colors.ink2, modifier = Modifier.width(110.dp))
                                Text(value, style = type.title, color = colors.ink)
                            }
                        }
                    }
                }
            }
        }

        // Metrics grid
        val metrics = ext?.metrics.orEmpty()
        if (metrics.isNotEmpty()) {
            item { SectionHeader("Reported results", count = metrics.size, modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)) }
            metrics.chunked(2).forEachIndexed { rowIdx, pair ->
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { i, metric ->
                            MetricTile(
                                metric,
                                onClick = { open(10 + rowIdx * 2 + i, metric.evidence) },
                                modifier = Modifier.weight(1f).staggeredEnter(rowIdx * 2 + i),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Findings
        val findings = ext?.findings.orEmpty()
        if (findings.isNotEmpty()) {
            item { SectionHeader("Findings", count = findings.size, modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)) }
            findings.forEachIndexed { i, finding ->
                item {
                    RadarCard(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).staggeredEnter(i),
                        onClick = { open(20 + i, finding.evidence) },
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text((i + 1).toString().padStart(2, '0'), style = type.mono, color = colors.accent)
                            Spacer(Modifier.width(RadarSpacing.sm))
                            Text(finding.text, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(RadarSpacing.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConfidenceTag(level = finding.confidence)
                            Spacer(Modifier.weight(1f))
                            SourceHint()
                        }
                    }
                }
            }
        }

        // Limitations
        val limitations = ext?.limitations.orEmpty()
        if (limitations.isNotEmpty()) {
            item { SectionHeader("Stated limitations", count = limitations.size, modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)) }
            item {
                RadarCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    limitations.forEachIndexed { i, lim ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = RadarSpacing.lg, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(RadarIcons.Gap, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                            Spacer(Modifier.width(RadarSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(lim.text, style = type.bodySmall, color = colors.ink)
                                Text(
                                    "Show passage",
                                    style = type.bodySmall,
                                    color = colors.accent,
                                    modifier = Modifier.padding(top = 4.dp).clickableNoRipple { open(40 + i, lim.evidence) },
                                )
                            }
                        }
                        if (i < limitations.lastIndex) {
                            Box(Modifier.padding(horizontal = RadarSpacing.lg).fillMaxWidth().height(1.dp).background(colors.rule))
                        }
                    }
                }
            }
        }

        // Tools and datasets
        val tools = ext?.tools.orEmpty().map { it.name to it.evidence } + ext?.datasets.orEmpty().map { it.name to it.evidence }
        if (tools.isNotEmpty()) {
            item { SectionHeader("Tools and datasets", count = tools.size, modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tools.forEachIndexed { i, (name, evidence) ->
                        Text(
                            name,
                            style = type.bodySmall,
                            color = colors.ink,
                            modifier = Modifier
                                .clip(RadarShape.pill)
                                .background(colors.surface)
                                .border(1.dp, colors.rule, RadarShape.pill)
                                .clickableNoRipple { open(60 + i, evidence) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        if (ext == null) {
            item {
                EmptyState(
                    title = "Nothing extracted",
                    body = "Neither full text nor an abstract was available for this paper, so there was nothing to extract.",
                    icon = RadarIcons.Document,
                )
            }
        }
    }
}

@Composable
private fun MetricTile(metric: Metric, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    RadarCard(modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatValue(metric.value), style = type.numeric.copy(fontSize = type.numeric.fontSize * 0.85f), color = colors.ink, maxLines = 1)
            val unit = metric.canonicalUnit.ifBlank { metric.unit }
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = type.mono, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(metric.name.replaceFirstChar { it.uppercase() }, style = type.bodySmall, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (metric.subject.isNotBlank()) {
            Text(metric.subject, style = type.bodySmall, color = colors.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val cond = listOfNotNull(
            metric.conditions.vdd?.let { "${formatValue(it)} V" },
            metric.conditions.tempC?.let { "${formatValue(it)} °C" },
            metric.conditions.corner?.takeIf { it.isNotBlank() },
            metric.conditions.other?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (cond.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(cond, style = type.bodySmall.copy(fontSize = type.label.fontSize), color = colors.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(RadarSpacing.sm))
        SourceHint()
    }
}

@Composable
private fun LabeledText(label: String, text: String, onShowSource: () -> Unit) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Text(label.uppercase(), style = type.label, color = colors.ink2)
    Spacer(Modifier.height(4.dp))
    Text(text, style = type.body, color = colors.ink)
    Text(
        "Show passage",
        style = type.bodySmall,
        color = colors.accent,
        modifier = Modifier.padding(top = 6.dp).clickableNoRipple(onShowSource),
    )
}

@Composable
private fun SourceHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RadarIcons.Quote, contentDescription = null, tint = RadarTheme.colors.ink3, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("Source", style = RadarTheme.typography.bodySmall, color = RadarTheme.colors.ink3)
    }
}

@Composable
private fun Pill(text: String, mono: Boolean = false) {
    val colors = RadarTheme.colors
    Text(
        text,
        style = if (mono) RadarTheme.typography.mono else RadarTheme.typography.bodySmall,
        color = colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RadarShape.pill).background(colors.raised).border(1.dp, colors.rule, RadarShape.pill).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = RadarTheme.typography.title, color = RadarTheme.colors.ink)
        Text(label, style = RadarTheme.typography.bodySmall, color = RadarTheme.colors.ink2)
    }
}

/** Bookmark with a bounce and haptic tick when saved. */
@Composable
private fun SaveButton(saved: Boolean, onToggle: () -> Unit) {
    val colors = RadarTheme.colors
    val haptics = LocalHapticFeedback.current
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(saved) {
        if (saved) {
            bounce.snapTo(0.7f)
            bounce.animateTo(1f, RadarMotion.settle())
        }
    }
    RadarIconButton(
        icon = if (saved) RadarIcons.BookmarkFilled else RadarIcons.Bookmark,
        contentDescription = if (saved) "Saved to library. Tap to remove." else "Save paper to library",
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        tint = if (saved) colors.accent else colors.ink,
        background = if (saved) colors.accentWash else colors.surface,
        border = if (saved) null else colors.rule,
        modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
    )
}

@Composable
private fun PaperSkeleton() {
    Column(Modifier.fillMaxSize().padding(RadarSpacing.gutter)) {
        SkeletonBlock(Modifier.width(140.dp).height(22.dp), shape = RadarShape.pill)
        Spacer(Modifier.height(RadarSpacing.md))
        SkeletonBlock(Modifier.fillMaxWidth().height(30.dp))
        Spacer(Modifier.height(RadarSpacing.sm))
        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(30.dp))
        Spacer(Modifier.height(RadarSpacing.xl))
        SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), shape = RadarShape.card)
        Spacer(Modifier.height(RadarSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(Modifier.weight(1f).height(110.dp), shape = RadarShape.card)
            SkeletonBlock(Modifier.weight(1f).height(110.dp), shape = RadarShape.card)
        }
    }
}

private fun formatValue(v: Double): String = formatReported(v)

/** Tappable without the default ripple (the text colour already signals it). */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
