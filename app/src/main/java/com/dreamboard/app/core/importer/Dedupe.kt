package com.dreamboard.app.core.importer

import com.dreamboard.app.domain.model.Transaction
import com.dreamboard.app.domain.model.TransactionType
import java.security.MessageDigest
import java.time.LocalDate

/** Money in (+) or out (−) of the account, from the transaction's type. */
fun Transaction.signedAmountMinor(): Long = when (type) {
    TransactionType.INCOME, TransactionType.REFUND, TransactionType.REIMBURSEMENT -> amountMinor
    else -> -amountMinor
}

object Dedupe {
    fun normalize(description: String): String =
        description.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Same account, same day, same signed amount and same normalised description = same transaction. */
    fun hash(accountId: String, date: LocalDate, signedAmountMinor: Long, description: String): String {
        val key = "$accountId|$date|$signedAmountMinor|${normalize(description)}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(t: Transaction): String = hash(t.accountId, t.date, t.signedAmountMinor(), t.merchant)
}
