package com.researchradar.feature.graph

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.model.GraphNode
import com.researchradar.core.model.Paper

@Composable
fun GraphScreen(
    mapId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToPaper: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GraphScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToPaper = onNavigateToPaper,
        onSelectNode = viewModel::selectNode,
        onDismissSelectedNode = viewModel::dismissSelectedNode,
        onToggleInMapOnly = viewModel::toggleInMapOnly,
        onRetry = { viewModel.loadGraph(mapId) },
        modifier = modifier,
    )
}

@Composable
fun GraphScreenContent(
    uiState: GraphUiState,
    onNavigateBack: () -> Unit,
    onNavigateToPaper: (String) -> Unit,
    onSelectNode: (GraphNode?) -> Unit,
    onDismissSelectedNode: () -> Unit,
    onToggleInMapOnly: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val years = remember(uiState.graphData.nodes) {
        uiState.graphData.nodes.map { it.year }.filter { it > 0 }
    }
    val minYear = remember(years) { years.minOrNull() ?: 2000 }
    val maxYear = remember(years) { years.maxOrNull() ?: 2024 }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "←",
                        style = type.heading,
                        color = colors.ink,
                        modifier = Modifier
                            .clickable(onClick = onNavigateBack)
                            .padding(end = RadarSpacing.md),
                    )
                    Column {
                        Text(
                            text = "CITATION NETWORK GRAPH",
                            style = type.sectionHeader,
                            color = colors.ink2,
                        )
                        Text(
                            text = uiState.topic.ifEmpty { "Citation Map" },
                            style = type.heading,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RadarSpacing.xs),
                ) {
                    // Filter in-map only toggle
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (uiState.inMapOnly) colors.accent else colors.rule,
                                RadarShape.chip,
                            )
                            .background(
                                if (uiState.inMapOnly) colors.accentWash else colors.paper,
                                RadarShape.chip,
                            )
                            .clickable(onClick = onToggleInMapOnly)
                            .padding(horizontal = RadarSpacing.sm, vertical = RadarSpacing.xs),
                    ) {
                        Text(
                            text = if (uiState.inMapOnly) "● IN-MAP ONLY" else "○ ALL NODES",
                            style = type.mono,
                            color = if (uiState.inMapOnly) colors.accent else colors.ink,
                        )
                    }

                    // Reset view button
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.rule, RadarShape.chip)
                            .background(colors.paper, RadarShape.chip)
                            .clickable {
                                zoomScale = 1.0f
                                panOffset = Offset.Zero
                            }
                            .padding(horizontal = RadarSpacing.sm, vertical = RadarSpacing.xs),
                    ) {
                        Text(
                            text = "⟲ RESET",
                            style = type.mono,
                            color = colors.ink,
                        )
                    }
                }
            }

            HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)

            // Year Legend Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RadarSpacing.xs),
                ) {
                    Text(text = "$minYear", style = type.caption, color = colors.ink2)
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(8.dp)
                            .background(
                                brush = Brush.horizontalGradient(
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

                Text(
                    text = "Radius ∝ √Citations · Drag to Pan · Pinch to Zoom",
                    style = type.caption,
                    color = colors.ink2,
                )
            }

            HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)

            // Main Canvas Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                    }
                } else if (uiState.errorMessage != null && uiState.graphData.nodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(RadarSpacing.gutter),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.errorMessage, style = type.body, color = colors.ink)
                            Spacer(modifier = Modifier.height(RadarSpacing.md))
                            Box(
                                modifier = Modifier
                                    .border(1.dp, colors.rule, RadarShape.chip)
                                    .clickable(onClick = onRetry)
                                    .padding(horizontal = RadarSpacing.md, vertical = RadarSpacing.sm),
                            ) {
                                Text(text = "RETRY", style = type.mono, color = colors.accent)
                            }
                        }
                    }
                } else {
                    CitationGraphCanvas(
                        graphData = uiState.graphData,
                        selectedNode = uiState.selectedNode,
                        inMapOnly = uiState.inMapOnly,
                        onNodeSelected = onSelectNode,
                        scaleState = zoomScale,
                        panState = panOffset,
                        onTransformChanged = { newScale, newPan ->
                            zoomScale = newScale
                            panOffset = newPan
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Floating Mini-Card Popover when a node is selected
        if (uiState.selectedNode != null) {
            val node = uiState.selectedNode
            val paper = uiState.selectedPaper

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(RadarSpacing.gutter),
            ) {
                GraphPaperMiniCard(
                    node = node,
                    paper = paper,
                    onViewPaper = {
                        onNavigateToPaper(node.paperId)
                    },
                    onDismiss = onDismissSelectedNode,
                )
            }
        }
    }
}

@Composable
fun GraphPaperMiniCard(
    node: GraphNode,
    paper: Paper?,
    onViewPaper: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RadarShape.card, ambientColor = colors.ink.copy(alpha = 0.2f), spotColor = colors.ink.copy(alpha = 0.2f))
            .clip(RadarShape.card)
            .background(colors.surface)
            .border(1.dp, colors.rule, RadarShape.card)
            .padding(RadarSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (node.inMap) "In this map" else "Cited by papers in this map",
                style = type.label,
                color = if (node.inMap) colors.accent else colors.ink2,
                modifier = Modifier.weight(1f),
            )
            com.researchradar.core.design.components.RadarIconButton(
                icon = com.researchradar.core.design.RadarIcons.Close,
                contentDescription = "Close",
                onClick = onDismiss,
                size = 34.dp,
                border = null,
                background = colors.raised,
                tint = colors.ink2,
            )
        }
        Spacer(modifier = Modifier.height(RadarSpacing.xs))
        Text(
            text = paper?.title ?: node.label,
            style = type.paperTitle,
            color = colors.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(RadarSpacing.sm))
        Text(
            text = listOfNotNull(
                node.label.takeIf { it.isNotBlank() },
                node.year.takeIf { it > 0 }?.toString(),
                "${node.citationCount} citations",
                paper?.venue?.takeIf { it.isNotBlank() },
            ).joinToString("  ·  "),
            style = type.bodySmall,
            color = colors.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (node.inMap) {
            Spacer(modifier = Modifier.height(RadarSpacing.md))
            com.researchradar.core.design.components.RadarButton(
                text = "Open paper",
                trailingIcon = com.researchradar.core.design.RadarIcons.ArrowRight,
                onClick = onViewPaper,
                style = com.researchradar.core.design.components.RadarButtonStyle.Primary,
                height = 44.dp,
            )
        }
    }
}
