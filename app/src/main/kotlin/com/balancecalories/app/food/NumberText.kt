package com.balancecalories.app.food

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats a per-100 g value for an editable text field: no exponent, no float noise, no trailing zeros.
 *
 * The reason this exists at all: OpenFoodFacts carries a number like `7.6899999999999995`, and handing that
 * to `Double.toString()` put exactly that into the edit field, where a person is expected to correct it.
 *
 * Three decimals is the ceiling because printed labels stop at three (salt is often `0.1` or `0.107`), and
 * rounding is `HALF_UP` so a displayed value does not look smaller than it is.
 */
internal fun trimNumber(value: Double): String {
    if (!value.isFinite()) return ""
    val rounded = BigDecimal(value).setScale(MAX_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros()
    // BigDecimal keeps a scale of 1 for whole numbers with stripTrailingZeros, which prints as "5E+1".
    return rounded.toPlainString()
}

/** Labels do not carry more precision than this; more digits are decoded float, not measured food. */
private const val MAX_DECIMALS = 3

/**
 * The portion a «−10 г» or «+10 г» tap lands on.
 *
 * Parsed with the same comma tolerance as every other field, never below [MIN_PORTION_G] — the repository
 * refuses a non-positive portion, and a button that produces a value the save button then rejects is a trap —
 * and formatted by [trimNumber], so the field never shows `160.00000000000003`.
 */
internal fun stepGrams(current: String, delta: Double): String {
    val value = current.replace(',', '.').toDoubleOrNull() ?: 0.0
    return trimNumber((value + delta).coerceAtLeast(MIN_PORTION_G))
}

/** A portion below a gram is not a portion. */
private const val MIN_PORTION_G = 1.0
