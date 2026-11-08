package com.balancecalories.core.food

/**
 * Turns logged macros into the energy each one stands for, for the intake bar of the history chart.
 *
 * The bar's height is the day's **logged energy** — the same number Today prints — and its segments are the
 * macros' share of it. Those two numbers do not have to agree: a product can state an energy that differs from
 * its Atwater sum (alcohol, sugar alcohols, fibre counted in carbs, or a rounding in the label), and a recipe can
 * carry an estimate. So the split has three possible shapes, and the honest one is chosen rather than the tidy one:
 *
 * - macros account for **less** than the logged energy → the remainder is an `other` segment, drawn in a neutral
 *   colour. Energy nobody labelled is still energy that was eaten, and hiding it would make the segments add up
 *   to a bar that came from somewhere else.
 * - macros account for **more** than the logged energy (a stated lower energy, e.g. a diet product) → the
 *   segments are scaled down to fit the bar, because the bar's height is what the day reports and a chart cannot
 *   be taller than its own number.
 * - a leftover under [NEGLIGIBLE_KCAL] is dropped, so a rounding does not draw a one-pixel sliver that looks
 *   like a category.
 *
 * Atwater factors (4/9/4 kcal per gram) are the ones the app already applies when a product states no energy;
 * using a different set here would print two different answers for one product.
 */
object MacroEnergy {

    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_FAT = 9.0
    const val KCAL_PER_G_CARBS = 4.0

    /** Below this a segment is not drawn: a rounding must not look like a food group. */
    const val NEGLIGIBLE_KCAL = 1.0

    /**
     * The energy a day's macros stand for, before any adjustment to the logged total.
     *
     * Missing macros count as zero — a product without a protein figure is not a product with no protein, but
     * there is nothing to draw either, and the `other` segment is what keeps that visible.
     */
    fun atwaterKcal(proteinG: Double, fatG: Double, carbsG: Double): Double =
        proteinG.coerceAtLeast(0.0) * KCAL_PER_G_PROTEIN +
            fatG.coerceAtLeast(0.0) * KCAL_PER_G_FAT +
            carbsG.coerceAtLeast(0.0) * KCAL_PER_G_CARBS

    /**
     * Segments of the intake bar, in drawing order: protein, fat, carbs, other.
     *
     * [eatenKcal] is the day's logged energy; when it is not positive there is nothing to draw and every
     * segment is zero (a day with no log draws no bar at all, which is the caller's decision, not this one's).
     */
    fun split(
        proteinG: Double,
        fatG: Double,
        carbsG: Double,
        eatenKcal: Double,
    ): MacroSplit {
        if (eatenKcal <= 0.0) return MacroSplit(0.0, 0.0, 0.0, 0.0)

        val protein = proteinG.coerceAtLeast(0.0) * KCAL_PER_G_PROTEIN
        val fat = fatG.coerceAtLeast(0.0) * KCAL_PER_G_FAT
        val carbs = carbsG.coerceAtLeast(0.0) * KCAL_PER_G_CARBS
        val macros = protein + fat + carbs

        // No macro data at all: the whole bar is unlabelled energy rather than three empty segments.
        if (macros <= 0.0) return MacroSplit(0.0, 0.0, 0.0, eatenKcal)

        if (macros <= eatenKcal) {
            val other = eatenKcal - macros
            return if (other <= NEGLIGIBLE_KCAL) {
                // Absorb a rounding into fat rather than drawing a sliver: fat is the largest Atwater factor, so
                // a gram of it is the closest thing to "energy that probably belongs to something already here".
                MacroSplit(protein, fat + other, carbs, 0.0)
            } else {
                MacroSplit(protein, fat, carbs, other)
            }
        }

        // Macros claim more energy than the day reports: shrink them to the bar that exists.
        val factor = eatenKcal / macros
        return MacroSplit(protein * factor, fat * factor, carbs * factor, 0.0)
    }
}

/**
 * The four segments of one day's intake bar, in kcal, in the order they are drawn.
 *
 * They always sum to the day's logged energy (see [MacroEnergy.split]), which is the property the chart relies
 * on: a stacked bar whose parts do not add up to its height is a lie drawn in colour.
 */
data class MacroSplit(
    val proteinKcal: Double,
    val fatKcal: Double,
    val carbsKcal: Double,
    val otherKcal: Double,
) {
    val totalKcal: Double get() = proteinKcal + fatKcal + carbsKcal + otherKcal

    /** True when nothing was labelled at all, so the legend can say so instead of showing three zeros. */
    val isUnlabelled: Boolean
        get() = proteinKcal == 0.0 && fatKcal == 0.0 && carbsKcal == 0.0 && otherKcal > 0.0
}
