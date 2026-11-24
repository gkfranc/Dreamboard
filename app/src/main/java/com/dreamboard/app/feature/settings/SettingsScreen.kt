package com.dreamboard.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dreamboard.app.core.designsystem.component.ConfirmDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenAssets: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val p by viewModel.preferences.collectAsStateWithLifecycle()
    val batches by viewModel.importBatches.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmErase by remember { mutableStateOf(false) }
    var confirmExamples by remember { mutableStateOf(false) }
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
                NavRow(Icons.Outlined.AccountBalance, "Accounts", "Bank, M-Pesa, SACCO and card accounts with opening balances.", onOpenAccounts)
                NavRow(Icons.Outlined.AccountBalanceWallet, "Assets & net-worth snapshots", "Add or revalue assets; snapshots are saved weekly.", onOpenAssets)
                NavRow(Icons.Outlined.FileUpload, "Import spreadsheet", "CSV or Excel (.xlsx) from your phone or Google Drive, with duplicate checks.", onOpenImport)
                NavRow(Icons.Outlined.Receipt, "Load my example debts", "Adds the example debts from your brief, marked unverified, if you have no debts yet.") { confirmExamples = true }
                NavRow(Icons.Outlined.DeleteForever, "Erase all data", "Deletes every account, transaction, asset, debt and snapshot on this phone.", danger = true) { confirmErase = true }
                PlannedRow(Icons.Outlined.Backup, "Encrypted backup & restore", "Keystore-encrypted export to your device or Google Drive via the file picker.", "Phase 5")
            }
        }

        message?.let { m -> item { Text(m, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.positive) } }
        if (batches.isNotEmpty()) {
            item { SectionHeader("Import history") }
            item {
                DbCard(contentPadding = 8.dp) {
                    batches.take(10).forEach { b ->
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(b.fileName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                IMPORT_TIME.format(Instant.ofEpochMilli(b.importedAtMillis).atZone(ZoneId.systemDefault())) +
                                    " · ${b.importedCount} imported · ${b.duplicateCount} duplicates · ${b.errorCount} errors",
                                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                            )
                        }
                    }
                }
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
                        "Until then, keep your original spreadsheets: they can be imported again, and duplicates are skipped.",
                    style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
                )
            }
        }
        item {
            Text("Dreamboard 0.2 · Phase 2 · free and open-source components only",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
    if (confirmErase) {
        ConfirmDialog(
            "Erase all data?", "This permanently deletes everything stored in Dreamboard on this phone. It can't be undone.", "Erase",
            onConfirm = { viewModel.eraseAll(); confirmErase = false }, onDismiss = { confirmErase = false },
        )
    }
    if (confirmExamples) {
        ConfirmDialog(
            "Load example debts?", "Adds the debts from your brief as unverified starting values. Edit each one to match your statements. Nothing is added if you already have debts.", "Load",
            onConfirm = { viewModel.loadExampleDebts(); confirmExamples = false }, onDismiss = { confirmExamples = false },
        )
    }
}

private val IMPORT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
private fun NavRow(icon: ImageVector, title: String, body: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) DbTheme.colors.negative else DbTheme.colors.gold
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) tint else MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = DbTheme.colors.textMuted)
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
