package com.researchradar.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.PaperRow
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.components.RadarIconButton
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.ResearchMap

@Composable
fun LibraryScreen(
    onNavigateToMap: (String) -> Unit,
    onNavigateToPaper: (String) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val query = state.searchQuery.trim().lowercase()
    val maps = state.savedMaps.filter { query.isEmpty() || it.topic.lowercase().contains(query) }
    val papers = state.savedPapers.filter { query.isEmpty() || it.title.lowercase().contains(query) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = RadarSpacing.bottomBarClearance),
    ) {
        item {
            Column(Modifier.padding(horizontal = RadarSpacing.gutter).padding(top = RadarSpacing.lg)) {
                Text("Library", style = type.hero, color = colors.ink, modifier = Modifier.staggeredEnter(0))
                Text(
                    "Maps and papers you've saved. They stay readable offline.",
                    style = type.body,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = RadarSpacing.xs).staggeredEnter(1),
                )
                Spacer(Modifier.height(RadarSpacing.lg))
                FilterField(value = state.searchQuery, onValueChange = viewModel::setSearchQuery, modifier = Modifier.staggeredEnter(2))
                Spacer(Modifier.height(RadarSpacing.md))
                TabSwitch(
                    selected = state.selectedTab,
                    mapsCount = state.savedMaps.size,
                    papersCount = state.savedPapers.size,
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.staggeredEnter(3),
                )
                Spacer(Modifier.height(RadarSpacing.md))
            }
        }

        when (state.selectedTab) {
            LibraryTab.MAPS -> {
                if (maps.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (query.isEmpty()) "No saved maps yet" else "No maps match",
                            body = if (query.isEmpty()) "Open a map and tap the bookmark to keep it here." else "Try a different word.",
                            icon = RadarIcons.Bookmark,
                            actionLabel = if (query.isEmpty()) "Start a search" else null,
                            onAction = if (query.isEmpty()) onBack else null,
                        )
                    }
                }
                items(maps, key = { it.id }) { map ->
                    SavedMapCard(
                        map = map,
                        onOpen = { onNavigateToMap(map.id) },
                        onRemove = { viewModel.unsaveMap(map) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            LibraryTab.PAPERS -> {
                if (papers.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (query.isEmpty()) "No saved papers yet" else "No papers match",
                            body = if (query.isEmpty()) "Open a paper and tap the bookmark to keep it here." else "Try a different word.",
                            icon = RadarIcons.Document,
                        )
                    }
                }
                items(papers, key = { it.id }) { paper ->
                    Box(Modifier.animateItem()) {
                        PaperRow(
                            title = paper.title,
                            year = paper.year,
                            venue = paper.venue,
                            citationCount = paper.citationCount,
                            relevanceReason = "",
                            hasFullText = paper.hasFullText,
                            isUploaded = paper.isUploaded,
                            onClick = { onNavigateToPaper(paper.id) },
                            onLongClick = { viewModel.unsavePaper(paper) },
                        )
                    }
                }
                if (papers.isNotEmpty()) {
                    item {
                        Text(
                            "Press and hold a paper to remove it.",
                            style = type.bodySmall,
                            color = colors.ink3,
                            modifier = Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMapCard(map: ResearchMap, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val total = map.stats.totalPapers.takeIf { it > 0 } ?: map.papers.size
    RadarCard(
        modifier = modifier
            .padding(horizontal = RadarSpacing.gutter, vertical = 5.dp)
            .fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                map.topic,
                style = type.heading,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(RadarSpacing.sm))
            RadarIconButton(
                icon = RadarIcons.BookmarkFilled,
                contentDescription = "Remove ${map.topic} from library",
                onClick = onRemove,
                tint = colors.accent,
                background = colors.accentWash,
                border = null,
                size = 38.dp,
            )
        }
        Spacer(Modifier.height(RadarSpacing.sm))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(RadarSpacing.lg)) {
            MiniStat("$total", "papers")
            MiniStat("${map.stats.fullTextPapers}", "full text")
            MiniStat("${map.gaps.size.takeIf { it > 0 } ?: map.stats.gapsCount}", "gaps")
        }
        if (map.synthesis.text.isNotBlank()) {
            Spacer(Modifier.height(RadarSpacing.sm))
            Text(map.synthesis.text, style = type.bodySmall, color = colors.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = RadarTheme.typography.mono.copy(fontWeight = RadarTheme.typography.title.fontWeight), color = RadarTheme.colors.ink)
        Spacer(Modifier.width(4.dp))
        Text(label, style = RadarTheme.typography.bodySmall, color = RadarTheme.colors.ink2)
    }
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RadarShape.pill)
            .background(colors.surface)
            .border(1.dp, colors.rule, RadarShape.pill)
            .padding(horizontal = RadarSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(RadarIcons.Search, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(RadarSpacing.sm))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Filter your library", style = type.body, color = colors.ink3)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                RadarIcons.Close,
                contentDescription = "Clear filter",
                tint = colors.ink2,
                modifier = Modifier.size(18.dp).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun TabSwitch(
    selected: LibraryTab,
    mapsCount: Int,
    papersCount: Int,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RadarShape.pill)
            .background(colors.raised)
            .border(1.dp, colors.rule, RadarShape.pill)
            .padding(4.dp),
    ) {
        val half = maxWidth / 2
        val offset by animateDpAsState(if (selected == LibraryTab.PAPERS) half else 0.dp, RadarMotion.settle(), label = "libThumb")
        Box(Modifier.offset(x = offset).width(half).fillMaxHeight().clip(RadarShape.pill).background(colors.night))
        Row(Modifier.fillMaxSize()) {
            listOf(LibraryTab.MAPS to "Maps  $mapsCount", LibraryTab.PAPERS to "Papers  $papersCount").forEach { (tab, label) ->
                val fg by animateColorAsState(if (tab == selected) colors.onNight else colors.ink2, label = "libTabFg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RadarShape.pill)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = type.title.copy(fontSize = type.bodySmall.fontSize), color = fg)
                }
            }
        }
    }
}
