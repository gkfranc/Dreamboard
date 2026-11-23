package com.dreamboard.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.money.MoneyFormatter

/** The base surface: rounded, hairline border, subtle elevation. */
@Composable
fun DbCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, DbTheme.colors.hairline),
        shadowElevation = if (DbTheme.colors.isDark) 0.dp else 1.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Small uppercase overline used for card labels. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = DbTheme.colors.textMuted) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun KpiCard(
    label: String,
    amountMinor: Long,
    modifier: Modifier = Modifier,
    caption: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    DbCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp) {
        Overline(label)
        Spacer(Modifier.height(8.dp))
        MoneyText(amountMinor, style = MaterialTheme.typography.titleLarge, color = amountColor, animate = true)
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** KPI with a free-text value, for counts, days and percentages. */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    DbCard(modifier = modifier, contentPadding = 16.dp) {
        Overline(label)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, maxLines = 2)
        }
    }
}

/** Two cards side by side with equal weight. */
@Composable
fun CardRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        content(Modifier.weight(1f))
    }
}

/** Change pill: emerald up, coral down, with arrow and text. */
@Composable
fun ChangePill(changeMinor: Long, percentBps: Long?, modifier: Modifier = Modifier) {
    val positive = changeMinor >= 0
    val color = if (positive) DbTheme.colors.positive else DbTheme.colors.negative
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (positive) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
            contentDescription = if (positive) "Increase" else "Decrease",
            tint = color, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        val pct = percentBps?.let { " (${MoneyFormatter.percent(it, showSign = true)})" } ?: ""
        Text(formatMoney(changeMinor, showSign = true) + pct, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Label/value line used in detail lists. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DbTheme.colors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}

@Composable
fun MoneyInfoRow(label: String, minor: Long?, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface, emptyText: String = "Not entered") {
    InfoRow(label, if (minor == null) emptyText else formatMoney(minor), modifier, valueColor)
}
