package com.balancecalories.core.food

/**
 * How much of the daily target a half-doughnut should fill, and with what.
 *
 * The gauge on Today answers one question at a glance — how much of today's allowance is gone — and the split of
 * that fill is the same macro breakdown the history chart draws. Keeping the fractions here rather than in the
 * composable means the clamping rules are testable, and there are three of them worth pinning:
 *
 * - **The arc never overflows.** Eating 2 400 kcal against a 1 900 target fills the arc completely and reports the
 *   excess separately; a sweep past 180° would wrap around and look like less than a full day.
 * - **A missing target fills nothing.** Without a target the gauge has no denominator, so it draws the track and
 *   says nothing rather than inventing a scale.
 * - **An empty day is empty, not full.** Zero eaten is zero sweep, and the caller decides to print "nothing
 *   recorded" instead of a number (an unlogged day is missing, not zero).
 */
data class GaugeFill(
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val other: Double,
    /** Fraction of the target that is used, 0..1. The four segments sum to this. */
    val used: Double,
    /** Energy beyond the target; zero when the day is inside it. */
    val overKcal: Double,
) {
    val isEmpty: Boolean get() = used <= 0.0
}

object CalorieGauge {

    val EMPTY = GaugeFill(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    /**
     * Fills the arc for [eatenKcal] against [targetKcal], split by [split].
     *
     * The split's own total is the eaten energy (see [MacroEnergy.split]), so each segment's share of the arc is its
     * share of the day, scaled by how much of the target is used.
     */
    fun of(eatenKcal: Double, targetKcal: Double, split: MacroSplit): GaugeFill {
        if (targetKcal <= 0.0 || eatenKcal <= 0.0) return EMPTY

        val used = (eatenKcal / targetKcal).coerceIn(0.0, 1.0)
        val over = (eatenKcal - targetKcal).coerceAtLeast(0.0)
        val share = used / eatenKcal

        return GaugeFill(
            protein = split.proteinKcal * share,
            fat = split.fatKcal * share,
            carbs = split.carbsKcal * share,
            other = split.otherKcal * share,
            used = used,
            overKcal = over,
        )
    }
}
