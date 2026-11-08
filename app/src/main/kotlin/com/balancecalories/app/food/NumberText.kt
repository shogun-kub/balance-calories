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
