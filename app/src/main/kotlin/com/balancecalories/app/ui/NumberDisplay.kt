package com.balancecalories.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

/**
 * Formats a measured amount for **reading**, in the phone's locale: `22,5` on a Russian phone, `22.5` on an
 * English one, and no trailing zeros — `20`, not `20,0`.
 *
 * [com.balancecalories.app.food.trimNumber] is the opposite job: a value placed into an *editable field*,
 * where a comma would have to be parsed back and a locale-dependent separator is a bug. Displaying a dot
 * there is a bug too — the first device build of M7.7 printed "Белки 1.1 · Жиры 0.3" on the line under
 * "100 г · 187 ккал", two number conventions in two lines of the same row (Honor X7c, 2026-11-11).
 */
internal fun displayNumber(value: Double, decimals: Int = 1): String {
    if (!value.isFinite()) return ""
    val rounded = BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
    // stripTrailingZeros turns 20.0 into 2E+1; a negative scale is not something to hand to a formatter.
    val plain = if (rounded.scale() < 0) rounded.setScale(0) else rounded
    return NumberFormat.getNumberInstance().format(plain)
}
