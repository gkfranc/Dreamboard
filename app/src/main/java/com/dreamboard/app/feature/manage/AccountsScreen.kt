package com.dreamboard.app.feature.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.designsystem.component.DateInput
import com.dreamboard.app.core.designsystem.component.DbCard
import com.dreamboard.app.core.designsystem.component.MoneyInput
import com.dreamboard.app.core.designsystem.component.MoneyText
import com.dreamboard.app.core.designsystem.component.PickerInput
import com.dreamboard.app.core.designsystem.component.PrimaryAction
import com.dreamboard.app.core.designsystem.component.ScreenHeader
import com.dreamboard.app.core.designsystem.component.TextInput
import com.dreamboard.app.core.designsystem.component.moneyToInput
import com.dreamboard.app.core.designsystem.component.parseMoneyInput
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.core.importer.signedAmountMinor
import com.dreamboard.app.domain.model.Account
import com.dreamboard.app.domain.model.AccountKind
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class AccountLine(val account: Account, val balanceMinor: Long, val transactionCount: Int)

@HiltViewModel
class AccountsViewModel @Inject constructor(private val repo: FinanceRepository) : ViewModel() {
    val lines = combine(repo.accounts, repo.transactions) { accts, txs ->
        accts.map { a ->
            val mine = txs.filter { it.accountId == a.id && !it.date.isBefore(a.openingDate) }
            AccountLine(a, a.openingBalanceMinor + mine.sumOf { it.signedAmountMinor() }, txs.count { it.accountId == a.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun save(a: Account) = viewModelScope.launch { repo.saveAccount(a) }
    fun delete(a: Account) = viewModelScope.launch {
        if (!repo.deleteAccount(a.id)) _message.value = "${a.name} still has transactions, so it can't be deleted."
    }
    fun clearMessage() { _message.value = null }
}

@Composable
fun AccountsScreen(onBack: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Accounts", overline = "Where transactions happen", onBack = onBack, showSettings = false) }
        item {
            Text(
                "Accounts hold transactions (bank, M-Pesa, cards). Running balance = opening balance + transactions since the opening date. " +
                    "To count a balance in net worth, add it as an asset too.",
                style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted,
            )
        }
        item { PrimaryAction("Add account", { creating = true }, Modifier.fillMaxWidth()) }
        message?.let { m -> item { Text(m, color = DbTheme.colors.negative, style = MaterialTheme.typography.bodySmall) } }
        items(lines, key = { it.account.id }) { l ->
            DbCard(onClick = { editing = l.account }, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.account.name, style = MaterialTheme.typography.titleSmall)
                        Text("${l.account.kind.label} · ${l.transactionCount} transactions", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                    }
                    MoneyText(l.balanceMinor, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (creating || editing != null) {
        AccountDialog(
            existing = editing,
            onDismiss = { creating = false; editing = null; viewModel.clearMessage() },
            onSave = { viewModel.save(it); creating = false; editing = null },
            onDelete = editing?.let { a -> { viewModel.delete(a); editing = null } },
        )
    }
}

@Composable
private fun AccountDialog(existing: Account?, onDismiss: () -> Unit, onSave: (Account) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: AccountKind.BANK) }
    var institution by remember { mutableStateOf(existing?.institution ?: "") }
    var opening by remember { mutableStateOf(moneyToInput(existing?.openingBalanceMinor ?: 0L)) }
    var openingDate by remember { mutableStateOf(existing?.openingDate ?: LocalDate.now()) }
    val valid = name.isNotBlank() && parseMoneyInput(opening) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add account" else "Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, "Name")
                PickerInput(kind, AccountKind.entries, "Type", { it.label }, { kind = it })
                TextInput(institution, { institution = it }, "Institution (optional)")
                MoneyInput(opening, { opening = it }, "Opening balance")
                DateInput(openingDate, { openingDate = it }, "Opening balance date")
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(Account(existing?.id ?: UUID.randomUUID().toString(), name, kind, institution, parseMoneyInput(opening) ?: 0L, openingDate))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = DbTheme.colors.negative) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
