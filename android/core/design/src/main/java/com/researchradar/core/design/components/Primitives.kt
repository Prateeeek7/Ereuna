package com.researchradar.core.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.AnimatedCounter
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.pressScale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Surfaces
// ─────────────────────────────────────────────────────────────────────────────

/** Content card: surface fill, hairline border, springy press when clickable. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RadarCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RadarShape.card,
    color: Color = RadarTheme.colors.surface,
    border: Color? = RadarTheme.colors.rule,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(RadarSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val clickable = onClick != null || onLongClick != null
    var m = modifier
    if (clickable) m = m.pressScale(interaction, 0.98f)
    m = m.clip(shape).background(color, shape)
    if (border != null) m = m.border(BorderStroke(borderWidth, border), shape)
    if (clickable) {
        m = m.combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onLongClick = onLongClick?.let { lc ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    lc()
                }
            },
            onClick = { onClick?.invoke() },
        )
    }
    Column(modifier = m.padding(contentPadding), content = content)
}

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

enum class RadarButtonStyle { Primary, Accent, Secondary, Ghost }

/**
 * Pill button. Primary = dark instrument fill, Accent = vermilion, Secondary =
 * outlined. Springs on press, gives a light haptic tick, and morphs its label
 * into a spinner while [loading].
 */
@Composable
fun RadarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RadarButtonStyle = RadarButtonStyle.Primary,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp,
    contentColor: Color? = null,
) {
    val colors = RadarTheme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val (bg, defaultFg, borderColor) = when (style) {
        RadarButtonStyle.Primary -> Triple(colors.night, colors.onNight, null)
        RadarButtonStyle.Accent -> Triple(colors.accent, colors.onAccent, null)
        RadarButtonStyle.Secondary -> Triple(colors.surface, colors.ink, colors.rule)
        RadarButtonStyle.Ghost -> Triple(Color.Transparent, colors.ink, null)
    }
    val fg = contentColor ?: defaultFg
    val bgAnimated by animateColorAsState(if (enabled) bg else colors.rule, label = "btnBg")
    val fgAnimated by animateColorAsState(if (enabled) fg else colors.ink3, label = "btnFg")

    Box(
        modifier = modifier
            .pressScale(interaction)
            .height(height)
            .clip(RadarShape.pill)
            .background(bgAnimated, RadarShape.pill)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RadarShape.pill) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = RadarSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)) },
            label = "btnLoading",
        ) { isLoading ->
            if (isLoading) {
                RadarSpinner(color = fgAnimated, size = 20.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm)) {
                    if (icon != null) Icon(icon, contentDescription = null, tint = fgAnimated, modifier = Modifier.size(20.dp))
                    Text(text, style = RadarTheme.typography.title, color = fgAnimated, maxLines = 1)
                    if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = fgAnimated, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Round icon button with press spring. */
@Composable
fun RadarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = RadarTheme.colors.ink,
    background: Color = RadarTheme.colors.surface,
    border: Color? = RadarTheme.colors.rule,
    size: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .size(size)
            .clip(RadarShape.pill)
            .background(background, RadarShape.pill)
            .then(if (border != null) Modifier.border(1.dp, border, RadarShape.pill) else Modifier)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Selectable pill chip with animated fill and border. */
@Composable
fun RadarChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    count: Int? = null,
) {
    val colors = RadarTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) colors.night else colors.surface, label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.onNight else colors.ink, label = "chipFg")
    val stroke by animateColorAsState(if (selected) colors.night else colors.rule, label = "chipBorder")
    Row(
        modifier = modifier
            .pressScale(interaction, 0.94f)
            .defaultMinSize(minHeight = 38.dp)
            .clip(RadarShape.pill)
            .background(bg, RadarShape.pill)
            .border(1.dp, stroke, RadarShape.pill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = RadarTheme.typography.title.copy(fontSize = RadarTheme.typography.bodySmall.fontSize), color = fg, maxLines = 1)
        if (count != null) {
            Text(count.toString(), style = RadarTheme.typography.mono, color = fg.copy(alpha = 0.7f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data display
// ─────────────────────────────────────────────────────────────────────────────

/** A figure that counts up, with its label beneath. */
@Composable
fun StatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    suffix: String = "",
    valueColor: Color = RadarTheme.colors.ink,
    labelColor: Color = RadarTheme.colors.ink2,
) {
    Column(modifier = modifier) {
        AnimatedCounter(value = value, style = RadarTheme.typography.numeric, color = valueColor, suffix = suffix)
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), style = RadarTheme.typography.label, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Heading row: serif title, optional mono count badge, optional trailing content. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = RadarTheme.typography.heading, color = RadarTheme.colors.ink)
        if (count != null) {
            Spacer(Modifier.width(RadarSpacing.sm))
            Box(
                Modifier
                    .clip(RadarShape.pill)
                    .background(RadarTheme.colors.raised)
                    .border(1.dp, RadarTheme.colors.rule, RadarShape.pill)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(count.toString(), style = RadarTheme.typography.mono, color = RadarTheme.colors.ink2)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Plain empty state: what's missing, why, and optionally one action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = RadarSpacing.xxl, vertical = RadarSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(52.dp).clip(RadarShape.pill).background(RadarTheme.colors.raised).border(1.dp, RadarTheme.colors.rule, RadarShape.pill),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = RadarTheme.colors.ink2, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.height(RadarSpacing.lg))
        }
        Text(title, style = RadarTheme.typography.heading, color = RadarTheme.colors.ink)
        Spacer(Modifier.height(RadarSpacing.sm))
        Text(body, style = RadarTheme.typography.bodySmall, color = RadarTheme.colors.ink2, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(RadarSpacing.xl))
            RadarButton(text = actionLabel, onClick = onAction, style = RadarButtonStyle.Secondary, height = 44.dp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading
// ─────────────────────────────────────────────────────────────────────────────

/** Small rotating arc. */
@Composable
fun RadarSpinner(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp, strokeWidth: Dp = 2.dp) {
    val t = rememberInfiniteTransition(label = "spinner")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinAngle")
    Canvas(modifier.size(size)) {
        rotate(angle) {
            drawArc(color, startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

/** Placeholder block that breathes gently while content loads. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RadarShape.control) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val alpha by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "skeletonAlpha")
    Box(modifier.graphicsLayer { this.alpha = alpha }.clip(shape).background(RadarTheme.colors.rule))
}

// ─────────────────────────────────────────────────────────────────────────────
// Radar instrument
// ─────────────────────────────────────────────────────────────────────────────

/** A detected paper on the radar: angle 0..1 around, distance 0 (centre) ..1 (edge). */
data class RadarBlip(val key: String, val angle: Float, val distance: Float, val strength: Float = 1f)

/** Deterministic blip placement from a stable id and a relevance score (closer = more relevant). */
fun radarBlipFor(key: String, relevance: Float): RadarBlip {
    val h = key.hashCode().toLong() and 0xffffffffL
    val angle = (h % 3600) / 3600f
    val distance = (1f - relevance.coerceIn(0f, 1f)) * 0.75f + 0.18f
    return RadarBlip(key, angle, distance.coerceIn(0.15f, 0.95f), 0.6f + relevance.coerceIn(0f, 1f) * 0.4f)
}

/**
 * The app's signature instrument: range rings, a rotating sweep with a fading
 * trail, and blips that flare as the sweep passes over them. Blips added later
 * pop in, so papers visibly "arrive" while a map is being built.
 */
@Composable
fun RadarSweep(
    modifier: Modifier = Modifier,
    blips: List<RadarBlip> = emptyList(),
    sweeping: Boolean = true,
    ringColor: Color = RadarTheme.colors.nightRule,
    sweepColor: Color = RadarTheme.colors.accent,
    blipColor: Color = RadarTheme.colors.onNight,
    periodMillis: Int = 3200,
) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing)), label = "sweep")
    val appear = remember { mutableMapOf<String, Long>() }
    val composedAt = System.currentTimeMillis()
    blips.forEach { appear.getOrPut(it.key) { composedAt } }

    Canvas(modifier) {
        // Read the sweep here so this block re-runs every frame, then use the
        // frame time for pop-in progress.
        val sweepNow = sweep
        val now = System.currentTimeMillis()
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) / 2f * 0.96f
        val ringStroke = Stroke(1.dp.toPx())

        // Range rings and crosshair
        for (i in 1..4) drawCircle(ringColor, radius = r * i / 4f, center = c, style = ringStroke)
        drawLine(ringColor, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(ringColor, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        // Tick marks around the rim
        for (i in 0 until 72) {
            val a = i / 72f * 2f * PI.toFloat()
            val inner = if (i % 6 == 0) r * 0.92f else r * 0.96f
            drawLine(ringColor, Offset(c.x + cos(a) * inner, c.y + sin(a) * inner), Offset(c.x + cos(a) * r, c.y + sin(a) * r), 1.dp.toPx())
        }

        val sweepDeg = sweepNow * 360f - 90f
        if (sweeping) {
            // Trail: stacked thin wedges fading behind the sweep line
            val steps = 28
            for (i in 0 until steps) {
                val alpha = (1f - i / steps.toFloat()) * 0.28f
                drawArc(
                    color = sweepColor.copy(alpha = alpha),
                    startAngle = sweepDeg - (i + 1) * 2.2f,
                    sweepAngle = 2.3f,
                    useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                )
            }
            val a = Math.toRadians(sweepDeg.toDouble())
            drawLine(sweepColor, c, Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r), 1.6.dp.toPx())
        }

        // Blips: pop in on arrival, flare when the sweep passes
        blips.forEach { b ->
            val deg = b.angle * 360f - 90f
            val a = Math.toRadians(deg.toDouble())
            val p = Offset(c.x + cos(a).toFloat() * r * b.distance, c.y + sin(a).toFloat() * r * b.distance)
            val age = (now - (appear[b.key] ?: now)).coerceAtLeast(0L)
            val pop = if (sweeping) (age / 380f).coerceIn(0f, 1f) else 1f
            val behind = ((sweepDeg - deg) % 360f + 360f) % 360f
            val flare = if (sweeping) (1f - (behind / 120f)).coerceIn(0f, 1f) else 0.5f
            val base = 2.6.dp.toPx() * (0.6f + b.strength * 0.6f)
            val radius = base * (0.4f + 0.6f * pop) * (1f + flare * 0.5f)
            drawCircle(sweepColor.copy(alpha = 0.18f * flare * pop), radius = radius * 3.2f, center = p)
            drawCircle(blipColor.copy(alpha = (0.45f + 0.55f * flare) * pop), radius = radius, center = p)
        }
        drawCircle(sweepColor, radius = 2.5.dp.toPx(), center = c)
    }
}
