package com.researchradar.feature.map

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.ResearchRadarTheme
import com.researchradar.core.design.components.ConfidenceTag
import com.researchradar.core.design.components.EvidenceBottomSheet
import com.researchradar.core.design.components.GapItem
import com.researchradar.core.design.components.SectionLabel
import com.researchradar.core.model.Experiment
import com.researchradar.core.model.MapFilters
import com.researchradar.core.model.MapStats
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import com.researchradar.core.model.Synthesis
import com.researchradar.feature.graph.CitationGraphCanvas
import com.researchradar.feature.graph.GraphPaperMiniCard
import com.researchradar.feature.graph.getYearColor
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.RadarChip
import com.researchradar.core.design.components.RadarIconButton
import com.researchradar.core.design.components.SkeletonBlock
import com.researchradar.core.model.buildCompareColumns
import com.researchradar.core.model.defaultCompareColumnIds
import com.researchradar.feature.map.components.ColumnPickerSheet
import com.researchradar.feature.map.components.CompareTab
import com.researchradar.feature.map.components.ConflictsTab
import com.researchradar.feature.map.components.ExperimentsTab
import com.researchradar.feature.map.components.FindingsTab
import com.researchradar.feature.map.components.GapsTab
import com.researchradar.feature.map.components.OverviewTab
import com.researchradar.feature.map.components.PapersTab
import com.researchradar.feature.map.components.ToolsTab

@Composable
fun MapScreen(
    mapId: String,
    onNavigateToPaper: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    MapScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToPaper = onNavigateToPaper,
        onSelectTab = viewModel::selectTab,
        onToggleBookmark = viewModel::toggleBookmark,
        onOpenExportDialog = viewModel::openExportDialog,
        onCloseExportDialog = viewModel::closeExportDialog,
        onPaperFilterChange = viewModel::setPaperFilter,
        onPaperSortChange = viewModel::setPaperSort,
        onClearToolFilter = viewModel::clearToolFilter,
        onToggleSelectPaperForCompare = viewModel::toggleSelectPaperForCompare,
        onOpenColumnPicker = viewModel::openColumnPicker,
        onCloseColumnPicker = viewModel::closeColumnPicker,
        onToggleCompareColumn = viewModel::toggleCompareColumn,
        onCitationClick = viewModel::showEvidence,
        onDismissEvidence = viewModel::dismissEvidence,
        onSelectTool = viewModel::filterByTool,
        onRetry = viewModel::loadMap,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreenContent(
    uiState: MapUiState,
    onNavigateBack: () -> Unit,
    onNavigateToPaper: (String) -> Unit,
    onSelectTab: (MapTab) -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenExportDialog: () -> Unit,
    onCloseExportDialog: () -> Unit,
    onPaperFilterChange: (String) -> Unit,
    onPaperSortChange: (PaperSort) -> Unit,
    onClearToolFilter: () -> Unit,
    onToggleSelectPaperForCompare: (String) -> Unit,
    onOpenColumnPicker: () -> Unit,
    onCloseColumnPicker: () -> Unit,
    onToggleCompareColumn: (String) -> Unit,
    onCitationClick: (com.researchradar.core.design.components.EvidenceSheetData) -> Unit,
    onDismissEvidence: () -> Unit,
    onSelectTool: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    var graphZoomScale by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    var graphPanOffset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var selectedGraphNode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.researchradar.core.model.GraphNode?>(null) }
    var graphInMapOnly by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar: back, compact title, save, export
        val map0 = uiState.researchMap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadarIconButton(RadarIcons.ArrowLeft, "Back", onNavigateBack)
            Spacer(Modifier.width(RadarSpacing.md))
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = map0 != null && uiState.selectedTab != MapTab.OVERVIEW,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                ) {
                    Text(
                        text = map0?.topic.orEmpty(),
                        style = type.title,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (map0 != null) {
                Spacer(Modifier.width(RadarSpacing.sm))
                BookmarkButton(saved = uiState.isBookmarked, onToggle = onToggleBookmark)
                Spacer(Modifier.width(RadarSpacing.sm))
                RadarIconButton(RadarIcons.Export, "Export map as Markdown, BibTeX or CSV", onOpenExportDialog)
            }
        }

        if (uiState.isOffline) {
            Row(
                modifier = Modifier
                    .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.xs)
                    .fillMaxWidth()
                    .clip(RadarShape.control)
                    .background(colors.accentWash)
                    .padding(horizontal = RadarSpacing.md, vertical = RadarSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Offline · showing your saved copy", style = type.bodySmall, color = colors.accent)
            }
        }

        if (uiState.isLoading) {
            MapLoadingSkeleton()
            return
        }

        if (uiState.errorMessage != null && uiState.researchMap == null) {
            MapErrorView(
                errorMessage = uiState.errorMessage,
                onRetry = onRetry,
            )
            return
        }

        val map = uiState.researchMap
        if (map == null) {
            MapEmptyView()
            return
        }

        // Pill tabs with counts; the selected tab scrolls into view
        val tabListState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(uiState.selectedTab) {
            tabListState.animateScrollToItem((uiState.selectedTab.ordinal - 1).coerceAtLeast(0))
        }
        androidx.compose.foundation.lazy.LazyRow(
            state = tabListState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = RadarSpacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm),
            modifier = Modifier.padding(vertical = RadarSpacing.xs),
        ) {
            items(MapTab.entries.size) { i ->
                val tab = MapTab.entries[i]
                RadarChip(
                    text = tab.label,
                    selected = uiState.selectedTab == tab,
                    count = tabCount(tab, map),
                    onClick = { onSelectTab(tab) },
                )
            }
        }
        Spacer(Modifier.height(RadarSpacing.xs))

        // Active tab: slides in the direction of travel
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.animation.AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (androidx.compose.animation.slideInHorizontally(com.researchradar.core.design.RadarMotion.snappy()) { it / 5 * dir } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(com.researchradar.core.design.RadarMotion.snappy()) { -it / 5 * dir } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
                },
                label = "mapTab",
            ) { activeTab ->
            when (activeTab) {
                MapTab.OVERVIEW -> {
                    OverviewTab(
                        map = map,
                        onNavigateToPaper = onNavigateToPaper,
                        onCitationClick = onCitationClick,
                    )
                }

                MapTab.PAPERS -> {
                    PapersTab(
                        papers = map.papers,
                        activeFilter = uiState.activePaperFilter,
                        activeSort = uiState.activeSort,
                        activeToolFilter = uiState.selectedToolFilter,
                        selectedPaperIdsForCompare = uiState.selectedPaperIdsForCompare,
                        onFilterChange = onPaperFilterChange,
                        onSortChange = onPaperSortChange,
                        onClearToolFilter = onClearToolFilter,
                        onToggleSelectPaperForCompare = onToggleSelectPaperForCompare,
                        onNavigateToPaper = onNavigateToPaper,
                        onNavigateToCompare = { onSelectTab(MapTab.COMPARE) },
                    )
                }

                MapTab.COMPARE -> {
                    CompareTab(
                        papers = map.papers,
                        visibleColumns = uiState.visibleCompareColumns,
                        selectedPaperIds = uiState.selectedPaperIdsForCompare,
                        onOpenColumnPicker = onOpenColumnPicker,
                        onNavigateToPaper = onNavigateToPaper,
                    )
                }

                MapTab.FINDINGS -> {
                    FindingsTab(
                        findings = map.findings,
                        papers = map.papers,
                        onCitationClick = onCitationClick,
                        onNavigateToPaper = onNavigateToPaper,
                    )
                }

                MapTab.CONFLICTS -> {
                    ConflictsTab(
                        contradictions = map.contradictions,
                        papers = map.papers,
                        onNavigateToPaper = onNavigateToPaper,
                        onCitationClick = onCitationClick,
                    )
                }

                MapTab.GAPS -> {
                    GapsTab(
                        gaps = map.gaps,
                        papers = map.papers,
                        onNavigateToPaper = onNavigateToPaper,
                        onViewExperimentForGap = { onSelectTab(MapTab.EXPERIMENTS) },
                    )
                }

                MapTab.EXPERIMENTS -> {
                    ExperimentsTab(
                        experiments = map.experiments,
                        topic = map.topic,
                    )
                }

                MapTab.TOOLS -> {
                    ToolsTab(
                        tools = map.tools,
                        papers = map.papers,
                        onSelectTool = onSelectTool,
                    )
                }

                MapTab.GRAPH -> {
                    val graphData = map.graph ?: com.researchradar.core.model.GraphData()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Graph toolbar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                val years = androidx.compose.runtime.remember(graphData.nodes) {
                                    graphData.nodes.map { it.year }.filter { it > 0 }
                                }
                                val minYear = androidx.compose.runtime.remember(years) { years.minOrNull() ?: 2000 }
                                val maxYear = androidx.compose.runtime.remember(years) { years.maxOrNull() ?: 2024 }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(RadarSpacing.xs),
                                ) {
                                    Text(text = "$minYear", style = type.caption, color = colors.ink2)
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(6.dp)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    listOf(
                                                        getYearColor(minYear, minYear, maxYear, colors.accent),
                                                        getYearColor(maxYear, minYear, maxYear, colors.accent),
                                                    )
                                                ),
                                                shape = RadarShape.chip,
                                            ),
                                    )
                                    Text(text = "$maxYear", style = type.caption, color = colors.ink2)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(RadarSpacing.xs),
                                ) {
                                    RadarChip(
                                        text = if (graphInMapOnly) "Map only" else "All works",
                                        selected = graphInMapOnly,
                                        onClick = { graphInMapOnly = !graphInMapOnly },
                                    )
                                    RadarIconButton(
                                        icon = RadarIcons.Retry,
                                        contentDescription = "Reset zoom",
                                        onClick = {
                                            graphZoomScale = 1.0f
                                            graphPanOffset = androidx.compose.ui.geometry.Offset.Zero
                                        },
                                        size = 38.dp,
                                    )
                                }
                            }

                            HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)

                            CitationGraphCanvas(
                                graphData = graphData,
                                selectedNode = selectedGraphNode,
                                inMapOnly = graphInMapOnly,
                                onNodeSelected = { selectedGraphNode = it },
                                scaleState = graphZoomScale,
                                panState = graphPanOffset,
                                onTransformChanged = { s, p ->
                                    graphZoomScale = s
                                    graphPanOffset = p
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // Floating card for the tapped paper
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectedGraphNode != null,
                            enter = androidx.compose.animation.slideInVertically(com.researchradar.core.design.RadarMotion.snappy()) { it } + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.slideOutVertically(com.researchradar.core.design.RadarMotion.snappy()) { it } + androidx.compose.animation.fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            val node = selectedGraphNode
                            if (node != null) {
                                val paper = map.papers.find { it.id == node.paperId }
                                GraphPaperMiniCard(
                                    node = node,
                                    paper = paper,
                                    onViewPaper = { onNavigateToPaper(node.paperId) },
                                    onDismiss = { selectedGraphNode = null },
                                    modifier = Modifier.padding(RadarSpacing.gutter),
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        // Column Picker Sheet for Compare tab
        if (uiState.isColumnPickerOpen) {
            val pickerPapers = uiState.researchMap?.papers.orEmpty().let { papers ->
                if (uiState.selectedPaperIdsForCompare.isNotEmpty()) {
                    papers.filter { it.id in uiState.selectedPaperIdsForCompare }
                } else {
                    papers.filter { it.extraction != null || it.hasFullText }
                }
            }
            val pickerColumns = buildCompareColumns(pickerPapers)
            ColumnPickerSheet(
                columns = pickerColumns,
                visibleColumns = uiState.visibleCompareColumns ?: defaultCompareColumnIds(pickerColumns),
                onToggleColumn = onToggleCompareColumn,
                onDismissRequest = onCloseColumnPicker,
            )
        }

        // Evidence Bottom Sheet
        if (uiState.activeEvidenceSheet != null) {
            EvidenceBottomSheet(
                data = uiState.activeEvidenceSheet,
                onDismissRequest = onDismissEvidence,
                onViewPaper = onNavigateToPaper,
            )
        }

        // Export Dialog
        if (uiState.isExportDialogOpen && uiState.researchMap != null) {
            com.researchradar.feature.map.components.ExportDialog(
                researchMap = uiState.researchMap,
                onDismiss = onCloseExportDialog,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5 Explicit Screen States: Loading, Error, Empty
// ---------------------------------------------------------------------------

@Composable
private fun MapLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(RadarSpacing.gutter),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(14.dp))
        Spacer(Modifier.height(RadarSpacing.md))
        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(30.dp))
        Spacer(Modifier.height(RadarSpacing.sm))
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(30.dp))
        Spacer(Modifier.height(RadarSpacing.xl))
        SkeletonBlock(Modifier.fillMaxWidth().height(110.dp), shape = RadarShape.hero)
        Spacer(Modifier.height(RadarSpacing.lg))
        repeat(3) {
            SkeletonBlock(Modifier.fillMaxWidth().height(96.dp), shape = RadarShape.card)
            Spacer(Modifier.height(RadarSpacing.md))
        }
    }
}

@Composable
private fun MapErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = "Couldn't load this map",
            body = errorMessage,
            icon = RadarIcons.Radar,
            actionLabel = "Try again",
            onAction = onRetry,
        )
    }
}

@Composable
private fun MapEmptyView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = "No map here",
            body = "The server has no map with this id. It may have been created before a server restart.",
            icon = RadarIcons.Radar,
        )
    }
}

/** Bookmark that fills and bounces when saved. */
@Composable
private fun BookmarkButton(saved: Boolean, onToggle: () -> Unit) {
    val colors = RadarTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bounce = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(saved) {
        if (saved) {
            bounce.snapTo(0.7f)
            bounce.animateTo(1f, com.researchradar.core.design.RadarMotion.settle())
        }
    }
    RadarIconButton(
        icon = if (saved) RadarIcons.BookmarkFilled else RadarIcons.Bookmark,
        contentDescription = if (saved) "Saved to library. Tap to remove." else "Save map to library",
        onClick = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onToggle()
        },
        tint = if (saved) colors.accent else colors.ink,
        background = if (saved) colors.accentWash else colors.surface,
        border = if (saved) null else colors.rule,
        modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
    )
}

private fun tabCount(tab: MapTab, map: com.researchradar.core.model.ResearchMap): Int? = when (tab) {
    MapTab.PAPERS -> map.papers.size
    MapTab.FINDINGS -> map.findings.size
    MapTab.CONFLICTS -> map.contradictions.size
    MapTab.GAPS -> map.gaps.size
    MapTab.EXPERIMENTS -> map.experiments.size
    MapTab.TOOLS -> map.tools.size
    else -> null
}
