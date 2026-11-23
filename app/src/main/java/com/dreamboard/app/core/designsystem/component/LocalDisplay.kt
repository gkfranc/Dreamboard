package com.dreamboard.app.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class DisplayPrefs(val privacyMode: Boolean = false, val abbreviate: Boolean = false)

val LocalDisplayPrefs = compositionLocalOf { DisplayPrefs() }

/** App-wide actions reachable from any screen header. */
@Immutable
data class AppActions(
    val togglePrivacy: () -> Unit = {},
    val openSettings: () -> Unit = {},
)

val LocalAppActions = staticCompositionLocalOf { AppActions() }
