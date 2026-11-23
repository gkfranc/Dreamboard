package com.dreamboard.app.domain.model

import java.time.LocalDate

enum class DebtCategory(val label: String) {
    PERSONAL_LOAN("Personal bank loan"),
    SACCO_LOAN("SACCO loan"),
    CREDIT_CARD("Credit card"),
    SALARY_ADVANCE("Salary advance"),
    FAMILY_LOAN("Family obligation"),
    OTHER("Other obligation"),
}

/** How a liability behaves, which decides how it is projected. */
enum class DebtKind(val label: String) {
    REDUCING_BALANCE("Reducing-balance loan"),
    REVOLVING("Revolving credit (interest-bearing)"),
    STATEMENT_PAID_IN_FULL("Statement balance, settled in full monthly"),
    INTEREST_FREE("Interest-free obligation"),
}

data class DebtPayment(
    val date: LocalDate,
    val amountMinor: Long,
    val principalMinor: Long,
    val interestMinor: Long,
)

data class Debt(
    val id: String,
    val name: String,
    val lender: String,
    val category: DebtCategory,
    val kind: DebtKind,
    /** Null means the balance is unknown and has not been entered yet. */
    val balanceMinor: Long?,
    val originalPrincipalMinor: Long?,
    val annualRateBps: Int,
    val monthlyPaymentMinor: Long,
    val dueDayOfMonth: Int?,
    val interestPaidToDateMinor: Long?,
    val customPriority: Int,
    val notes: String,
    val payments: List<DebtPayment> = emptyList(),
) {
    val isKnown: Boolean get() = balanceMinor != null
    /** Interest-bearing balances that a repayment strategy should attack. */
    val isProjectable: Boolean
        get() = balanceMinor != null && balanceMinor > 0 &&
            kind != DebtKind.STATEMENT_PAID_IN_FULL
}
