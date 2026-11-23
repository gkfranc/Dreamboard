package com.dreamboard.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.theme.DreamboardTheme
import com.dreamboard.app.data.prefs.ThemePreference
import com.dreamboard.app.data.prefs.UserPreferences
import com.dreamboard.app.data.prefs.UserPreferencesRepository
import com.dreamboard.app.navigation.DreamboardAppUi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Secure by default until preferences load; the user can turn it off in Settings.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val prefs by preferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val scope = rememberCoroutineScope()

            LaunchedEffect(prefs.screenshotProtection) {
                if (prefs.screenshotProtection) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val dark = when (prefs.theme) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                this@MainActivity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DreamboardTheme(darkTheme = dark) {
                DreamboardAppUi(
                    preferences = prefs,
                    onTogglePrivacy = {
                        scope.launch { preferencesRepository.setPrivacyMode(!prefs.privacyMode) }
                    },
                )
            }
        }
    }
}
