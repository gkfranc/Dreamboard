package com.dreamboard.app.core.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dreamboard.app.core.designsystem.theme.DbTheme
import kotlin.math.roundToInt

/**
 * Smooth line + soft area chart drawn on Canvas (no third-party dependency).
 * Tap or drag to inspect a point; the caller renders the readout for [selectedIndex].
 * Points from [forecastFromIndex] onwards are drawn dashed to mark projections.
 */
@Composable
fun LineAreaChart(
    values: List<Long>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColor: Color = DbTheme.colors.gold,
    forecastFromIndex: Int? = null,
    contentDescription: String = "Line chart",
) {
    val reveal = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { reveal.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val grid = DbTheme.colors.hairline
    val surface = DbTheme.colors.surfaceRaised

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(values) {
                detectTapGestures { o -> onSelect(indexAt(o.x, size.width.toFloat(), values.size)) }
            }
            .pointerInput(values) {
                detectHorizontalDragGestures { change, _ ->
                    onSelect(indexAt(change.position.x, size.width.toFloat(), values.size))
                }
            },
    ) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val top = 12f
        val bottom = h - 8f
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it != 0L } ?: 1L
        fun x(i: Int) = w * i / (values.size - 1)
        fun y(v: Long) = bottom - (bottom - top) * ((v - min).toDouble() / span).toFloat()

        // Grid
        for (k in 0..3) {
            val gy = top + (bottom - top) * k / 3f
            drawLine(grid.copy(alpha = 0.6f), Offset(0f, gy), Offset(w, gy), 1f)
        }
        if (min < 0 && max > 0) {
            val zy = y(0L)
            drawLine(grid, Offset(0f, zy), Offset(w, zy), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }

        val pts = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
        val line = smoothPath(pts)
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, bottom)
            lineTo(pts.first().x, bottom)
            close()
        }
        val split = forecastFromIndex?.coerceIn(0, values.lastIndex)
        val splitX = split?.let { x(it) } ?: w

        clipRect(right = w * reveal.value) {
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = bottom))
            clipRect(right = splitX) {
                drawPath(line, lineColor, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (split != null) {
                clipRect(left = splitX) {
                    drawPath(line, lineColor.copy(alpha = 0.8f), style = Stroke(4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                }
            }
        }

        selectedIndex?.takeIf { it in values.indices }?.let { i ->
            val p = pts[i]
            drawLine(lineColor.copy(alpha = 0.5f), Offset(p.x, top), Offset(p.x, bottom), 2f)
            drawCircle(surface, 11f, p)
            drawCircle(lineColor, 7f, p)
        } ?: run {
            drawCircle(lineColor, 7f, pts.last())
        }
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int? {
    if (count < 2 || width <= 0f) return null
    return ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/** Monotone-ish smoothing using mid-point quadratic curves. */
private fun smoothPath(p: List<Offset>): Path = Path().apply {
    moveTo(p.first().x, p.first().y)
    if (p.size == 2) { lineTo(p[1].x, p[1].y); return@apply }
    for (i in 1 until p.size - 1) {
        val mid = Offset((p[i].x + p[i + 1].x) / 2f, (p[i].y + p[i + 1].y) / 2f)
        quadraticTo(p[i].x, p[i].y, mid.x, mid.y)
    }
    lineTo(p.last().x, p.last().y)
}
