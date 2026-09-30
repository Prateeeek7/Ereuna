package com.researchradar.feature.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.components.SectionHeader
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ToolEntry
import kotlinx.coroutines.delay

@Composable
fun ToolsTab(
    tools: List<ToolEntry>,
    papers: List<Paper>,
    onSelectTool: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    val entries = remember(tools, papers) {
        tools.ifEmpty {
            val byName = linkedMapOf<String, Pair<String, MutableSet<String>>>()
            papers.forEach { paper ->
                paper.extraction?.tools?.forEach { t ->
                    byName.getOrPut(t.name) { t.category.ifBlank { "tool" } to mutableSetOf() }.second.add(paper.id)
                }
                paper.extraction?.datasets?.forEach { d ->
                    byName.getOrPut(d.name) { "dataset" to mutableSetOf() }.second.add(paper.id)
                }
            }
            byName.map { (name, v) -> ToolEntry(name = name, category = v.first, count = v.second.size, paperIds = v.second.toList()) }
                .sortedByDescending { it.count }
        }
    }

    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No tools or datasets",
                body = "None of the papers name software, instruments or datasets that could be verified in their text.",
                icon = RadarIcons.Sliders,
            )
        }
        return
    }

    val maxCount = entries.maxOf { it.count }.coerceAtLeast(1)
    val groups = entries.groupBy { categoryTitle(it.category) }.toList().sortedByDescending { (_, v) -> v.sumOf { it.count } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = RadarSpacing.xxxl)) {
        item {
            Text(
                "Software, instruments and datasets the papers say they used. Tap one to see those papers.",
                style = type.bodySmall,
                color = colors.ink2,
                modifier = Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
            )
        }
        var index = 0
        groups.forEach { (title, items) ->
            item(key = "h_$title") {
                SectionHeader(
                    title,
                    count = items.size,
                    modifier = Modifier.padding(horizontal = RadarSpacing.gutter).padding(top = RadarSpacing.lg, bottom = RadarSpacing.sm),
                )
            }
            item(key = "g_$title") {
                RadarCard(
                    modifier = Modifier.padding(horizontal = RadarSpacing.gutter).fillMaxWidth().staggeredEnter(index++),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items.forEachIndexed { i, tool ->
                        ToolRow(tool = tool, maxCount = maxCount, delayIndex = i, onClick = { onSelectTool(tool.name) })
                        if (i < items.lastIndex) {
                            Box(Modifier.padding(horizontal = RadarSpacing.lg).fillMaxWidth().height(1.dp).background(colors.rule))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: ToolEntry, maxCount: Int, delayIndex: Int, onClick: () -> Unit) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val target = tool.count / maxCount.toFloat()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(120L + delayIndex * 60L)
        grow.animateTo(target, tween(650))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RadarSpacing.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tool.name, style = type.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.85f).height(5.dp).clip(RadarShape.pill).background(colors.raised)) {
                Box(Modifier.fillMaxWidth(grow.value).fillMaxHeight().clip(RadarShape.pill).background(colors.accent))
            }
        }
        Spacer(Modifier.width(RadarSpacing.md))
        Text(
            "${tool.count} ${if (tool.count == 1) "paper" else "papers"}",
            style = type.mono,
            color = colors.ink2,
        )
        Spacer(Modifier.width(RadarSpacing.sm))
        Icon(RadarIcons.ArrowRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
    }
}

private fun categoryTitle(category: String): String = when (category.lowercase().trim()) {
    "dataset", "benchmark", "dataset & benchmark" -> "Datasets & benchmarks"
    "framework", "software", "platform" -> "Software & frameworks"
    "simulator", "eda", "model" -> "Simulation & design tools"
    "hardware", "instrument" -> "Hardware & instruments"
    "", "tool" -> "Tools"
    else -> category.replaceFirstChar { it.uppercase() }
}
