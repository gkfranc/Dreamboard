package com.dreamboard.app.core.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dreamboard.app.core.designsystem.theme.DbTheme

/** One group on the x-axis: a primary bar, an optional comparison bar and optional target marker. */
data class BarGroup(
    val label: String,
    val primary: Long,
    val secondary: Long? = null,
    val target: Long? = null,
)

/**
 * Grouped vertical bars. Primary uses [primaryColor]; secondary (e.g. budget) is drawn as a muted
 * ghost bar behind; target is a dashed tick. Tap a group to select it.
 */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    primaryColor: Color = DbTheme.colors.gold,
    secondaryColor: Color = DbTheme.colors.textMuted.copy(alpha = 0.28f),
    overColor: Color? = null,
) {
    val grow = remember(groups) { Animatable(0f) }
    LaunchedEffect(groups) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val grid = DbTheme.colors.hairline
    val targetColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(groups) {
                    detectTapGestures { o ->
                        if (groups.isEmpty()) return@detectTapGestures
                        val i = (o.x / (size.width.toFloat() / groups.size)).toInt().coerceIn(0, groups.lastIndex)
                        onSelect(if (i == selectedIndex) null else i)
                    }
                },
        ) {
            if (groups.isEmpty()) return@Canvas
            val maxV = groups.maxOf { maxOf(it.primary, it.secondary ?: 0L, it.target ?: 0L) }.coerceAtLeast(1L)
            val slot = size.width / groups.size
            val barW = slot * 0.42f
            val r = CornerRadius(barW / 3f, barW / 3f)
            for (k in 1..3) {
                val gy = size.height * k / 4f
                drawLine(grid.copy(alpha = 0.5f), Offset(0f, gy), Offset(size.width, gy), 1f)
            }
            groups.forEachIndexed { i, g ->
                val cx = slot * i + slot / 2f
                val dim = selectedIndex != null && selectedIndex != i
                fun hFor(v: Long) = size.height * (v.toFloat() / maxV) * grow.value
                g.secondary?.let { s ->
                    val h = hFor(s)
                    drawRoundRect(secondaryColor, Offset(cx - barW / 2f - barW * 0.18f, size.height - h), Size(barW * 1.36f, h), r)
                }
                val over = overColor != null && g.secondary != null && g.primary > g.secondary
                val c = (if (over) overColor!! else primaryColor).copy(alpha = if (dim) 0.35f else 1f)
                val h = hFor(g.primary)
                drawRoundRect(c, Offset(cx - barW / 2f, size.height - h), Size(barW, h), r)
                g.target?.let { t ->
                    val ty = size.height - size.height * (t.toFloat() / maxV)
                    drawLine(targetColor, Offset(cx - barW, ty), Offset(cx + barW, ty), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            groups.forEachIndexed { i, g ->
                Text(
                    g.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == selectedIndex) DbTheme.colors.gold else DbTheme.colors.textMuted,
                )
            }
        }
    }
}

/** Horizontal stacked bar for composition (e.g. principal vs interest). */
@Composable
fun StackedBar(parts: List<Pair<Long, Color>>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1L)
    val grow = remember(parts) { Animatable(0f) }
    LaunchedEffect(parts) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        var x = 0f
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        parts.forEach { (v, c) ->
            val w = size.width * v / total * grow.value
            if (w > 0f) {
                drawRoundRect(c, Offset(x, 0f), Size((w - 3f).coerceAtLeast(1f), size.height), r)
            }
            x += w
        }
    }
}
