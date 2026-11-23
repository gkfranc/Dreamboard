package com.dreamboard.app.core.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2

data class DonutSlice(val value: Long, val color: Color)

/** Animated donut; tap a slice to select it. Centre content is supplied by the caller. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    thickness: Dp = 18.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val sweep = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(size)
                .pointerInput(slices) {
                    detectTapGestures { o ->
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        var deg = Math.toDegrees(atan2((o.y - c.y).toDouble(), (o.x - c.x).toDouble())).toFloat() + 90f
                        if (deg < 0) deg += 360f
                        var acc = 0f
                        var hit: Int? = null
                        slices.forEachIndexed { i, s ->
                            val a = 360f * s.value / total
                            if (hit == null && deg >= acc && deg < acc + a) hit = i
                            acc += a
                        }
                        onSelect(if (hit == selectedIndex) null else hit)
                    }
                },
        ) {
            val t = thickness.toPx()
            val arc = Size(this.size.width - t, this.size.height - t)
            val tl = Offset(t / 2, t / 2)
            var start = -90f
            val gap = if (slices.size > 1) 1.6f else 0f
            slices.forEachIndexed { i, s ->
                val a = 360f * s.value / total * sweep.value
                val selected = i == selectedIndex
                val alpha = if (selectedIndex == null || selected) 1f else 0.35f
                if (a > gap) {
                    drawArc(s.color.copy(alpha = alpha), start + gap / 2, a - gap, false, tl, arc, style = Stroke(if (selected) t * 1.25f else t))
                }
                start += a
            }
        }
        center()
    }
}
