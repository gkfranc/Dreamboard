package com.dreamboard.app.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Accounting classification. Only [countsAsSpending] types reduce the budget; transfers,
 * card bill payments and loan principal never count as spending, so nothing is double-counted.
 */
enum class TransactionType(val label: String, val countsAsSpending: Boolean, val countsAsIncome: Boolean) {
    INCOME("Income", false, true),
    EXPENSE("Expense", true, false),
    TRANSFER("Internal transfer", false, false),
    CARD_PAYMENT("Credit card bill payment", false, false),
    LOAN_PRINCIPAL("Loan principal repayment", false, false),
    INTEREST("Interest expense", true, false),
    INVESTMENT("Investment contribution", false, false),
    BUSINESS_CAPITAL("Business capital movement", false, false),
    REFUND("Refund", false, false),
    REIMBURSEMENT("Reimbursement", false, false),
}

enum class TransactionSource(val label: String) { DEMO("Demo data"), MANUAL("Manual"), CSV("CSV import"), XLSX("XLSX import") }

enum class AccountKind(val label: String) {
    BANK("Bank account"),
    MOBILE_MONEY("Mobile money"),
    SACCO("SACCO account"),
    CREDIT_CARD("Credit card"),
    CASH("Cash"),
    INVESTMENT("Investment account"),
    OTHER("Other"),
}

data class Account(
    val id: String,
    val name: String,
    val kind: AccountKind,
    val institution: String,
    val openingBalanceMinor: Long,
    val openingDate: LocalDate,
)

data class BudgetCategory(
    val id: String,
    val name: String,
    val monthlyBudgetMinor: Long,
)

/** Part of a transaction assigned to one category. Splits of a transaction sum to its amount. */
data class TransactionSplit(val categoryId: String, val amountMinor: Long)

data class Transaction(
    val id: String,
    val date: LocalDate,
    val merchant: String,
    /** Always positive; direction comes from [type]. */
    val amountMinor: Long,
    val type: TransactionType,
    val categoryId: String?,
    /** Display name of the account. */
    val account: String,
    val source: TransactionSource,
    val note: String = "",
    /** Purchase made on a credit card: counts as spending when incurred; the later bill payment is a CARD_PAYMENT. */
    val paidByCreditCard: Boolean = false,
    val accountId: String = "",
    val splits: List<TransactionSplit> = emptyList(),
    val importBatchId: String? = null,
) {
    /** Category allocation: the splits when present, otherwise the whole amount to [categoryId]. */
    fun allocations(): List<Pair<String?, Long>> =
        if (splits.isNotEmpty()) splits.map { it.categoryId to it.amountMinor } else listOf(categoryId to amountMinor)
}

data class MonthSummary(
    val month: YearMonth,
    val budgetMinor: Long,
    val spentMinor: Long,
    val incomeMinor: Long,
)

data class ImportBatch(
    val id: String,
    val fileName: String,
    val importedAtMillis: Long,
    val rowCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
    val errorReport: String,
)
