package com.dreamboard.app.feature.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.designsystem.component.DateInput
import com.dreamboard.app.core.designsystem.component.EditorScaffold
import com.dreamboard.app.core.designsystem.component.MoneyInput
import com.dreamboard.app.core.designsystem.component.Overline
import com.dreamboard.app.core.designsystem.component.PickerInput
import com.dreamboard.app.core.designsystem.component.SecondaryAction
import com.dreamboard.app.core.designsystem.component.TextInput
import com.dreamboard.app.core.designsystem.component.formatMoney
import com.dreamboard.app.core.designsystem.component.moneyToInput
import com.dreamboard.app.core.designsystem.component.parseMoneyInput
import com.dreamboard.app.core.designsystem.theme.DbTheme
import com.dreamboard.app.domain.model.Account
import com.dreamboard.app.domain.model.BudgetCategory
import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.TransactionSource
import com.dreamboard.app.domain.model.TransactionSplit
import com.dreamboard.app.domain.model.TransactionType
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class SplitLine(categoryId: String, amount: String) {
    var categoryId by mutableStateOf(categoryId)
    var amount by mutableStateOf(amount)
}

private val NO_CATEGORY = BudgetCategory("", "Uncategorised", 0)

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val existing: Transaction? = savedStateHandle.get<String>("txId")?.let { id -> repo.transactions.value.firstOrNull { it.id == id } }
    val isNew = existing == null
    val accounts: List<Account> get() = repo.accounts.value
    val categories: List<BudgetCategory> get() = listOf(NO_CATEGORY) + repo.budgetCategories.value

    var date by mutableStateOf(existing?.date ?: LocalDate.now())
    var accountId by mutableStateOf(existing?.accountId ?: repo.accounts.value.firstOrNull()?.id ?: "")
    var type by mutableStateOf(existing?.type ?: TransactionType.EXPENSE)
    var amount by mutableStateOf(moneyToInput(existing?.amountMinor))
    var merchant by mutableStateOf(existing?.merchant ?: "")
    var categoryId by mutableStateOf(existing?.categoryId ?: "")
    var card by mutableStateOf(existing?.paidByCreditCard ?: false)
    var note by mutableStateOf(existing?.note ?: "")
    val splits = mutableStateListOf<SplitLine>().apply { existing?.splits?.forEach { add(SplitLine(it.categoryId, moneyToInput(it.amountMinor))) } }
    var useSplits by mutableStateOf(existing?.splits?.isNotEmpty() == true)

    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    val usesCategory: Boolean get() = type.countsAsSpending || type == TransactionType.REFUND

    val splitRemainderMinor: Long?
        get() {
            val total = parseMoneyInput(amount) ?: return null
            return total - splits.sumOf { parseMoneyInput(it.amount) ?: 0L }
        }

    val canSave: Boolean
        get() {
            val a = parseMoneyInput(amount) ?: return false
            if (a <= 0 || merchant.isBlank() || accountId.isBlank()) return false
            if (useSplits && usesCategory) {
                if (splits.size < 2 || splits.any { it.categoryId.isBlank() || parseMoneyInput(it.amount) == null }) return false
                if (splitRemainderMinor != 0L) return false
            }
            return true
        }

    fun addSplit() { splits.add(SplitLine(categories.drop(1).firstOrNull()?.id ?: "", "")) }

    fun save() = viewModelScope.launch {
        if (!canSave) return@launch
        val withSplits = useSplits && usesCategory
        repo.saveTransaction(
            Transaction(
                id = existing?.id ?: UUID.randomUUID().toString(), date = date, merchant = merchant,
                amountMinor = parseMoneyInput(amount) ?: 0L, type = type,
                categoryId = if (!usesCategory || withSplits) null else categoryId.ifBlank { null },
                account = "", source = existing?.source ?: TransactionSource.MANUAL, note = note,
                paidByCreditCard = card && type == TransactionType.EXPENSE, accountId = accountId,
                splits = if (withSplits) splits.map { TransactionSplit(it.categoryId, parseMoneyInput(it.amount) ?: 0L) } else emptyList(),
                importBatchId = existing?.importBatchId,
            ),
        )
        _done.value = true
    }

    fun delete() = viewModelScope.launch { existing?.let { repo.deleteTransaction(it.id) }; _done.value = true }
}

@Composable
fun TransactionEditorScreen(onBack: () -> Unit, viewModel: TransactionEditorViewModel = hiltViewModel()) {
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    val vm = viewModel
    val accounts = vm.accounts
    val categories = vm.categories
    EditorScaffold(
        title = if (vm.isNew) "Add transaction" else "Edit transaction", overline = "Budget & Expenses", onBack = onBack,
        canSave = vm.canSave, onSave = { vm.save() }, onDelete = if (vm.isNew) null else ({ vm.delete() }),
    ) {
        PickerInput(vm.type, TransactionType.entries, "Type", { it.label }, { vm.type = it })
        Text(typeHelp(vm.type), style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
        DateInput(vm.date, { vm.date = it }, "Date")
        if (accounts.isEmpty()) {
            Text("Add an account in Settings › Accounts first.", color = DbTheme.colors.negative)
        } else {
            PickerInput(accounts.firstOrNull { it.id == vm.accountId } ?: accounts.first(), accounts, "Account", { it.name }, { vm.accountId = it.id })
        }
        MoneyInput(vm.amount, { vm.amount = it }, "Amount")
        TextInput(vm.merchant, { vm.merchant = it }, "Merchant or description")
        if (vm.usesCategory) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split across categories", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(vm.useSplits, {
                    vm.useSplits = it
                    if (it && vm.splits.isEmpty()) { vm.addSplit(); vm.addSplit() }
                }, colors = SwitchDefaults.colors(checkedTrackColor = DbTheme.colors.gold))
            }
            if (!vm.useSplits) {
                PickerInput(categories.firstOrNull { it.id == vm.categoryId } ?: NO_CATEGORY, categories, "Category", { it.name }, { vm.categoryId = it.id })
            } else {
                vm.splits.forEachIndexed { i, line ->
                    Overline("Part ${i + 1}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            PickerInput(categories.firstOrNull { it.id == line.categoryId } ?: NO_CATEGORY, categories.drop(1), "Category", { it.name }, { line.categoryId = it.id })
                            MoneyInput(line.amount, { line.amount = it }, "Amount")
                        }
                        if (vm.splits.size > 2) {
                            IconButton(onClick = { vm.splits.removeAt(i) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove part") }
                        }
                    }
                }
                SecondaryAction("Add another part", { vm.addSplit() }, Modifier.fillMaxWidth())
                vm.splitRemainderMinor?.let { r ->
                    Text(
                        if (r == 0L) "Parts add up to the total." else "Parts must add up to the total. Difference: ${formatMoney(r, showSign = true)}",
                        style = MaterialTheme.typography.bodySmall, color = if (r == 0L) DbTheme.colors.positive else DbTheme.colors.negative,
                    )
                }
            }
        }
        if (vm.type == TransactionType.EXPENSE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Paid with a credit card", style = MaterialTheme.typography.titleSmall)
                    Text("Counts as spending now; the card bill payment later won't count again.", style = MaterialTheme.typography.bodySmall, color = DbTheme.colors.textMuted)
                }
                Spacer(Modifier.width(8.dp))
                Switch(vm.card, { vm.card = it }, colors = SwitchDefaults.colors(checkedTrackColor = DbTheme.colors.gold))
            }
        }
        TextInput(vm.note, { vm.note = it }, "Note (optional)", singleLine = false)
    }
}

private fun typeHelp(t: TransactionType): String = when (t) {
    TransactionType.INCOME -> "Money earned, such as salary or an Ignite distribution actually received."
    TransactionType.EXPENSE -> "Counts against your budget."
    TransactionType.TRANSFER -> "Between your own accounts. Never income or spending."
    TransactionType.CARD_PAYMENT -> "Paying a card bill. Not spending: the purchases already counted."
    TransactionType.LOAN_PRINCIPAL -> "Reduces a loan. A cash commitment, not an expense. Record it on the debt too."
    TransactionType.INTEREST -> "Interest charged. Counts as an expense."
    TransactionType.INVESTMENT -> "Money moved into savings or investments, e.g. the Porsche fund."
    TransactionType.BUSINESS_CAPITAL -> "Money put into or taken out of Ignite as capital."
    TransactionType.REFUND -> "Money returned for a purchase. Reduces spending in its category."
    TransactionType.REIMBURSEMENT -> "Someone paying you back. Not income."
}
