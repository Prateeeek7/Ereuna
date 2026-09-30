package com.researchradar.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.AnimatedCounter
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarIconButton
import com.researchradar.core.design.components.RadarSweep
import com.researchradar.core.design.components.radarBlipFor
import com.researchradar.core.design.staggeredEnter
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RunningJobRoute(
    jobId: String,
    onNavigateToMap: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningJobViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(uiState.isComplete, uiState.completedMapId) {
        val mapId = uiState.completedMapId
        if (uiState.isComplete && mapId != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // Let the "ready" state register before moving on.
            delay(1100)
            onNavigateToMap(mapId)
        }
    }

    RunningJobScreen(
        uiState = uiState,
        onCancel = {
            viewModel.cancelJob()
            onNavigateBack()
        },
        onRetry = viewModel::startListening,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
fun RunningJobScreen(
    uiState: RunningJobUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val failed = uiState.errorMessage != null
    val done = uiState.isComplete

    // Elapsed clock
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(done, failed) {
        while (!done && !failed) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedSec = ((now - uiState.startedAtMs) / 1000).coerceAtLeast(0)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = RadarSpacing.xxxl),
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadarIconButton(RadarIcons.ArrowLeft, "Back", onNavigateBack)
                Spacer(Modifier.weight(1f))
                StatusPill(done = done, failed = failed)
            }
        }

        // Topic
        item {
            Column(Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm)) {
                Text(
                    text = if (done) "Map ready" else if (failed) "Map stopped" else "Building your map",
                    style = type.label,
                    color = if (failed) colors.accent else colors.ink2,
                )
                Spacer(Modifier.height(RadarSpacing.xs))
                Text(
                    text = uiState.topic.ifBlank { "Research map" },
                    style = type.display,
                    color = colors.ink,
                    modifier = Modifier.staggeredEnter(0),
                )
            }
        }

        // Radar instrument with live stats
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.md)
                    .fillMaxWidth()
                    .clip(RadarShape.hero)
                    .background(colors.night)
                    .padding(RadarSpacing.lg)
                    .staggeredEnter(1),
            ) {
                val blips = uiState.selectedPapers.map { radarBlipFor(it.paperId.ifBlank { it.title }, it.score.toFloat()) }
                Box(Modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
                    RadarSweep(
                        modifier = Modifier.fillMaxSize(),
                        blips = blips,
                        sweeping = !done && !failed,
                    )
                    PopIn(visible = done) {
                        Box(
                            Modifier.size(64.dp).clip(RadarShape.pill).background(colors.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(RadarIcons.Check, contentDescription = "Map ready", tint = colors.onAccent, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Spacer(Modifier.height(RadarSpacing.lg))
                Row(Modifier.fillMaxWidth()) {
                    if (uiState.isUpload) {
                        NightStat("Your PDFs", uiState.uploadedCount, Modifier.weight(1f))
                        NightStat("Related", uiState.selectedCount, Modifier.weight(1f))
                    } else {
                        NightStat("Found", uiState.candidateCount, Modifier.weight(1f))
                        NightStat("Selected", uiState.selectedCount.takeIf { it > 0 } ?: uiState.selectedPapers.size, Modifier.weight(1f))
                    }
                    NightStat("Full text", uiState.fullTextCount + uiState.uploadedCount, Modifier.weight(1f))
                    Column(Modifier.weight(1f)) {
                        Text(formatClock(elapsedSec), style = type.numeric.copy(fontSize = type.numeric.fontSize * 0.8f), color = colors.onNight)
                        Text("ELAPSED", style = type.label, color = colors.onNight.copy(alpha = 0.55f))
                    }
                }
            }
        }

        // Latest activity ticker
        item {
            val latest = uiState.logs.lastOrNull()?.second.orEmpty()
            Row(
                modifier = Modifier
                    .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm)
                    .fillMaxWidth()
                    .clip(RadarShape.control)
                    .background(colors.surface)
                    .border(1.dp, colors.rule, RadarShape.control)
                    .padding(horizontal = RadarSpacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot(active = !done && !failed)
                Spacer(Modifier.width(RadarSpacing.sm))
                AnimatedContent(
                    targetState = latest,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "ticker",
                    modifier = Modifier.weight(1f),
                ) { line ->
                    Text(line, style = type.bodySmall, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Error card
        if (failed) {
            item {
                Column(
                    Modifier
                        .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm)
                        .fillMaxWidth()
                        .clip(RadarShape.card)
                        .background(colors.accentWash)
                        .padding(RadarSpacing.lg),
                ) {
                    Text("The map couldn't be finished", style = type.title, color = colors.ink)
                    Spacer(Modifier.height(RadarSpacing.xs))
                    Text(uiState.errorMessage.orEmpty(), style = type.bodySmall, color = colors.ink2)
                    Spacer(Modifier.height(RadarSpacing.md))
                    RadarButton(text = "Reconnect", icon = RadarIcons.Retry, onClick = onRetry, style = RadarButtonStyle.Secondary, height = 44.dp)
                }
            }
        }

        // Stage timeline
        item {
            Text(
                "STEPS",
                style = type.label,
                color = colors.ink2,
                modifier = Modifier.padding(start = RadarSpacing.gutter, top = RadarSpacing.lg, bottom = RadarSpacing.sm),
            )
        }
        itemsIndexed(uiState.stages) { index, name ->
            val stageNumber = index + 1
            val state = when {
                done || uiState.currentStage > stageNumber -> StepState.Done
                uiState.currentStage == stageNumber && failed -> StepState.Failed
                uiState.currentStage == stageNumber -> StepState.Active
                else -> StepState.Pending
            }
            StageRow(
                name = name,
                state = state,
                isLast = index == uiState.stages.lastIndex,
                modifier = Modifier
                    .padding(horizontal = RadarSpacing.gutter)
                    .staggeredEnter(2 + index, lift = 14f),
            )
        }

        // Full log (collapsed)
        item {
            var showLog by remember { mutableStateOf(false) }
            val chevron by animateFloatAsState(if (showLog) 180f else 0f, RadarMotion.snappy(), label = "logChevron")
            Column(Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.lg)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RadarShape.control)
                        .clickable { showLog = !showLog }
                        .padding(vertical = RadarSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Detailed log", style = type.title, color = colors.ink)
                    Spacer(Modifier.width(RadarSpacing.sm))
                    Text("${uiState.logs.size}", style = type.mono, color = colors.ink2)
                    Spacer(Modifier.weight(1f))
                    Icon(RadarIcons.ChevronDown, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(20.dp).rotate(chevron))
                }
                AnimatedVisibility(visible = showLog, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RadarShape.control)
                            .background(colors.night)
                            .padding(RadarSpacing.md),
                    ) {
                        uiState.logs.forEach { (time, message) ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(time, style = type.mono, color = colors.accent)
                                Spacer(Modifier.width(RadarSpacing.sm))
                                Text(message, style = type.mono, color = colors.onNight)
                            }
                        }
                    }
                }
                if (!done && !failed) {
                    Spacer(Modifier.height(RadarSpacing.lg))
                    RadarButton(
                        text = "Stop and go back",
                        onClick = onCancel,
                        style = RadarButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(RadarSpacing.sm))
                    Text(
                        "Maps take a few minutes: the server reads full papers and checks every quote.",
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                }
            }
        }
    }
}

private enum class StepState { Pending, Active, Done, Failed }

/** Springs content in when it becomes visible (no layout scope receiver). */
@Composable
private fun PopIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = scaleIn(RadarMotion.settle()) + fadeIn()) { content() }
}

@Composable
private fun StageRow(name: String, state: StepState, isLast: Boolean, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val nodeColor by animateColorAsState(
        when (state) {
            StepState.Done -> colors.ink
            StepState.Active -> colors.accent
            StepState.Failed -> colors.accent
            StepState.Pending -> colors.rule
        },
        label = "stageNode",
    )
    val textColor by animateColorAsState(if (state == StepState.Pending) colors.ink3 else colors.ink, label = "stageText")

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (state == StepState.Active) PulseRing(colors.accent)
                Box(
                    Modifier
                        .size(if (state == StepState.Pending) 10.dp else 20.dp)
                        .clip(RadarShape.pill)
                        .background(if (state == StepState.Pending) colors.background else nodeColor)
                        .border(1.5.dp, nodeColor, RadarShape.pill),
                    contentAlignment = Alignment.Center,
                ) {
                    PopIn(visible = state == StepState.Done) {
                        Icon(RadarIcons.Check, contentDescription = null, tint = colors.paper, modifier = Modifier.size(13.dp))
                    }
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.5.dp)
                        .height(22.dp)
                        .background(if (state == StepState.Done) colors.ink else colors.rule),
                )
            }
        }
        Spacer(Modifier.width(RadarSpacing.md))
        Text(
            text = name,
            style = if (state == StepState.Active) type.title else type.body,
            color = textColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PulseRing(color: androidx.compose.ui.graphics.Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val scale by t.animateFloat(0.8f, 1.35f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "pulseScale")
    val alpha by t.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "pulseAlpha")
    Box(
        Modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RadarShape.pill)
            .background(color),
    )
}

@Composable
private fun LiveDot(active: Boolean) {
    val colors = RadarTheme.colors
    val t = rememberInfiniteTransition(label = "liveDot")
    val alpha by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "liveAlpha")
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = if (active) alpha else 1f }
            .clip(RadarShape.pill)
            .background(if (active) colors.accent else colors.positive),
    )
}

@Composable
private fun StatusPill(done: Boolean, failed: Boolean) {
    val colors = RadarTheme.colors
    val (label, bg, fg) = when {
        done -> Triple("Ready", colors.positive, colors.paper)
        failed -> Triple("Stopped", colors.accentWash, colors.accent)
        else -> Triple("Live", colors.night, colors.onNight)
    }
    Row(
        Modifier
            .clip(RadarShape.pill)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!done && !failed) {
            LiveDot(active = true)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = RadarTheme.typography.title.copy(fontSize = RadarTheme.typography.bodySmall.fontSize), color = fg)
    }
}

@Composable
private fun NightStat(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Column(modifier) {
        if (value > 0) {
            AnimatedCounter(value = value, style = type.numeric.copy(fontSize = type.numeric.fontSize * 0.8f), color = colors.onNight)
        } else {
            Text("–", style = type.numeric.copy(fontSize = type.numeric.fontSize * 0.8f), color = colors.onNight.copy(alpha = 0.4f))
        }
        Text(label.uppercase(), style = type.label, color = colors.onNight.copy(alpha = 0.55f), maxLines = 1)
    }
}

private fun formatClock(seconds: Long): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
