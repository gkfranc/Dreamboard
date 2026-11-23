package com.dreamboard.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.dreamboard.app.core.money.MoneyFormatter

/** Formats an amount respecting privacy mode and the abbreviation preference. */
@Composable
fun formatMoney(minor: Long, showSign: Boolean = false, forceAbbreviate: Boolean? = null): String {
    val prefs = LocalDisplayPrefs.current
    if (prefs.privacyMode) return MoneyFormatter.masked()
    return MoneyFormatter.format(minor, abbreviated = forceAbbreviate ?: prefs.abbreviate, showSign = showSign)
}

@Composable
fun MoneyText(
    minor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    showSign: Boolean = false,
    forceAbbreviate: Boolean? = null,
    animate: Boolean = false,
) {
    val text = formatMoney(minor, showSign, forceAbbreviate)
    val prefs = LocalDisplayPrefs.current
    val a11y = if (prefs.privacyMode) "Amount hidden" else text
    if (animate) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "money",
            modifier = modifier.semantics { contentDescription = a11y },
        ) { t -> Text(t, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else {
        Text(
            text, modifier = modifier.semantics { contentDescription = a11y },
            style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
