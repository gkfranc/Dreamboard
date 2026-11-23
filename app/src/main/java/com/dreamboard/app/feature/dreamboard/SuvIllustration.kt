package com.dreamboard.app.feature.dreamboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dreamboard.app.core.designsystem.theme.DbTheme

/**
 * Original line-art illustration of a generic luxury SUV, drawn in code.
 * No manufacturer imagery or trademarks are used; a user-supplied photo can replace it later.
 */
@Composable
fun SuvIllustration(modifier: Modifier = Modifier) {
    val gold = DbTheme.colors.gold
    val muted = DbTheme.colors.textMuted
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .semantics { contentDescription = "Illustration of a luxury SUV" },
    ) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)

        // Soft floor glow
        drawOval(
            Brush.radialGradient(listOf(gold.copy(alpha = 0.18f), Color.Transparent), center = p(0.5f, 0.86f), radius = w * 0.45f),
            topLeft = p(0.05f, 0.74f), size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.24f),
        )
        drawLine(muted.copy(alpha = 0.35f), p(0.04f, 0.86f), p(0.96f, 0.86f), 2f)

        val body = Path().apply {
            moveTo(w * 0.07f, h * 0.74f)
            lineTo(w * 0.07f, h * 0.60f)
            cubicTo(w * 0.08f, h * 0.53f, w * 0.12f, h * 0.50f, w * 0.20f, h * 0.48f)
            lineTo(w * 0.34f, h * 0.44f)
            cubicTo(w * 0.39f, h * 0.34f, w * 0.44f, h * 0.24f, w * 0.52f, h * 0.22f)
            lineTo(w * 0.76f, h * 0.22f)
            cubicTo(w * 0.84f, h * 0.23f, w * 0.89f, h * 0.33f, w * 0.92f, h * 0.44f)
            cubicTo(w * 0.94f, h * 0.50f, w * 0.95f, h * 0.56f, w * 0.95f, h * 0.62f)
            lineTo(w * 0.95f, h * 0.74f)
            lineTo(w * 0.85f, h * 0.74f)
            cubicTo(w * 0.85f, h * 0.60f, w * 0.71f, h * 0.60f, w * 0.71f, h * 0.74f)
            lineTo(w * 0.33f, h * 0.74f)
            cubicTo(w * 0.33f, h * 0.60f, w * 0.19f, h * 0.60f, w * 0.19f, h * 0.74f)
            close()
        }
        drawPath(body, Brush.verticalGradient(listOf(gold.copy(alpha = 0.16f), gold.copy(alpha = 0.02f)), startY = h * 0.2f, endY = h * 0.75f))
        drawPath(body, gold, style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Glasshouse
        val glass = Path().apply {
            moveTo(w * 0.38f, h * 0.44f)
            cubicTo(w * 0.42f, h * 0.36f, w * 0.46f, h * 0.29f, w * 0.53f, h * 0.28f)
            lineTo(w * 0.75f, h * 0.28f)
            cubicTo(w * 0.81f, h * 0.29f, w * 0.85f, h * 0.36f, w * 0.87f, h * 0.44f)
            close()
        }
        drawPath(glass, muted.copy(alpha = 0.10f))
        drawPath(glass, muted.copy(alpha = 0.7f), style = Stroke(2f, join = StrokeJoin.Round))
        drawLine(muted.copy(alpha = 0.7f), p(0.62f, 0.28f), p(0.62f, 0.44f), 2f)
        // Shoulder line, lamps
        drawLine(gold.copy(alpha = 0.6f), p(0.20f, 0.53f), p(0.93f, 0.50f), 1.6f)
        drawLine(gold, p(0.085f, 0.55f), p(0.15f, 0.53f), 4f, cap = StrokeCap.Round)
        drawLine(Color(0xFFE9A09B), p(0.925f, 0.47f), p(0.945f, 0.52f), 4f, cap = StrokeCap.Round)

        // Wheels
        listOf(0.26f, 0.78f).forEach { cx ->
            val c = p(cx, 0.74f)
            drawCircle(Color.Black.copy(alpha = 0.35f), h * 0.115f, c)
            drawCircle(gold, h * 0.115f, c, style = Stroke(3.5f))
            drawCircle(muted, h * 0.06f, c, style = Stroke(2f))
            drawCircle(gold, h * 0.015f, c)
        }
    }
}
