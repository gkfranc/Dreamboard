package com.dreamboard.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All monetary values in Dreamboard are stored as [Long] minor units (cents).
 * KES 1,480,000.00 == 148_000_000L. Floating point is never used for balances.
 */
const val MINOR_PER_MAJOR = 100L

/** Converts whole shillings to minor units, failing loudly on overflow. */
val Long.kes: Long get() = Math.multiplyExact(this, MINOR_PER_MAJOR)
val Int.kes: Long get() = toLong().kes

/** Basis points: 10_000 bps == 100%. Used for rates, shares and percentages. */
const val BPS_SCALE = 10_000L

/** part / whole expressed in basis points, rounded half-up. Returns 0 when whole is 0. */
fun ratioBps(part: Long, whole: Long): Long {
    if (whole == 0L) return 0L
    return BigDecimal.valueOf(part)
        .multiply(BigDecimal.valueOf(BPS_SCALE))
        .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP)
        .toLong()
}

/** amount × bps / 10_000, rounded half-up to the nearest minor unit. */
fun applyBps(amountMinor: Long, bps: Long): Long =
    BigDecimal.valueOf(amountMinor)
        .multiply(BigDecimal.valueOf(bps))
        .divide(BigDecimal.valueOf(BPS_SCALE), 0, RoundingMode.HALF_UP)
        .toLong()

/** Rounds a minor amount up to the next whole shilling. */
fun ceilToWholeMajor(minor: Long): Long {
    val rem = Math.floorMod(minor, MINOR_PER_MAJOR)
    return if (rem == 0L) minor else minor - rem + MINOR_PER_MAJOR
}
