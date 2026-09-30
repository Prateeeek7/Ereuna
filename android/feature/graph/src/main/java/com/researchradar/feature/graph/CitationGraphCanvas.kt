package com.researchradar.feature.graph

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.model.GraphData
import com.researchradar.core.model.GraphNode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes single-hue sequential year color in vermilion/copper palette.
 */
fun getYearColor(year: Int, minYear: Int, maxYear: Int, baseAccent: Color): Color {
    val span = (maxYear - minYear).coerceAtLeast(1)
    val fraction = ((year - minYear).toFloat() / span).coerceIn(0f, 1f)

    // Interpolate from deep warm bronze/sepia to luminous vermilion accent
    val r = 0.35f + fraction * (baseAccent.red - 0.35f)
    val g = 0.16f + fraction * (baseAccent.green - 0.16f)
    val b = 0.09f + fraction * (baseAccent.blue - 0.09f)
    return Color(red = r.coerceIn(0f, 1f), green = g.coerceIn(0f, 1f), blue = b.coerceIn(0f, 1f), alpha = 1f)
}

@Composable
fun CitationGraphCanvas(
    graphData: GraphData,
    selectedNode: GraphNode?,
    inMapOnly: Boolean,
    onNodeSelected: (GraphNode?) -> Unit,
    modifier: Modifier = Modifier,
    scaleState: Float = 1.0f,
    panState: Offset = Offset.Zero,
    onTransformChanged: (Float, Offset) -> Unit = { _, _ -> },
) {
    val colors = RadarTheme.colors

    // User zoom/pan on top of the automatic fit-to-screen transform.
    var zoom by remember(scaleState) { mutableFloatStateOf(scaleState) }
    var pan by remember(panState) { mutableStateOf(panState) }

    val nodes = remember(graphData.nodes, inMapOnly) {
        if (inMapOnly) graphData.nodes.filter { it.inMap } else graphData.nodes
    }
    val nodeMap = remember(nodes) { nodes.associateBy { it.paperId } }
    val edges = remember(graphData.edges, nodeMap) {
        graphData.edges.filter { nodeMap.containsKey(it.source) && nodeMap.containsKey(it.target) }
    }
    val neighbours = remember(edges, selectedNode) {
        val id = selectedNode?.paperId ?: return@remember emptySet<String>()
        edges.flatMap { e -> listOfNotNull(e.target.takeIf { e.source == id }, e.source.takeIf { e.target == id }) }.toSet() + id
    }
    val years = remember(nodes) { nodes.map { it.year }.filter { it > 0 } }
    val minYear = years.minOrNull() ?: 2000
    val maxYear = years.maxOrNull() ?: 2024

    // Layout positions. Linked papers keep the force layout; papers with no
    // citation link inside this set go on a tidy shelf under the network, since
    // their layout position carries no meaning and scattered they read as noise.
    val layout = remember(nodes, edges) { arrangeLayout(nodes, edges) }
    val bounds = layout.bounds

    // Nodes grow in once when the graph first appears.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    val labelPaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) } }
    val haloPaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) } }

    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val padPx = with(density) { 40.dp.toPx() }

    // Fit the layout to the canvas. Each axis may stretch up to 1.5x the other so
    // a wide layout still fills a tall phone screen without visibly distorting.
    fun fit(size: androidx.compose.ui.geometry.Size): FloatArray {
        val bw = (bounds[2] - bounds[0]).coerceAtLeast(1f)
        val bh = (bounds[3] - bounds[1]).coerceAtLeast(1f)
        val fx = (size.width - 2 * padPx) / bw
        val fy = (size.height - 2 * padPx) / bh
        val base = minOf(fx, fy).coerceAtLeast(0.05f)
        val sx = minOf(fx, base * 1.5f).coerceAtLeast(0.05f)
        val sy = minOf(fy, base * 1.5f).coerceAtLeast(0.05f)
        val ox = (size.width - bw * sx) / 2f - bounds[0] * sx
        val oy = (size.height - bh * sy) / 2f - bounds[1] * sy
        return floatArrayOf(sx, sy, ox, oy)
    }

    fun screenAt(x: Float, y: Float, size: androidx.compose.ui.geometry.Size): Offset {
        val (sx, sy, ox, oy) = fit(size)
        val cx = size.width / 2f
        val cy = size.height / 2f
        return Offset((x * sx + ox - cx) * zoom + cx + pan.x, (y * sy + oy - cy) * zoom + cy + pan.y)
    }

    fun screen(node: GraphNode, size: androidx.compose.ui.geometry.Size): Offset {
        val pos = layout.positions[node.paperId] ?: Offset(node.x, node.y)
        return screenAt(pos.x, pos.y, size)
    }

    fun radius(node: GraphNode): Float = with(density) { (node.size * 0.55f).dp.toPx() } * sqrt(zoom).coerceIn(0.7f, 1.8f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(nodes, zoom, pan, canvasSize) {
                detectTapGestures { tap ->
                    val hit = nodes
                        .map { it to screen(it, canvasSize) }
                        .minByOrNull { (_, p) -> (p - tap).getDistance() }
                        ?.takeIf { (n, p) -> (p - tap).getDistance() <= maxOf(radius(n) + 12f, 44f) }
                        ?.first
                    onNodeSelected(hit)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(0.5f, 5f)
                    pan += panChange
                    onTransformChanged(zoom, pan)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            canvasSize = size
            val t = appear.value
            val dimOthers = selectedNode != null

            // Edges
            for (edge in edges) {
                val src = nodeMap[edge.source] ?: continue
                val tgt = nodeMap[edge.target] ?: continue
                val a = screen(src, size)
                val b = screen(tgt, size)
                val d = (b - a).getDistance()
                if (d < 4f) continue
                val active = dimOthers && (edge.source == selectedNode?.paperId || edge.target == selectedNode?.paperId)
                val color = when {
                    active -> colors.accent.copy(alpha = 0.85f)
                    dimOthers -> colors.ink2.copy(alpha = 0.06f)
                    else -> colors.ink2.copy(alpha = 0.18f)
                }
                val end = b - (b - a) / d * radius(tgt)
                drawLine(color, a, a + (end - a) * t, strokeWidth = (if (active) 2.dp else 1.dp).toPx())
                if (active || zoom > 1.6f) {
                    drawArrowHead(a, end, color, (if (active) 7.dp else 5.dp).toPx())
                }
            }

            // Shelf caption for papers with no citation link in this set.
            layout.shelfY?.let { shelfY ->
                val y = screenAt(bounds[0], shelfY, size).y
                val left = padPx * 0.5f
                val right = size.width - padPx * 0.5f
                drawLine(colors.rule, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                labelPaint.textSize = 10.dp.toPx()
                labelPaint.color = colors.ink3.toArgb()
                labelPaint.textAlign = Paint.Align.LEFT
                drawContext.canvas.nativeCanvas.drawText("NOT LINKED BY CITATIONS IN THIS MAP", left, y + 14.dp.toPx(), labelPaint)
                labelPaint.textAlign = Paint.Align.CENTER
            }

            // Nodes
            for (node in nodes) {
                val p = screen(node, size)
                val r = radius(node) * t
                val faded = dimOthers && node.paperId !in neighbours
                val alpha = if (faded) 0.25f else 1f
                val fill = getYearColor(node.year, minYear, maxYear, colors.accent)
                if (node.paperId == selectedNode?.paperId) {
                    drawCircle(colors.accent.copy(alpha = 0.18f), r + 12.dp.toPx(), p)
                    drawCircle(colors.accent, r + 5.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                }
                if (node.inMap) {
                    drawCircle(fill.copy(alpha = alpha), r, p)
                    drawCircle(colors.background.copy(alpha = alpha), r, p, style = Stroke(1.5.dp.toPx()))
                } else {
                    drawCircle(colors.background, r, p)
                    drawCircle(colors.ink2.copy(alpha = 0.7f * alpha), r, p, style = Stroke(1.5.dp.toPx()))
                }
            }

            // Labels: most-cited first, below the node or else above it, skipped
            // where they would cover another label or another node.
            val nodeRects = nodes.associate { n ->
                val c = screen(n, size)
                val r = radius(n) * t
                n.paperId to android.graphics.RectF(c.x - r, c.y - r, c.x + r, c.y + r)
            }
            val placed = mutableListOf<android.graphics.RectF>()
            val textPx = 11.dp.toPx() * zoom.coerceIn(0.9f, 1.3f)
            labelPaint.textSize = textPx
            haloPaint.textSize = textPx
            haloPaint.strokeWidth = 3.dp.toPx()
            haloPaint.color = colors.background.toArgb()
            val ordered = nodes.sortedWith(
                compareByDescending<GraphNode> { it.paperId == selectedNode?.paperId }
                    .thenByDescending { it.paperId in neighbours }
                    .thenByDescending { it.citationCount },
            )
            for (node in ordered) {
                if (node.label.isBlank() || t < 0.6f) continue
                val faded = dimOthers && node.paperId !in neighbours
                if (faded) continue
                val anchor = screen(node, size)
                val w = labelPaint.measureText(node.label)
                // Keep the label on screen: shift it inward rather than clip it at the edge.
                val edge = 6.dp.toPx()
                val p = Offset(anchor.x.coerceIn(edge + w / 2, maxOf(edge + w / 2, size.width - edge - w / 2)), anchor.y)
                val gap = 2.dp.toPx()
                val below = p.y + radius(node) + textPx + gap
                val above = p.y - radius(node) - gap - 3f
                val y = listOf(below, above).firstOrNull { baseline ->
                    val rect = android.graphics.RectF(p.x - w / 2 - 4f, baseline - textPx, p.x + w / 2 + 4f, baseline + 4f)
                    placed.none { android.graphics.RectF.intersects(it, rect) } &&
                        nodeRects.none { (id, r) -> id != node.paperId && android.graphics.RectF.intersects(r, rect) }
                } ?: continue
                placed += android.graphics.RectF(p.x - w / 2 - 4f, y - textPx, p.x + w / 2 + 4f, y + 4f)
                labelPaint.color = (if (node.inMap) colors.ink else colors.ink2).toArgb()
                drawContext.canvas.nativeCanvas.drawText(node.label, p.x, y, haloPaint)
                drawContext.canvas.nativeCanvas.drawText(node.label, p.x, y, labelPaint)
            }
        }
    }
}

private class GraphLayout(
    val positions: Map<String, Offset>,
    val bounds: FloatArray,
    /** Layout y of the shelf divider, or null when every paper is linked. */
    val shelfY: Float?,
)

private fun arrangeLayout(nodes: List<GraphNode>, edges: List<com.researchradar.core.model.GraphEdge>): GraphLayout {
    if (nodes.isEmpty()) return GraphLayout(emptyMap(), floatArrayOf(0f, 0f, 1000f, 1000f), null)
    val linkedIds = edges.flatMap { listOf(it.source, it.target) }.toSet()
    val linked = nodes.filter { it.paperId in linkedIds }
    val unlinked = nodes.filter { it.paperId !in linkedIds }
    // Nothing to separate: keep the layout as computed.
    if (linked.isEmpty() || unlinked.isEmpty()) {
        val all = nodes.associate { it.paperId to Offset(it.x, it.y) }
        return GraphLayout(all, boundsOf(all.values), null)
    }
    val positions = linked.associate { it.paperId to Offset(it.x, it.y) }.toMutableMap()
    val net = boundsOf(positions.values)
    val width = (net[2] - net[0]).coerceAtLeast(1f)
    val height = (net[3] - net[1]).coerceAtLeast(1f)
    val cols = minOf(unlinked.size, 5)
    val step = width / cols
    val rowGap = maxOf(height * 0.09f, step * 0.4f)
    val shelfY = net[3] + rowGap
    unlinked.sortedByDescending { it.citationCount }.forEachIndexed { i, node ->
        val row = i / cols
        val inRow = minOf(cols, unlinked.size - row * cols)
        val rowStart = net[0] + (width - step * (inRow - 1)) / 2f
        positions[node.paperId] = Offset(rowStart + (i % cols) * step, shelfY + rowGap * (row + 1))
    }
    return GraphLayout(positions, boundsOf(positions.values), shelfY)
}

private fun boundsOf(points: Collection<Offset>): FloatArray =
    floatArrayOf(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

/**
 * Helper to draw an arrowhead at the destination point of an edge.
 */
private fun DrawScope.drawArrowHead(
    from: Offset,
    to: Offset,
    arrowColor: Color,
    arrowSize: Float,
) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val arrowAngle = Math.PI / 6.0

    val x1 = to.x - arrowSize * cos(angle - arrowAngle).toFloat()
    val y1 = to.y - arrowSize * sin(angle - arrowAngle).toFloat()
    val x2 = to.x - arrowSize * cos(angle + arrowAngle).toFloat()
    val y2 = to.y - arrowSize * sin(angle + arrowAngle).toFloat()

    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(x1, y1)
        lineTo(x2, y2)
        close()
    }
    drawPath(path = path, color = arrowColor)
}
