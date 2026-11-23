package com.dreamboard.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyFormatter {
    const val DEFAULT_CURRENCY = "KES"
    private const val MINUS = "−"
    const val MASK = "••••••"

    /**
     * Formats minor units, e.g. 148_000_000 -> "KES 1,480,000".
     * @param abbreviated "KES 3.45M" style for large values.
     * @param showSign prefix "+" for positive values (useful for changes).
     * @param showCents include ".00" style cents.
     */
    fun format(
        minor: Long,
        currency: String = DEFAULT_CURRENCY,
        abbreviated: Boolean = false,
        showSign: Boolean = false,
        showCents: Boolean = false,
    ): String {
        val sign = when {
            minor < 0 -> MINUS
            showSign && minor > 0 -> "+"
            else -> ""
        }
        val body = if (abbreviated) abbreviate(absolute(minor)) else full(absolute(minor), showCents)
        return if (currency.isEmpty()) "$sign$body" else "$sign$currency $body"
    }

    fun masked(currency: String = DEFAULT_CURRENCY): String = "$currency $MASK"

    /** Percent from basis points: 1234 -> "12.3%". */
    fun percent(bps: Long, decimals: Int = 1, showSign: Boolean = false): String {
        val value = BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(100), decimals, RoundingMode.HALF_UP)
        val sign = if (showSign && bps > 0) "+" else if (bps < 0) MINUS else ""
        return "$sign${value.abs().toPlainString()}%"
    }

    private fun absolute(minor: Long): BigDecimal = BigDecimal.valueOf(minor).abs()

    private fun full(absMinor: BigDecimal, showCents: Boolean): String {
        val major = absMinor.movePointLeft(2)
        return if (showCents) {
            val scaled = major.setScale(2, RoundingMode.HALF_UP)
            val whole = scaled.toBigInteger().toString()
            val cents = scaled.remainder(BigDecimal.ONE).movePointRight(2).toBigInteger().toString().padStart(2, '0')
            "${group(whole)}.$cents"
        } else {
            group(major.setScale(0, RoundingMode.HALF_UP).toPlainString())
        }
    }

    private fun abbreviate(absMinor: BigDecimal): String {
        val major = absMinor.movePointLeft(2)
        val (divisor, suffix, scale) = when {
            major >= BigDecimal("1000000000") -> Triple(BigDecimal("1000000000"), "B", 2)
            major >= BigDecimal("1000000") -> Triple(BigDecimal("1000000"), "M", 2)
            major >= BigDecimal("1000") -> Triple(BigDecimal("1000"), "K", 1)
            else -> return group(major.setScale(0, RoundingMode.HALF_UP).toPlainString())
        }
        val v = major.divide(divisor, scale, RoundingMode.HALF_UP).stripTrailingZeros()
        return v.toPlainString() + suffix
    }

    private fun group(digits: String): String {
        val sb = StringBuilder()
        val n = digits.length
        digits.forEachIndexed { i, c ->
            if (i > 0 && (n - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.toString()
    }
}
