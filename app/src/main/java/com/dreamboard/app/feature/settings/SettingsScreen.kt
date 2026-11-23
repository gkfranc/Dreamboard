package com.dreamboard.app.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamboard.app.core.designsystem.component.ChoiceChips
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.InfoRow
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.SectionHeader
import com.dreamboard.app.core.designsystem.component.Tag
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.data.prefs.ThemePreference

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val p by viewModel.preferences.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Settings", overline = "Privacy & preferences", onBack = onBack, showSettings = false) }

        item { SectionHeader("Privacy") }
        item {
            DbCard(contentPadding = 8.dp) {
                ToggleRow(Icons.Outlined.VisibilityOff, "Privacy mode", "Hide every amount behind dots. Also on the eye icon in each header.", p.privacyMode, viewModel::setPrivacy)
                ToggleRow(Icons.Outlined.ScreenLockPortrait, "Screenshot protection", "Block screenshots and blank the app in recent apps.", p.screenshotProtection, viewModel::setScreenshotProtection)
                PlannedRow(Icons.Outlined.Fingerprint, "Biometric app lock", "Fingerprint or face unlock via Android BiometricPrompt.", "Phase 5")
            }
        }

        item { SectionHeader("Display") }
        item {
            DbCard(contentPadding = 8.dp) {
                ToggleRow(Icons.Outlined.Numbers, "Abbreviate large amounts", "Show KES 3.45M instead of KES 3,450,000.", p.abbreviateAmounts, viewModel::setAbbreviate)
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = DbTheme.colors.gold)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        ChoiceChips(ThemePreference.entries, p.theme, { it.label }, viewModel::setTheme, Modifier.horizontalScroll(rememberScrollState()))
                    }
                }
                InfoRow("Currency", "Kenyan Shilling (KES)", Modifier.padding(horizontal = 12.dp))
            }
        }

        item { SectionHeader("Data") }
        item {
            DbCard(contentPadding = 8.dp) {
                PlannedRow(Icons.Outlined.FileUpload, "Import statements", "CSV, XLSX and M-Pesa statements via the system file picker.", "Phase 5")
                PlannedRow(Icons.Outlined.Backup, "Encrypted backup & restore", "Keystore-encrypted export to your device or Google Drive via the file picker.", "Phase 5")
            }
        }

        item { SectionHeader("How your data is protected") }
        item {
            DbCard {
                Text(
                    "Dreamboard is offline-first. It has no account, no ads, no analytics and does not request internet access. " +
                        "Your data never leaves this phone unless you export it yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "The trade-off: device-only storage means a lost, reset or broken phone takes the data with it. " +
                        "Android auto-backup is deliberately disabled for privacy, so encrypted manual backups (Phase 5) will be essential. " +
                        "Until then, Phase 1 shows demonstration data only.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                )
            }
        }
        item {
            Text("Dreamboard 0.1 · Phase 1 · free and open-source components only",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DbTheme.colors.gold)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = DbTheme.colors.gold, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
        )
    }
}

/** A feature that is planned but not built yet: shown as information, never as a dead button. */
@Composable
private fun PlannedRow(icon: ImageVector, title: String, body: String, phase: String) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = DbTheme.colors.textMuted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = DbTheme.colors.textMuted)
            Text(body, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        Tag(phase)
    }
}
