package com.balancecalories.core.energy

/**
 * Physical activity level (PAL) multipliers.
 *
 * These seed a total daily energy expenditure estimate **before** measured data exists
 * (docs/02-research.md §5.2). Once Health Connect has two weeks of band data, the adaptive
 * TDEE replaces this seed entirely, so the exact multiplier stops mattering.
 */
enum class ActivityLevel(val pal: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    HARD(1.725),
    ATHLETE(1.9),
}
