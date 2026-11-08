package com.balancecalories.core.food

/**
 * The arithmetic of a cooked dish: raw components in, per-100 g and per-serving out.
 *
 * Two rules run through everything here, and both exist because the alternative is a confident wrong number:
 *
 * 1. **A missing value stays missing.** If one ingredient has no calorie data, the dish total is *unknown*,
 *    not "the sum of the others". Summing what happens to be known would silently understate the plate by
 *    however much the unknown ingredient contributes, and nothing on screen would hint at it.
 * 2. **The weight basis is stated, never assumed.** Energy is conserved through cooking; weight is not. A
 *    per-100 g value is only meaningful against the weight the food actually has when it is served, so
 *    [WeightBasis] travels with the totals and [WeightBasis.usedRawWeight] marks the fallback.
 *
 * Every portion below is expressed in **served grams** — the weight of the food on the plate — while
 * [Totals.rawGrams] stays the weight that went into the pan. Confusing the two is the classic way a cooked
 * dish ends up with calories that do not match what the scale says.
 */
object RecipeMath {

    /** Which weight the per-100 g figures are computed against, and whether that weight was measured. */
    enum class BasisKind {
        /** The user weighed the finished dish. The honest case. */
        COOKED,

        /** No cooked weight recorded, so the raw component sum was used. Wrong for anything that loses water. */
        RAW_FALLBACK,
    }

    data class WeightBasis(
        val grams: Double,
        val kind: BasisKind,
    ) {
        val usedRawWeight: Boolean get() = kind == BasisKind.RAW_FALLBACK
    }

    /**
     * Totals for the whole dish, from its raw components.
     *
     * [rawGrams] is always known; every nutrition field is null when at least one ingredient lacks it.
     * [missingEnergyItems] counts the ingredients that made the energy total unknown, so the UI can name a
     * number instead of shrugging.
     */
    data class Totals(
        val rawGrams: Double,
        val kcal: Double?,
        val proteinG: Double?,
        val fatG: Double?,
        val carbsG: Double?,
        val fiberG: Double?,
        val missingEnergyItems: Int,
    ) {
        val hasEnergy: Boolean get() = kcal != null
    }

    fun totals(items: List<RecipeItem>): Totals {
        // A recipe with no ingredients is not a zero-calorie dish, it is not a dish. Reporting 0.0 here would
        // make an empty draft look loggable and would need a separate guard at every call site.
        if (items.isEmpty()) {
            return Totals(
                rawGrams = 0.0,
                kcal = null,
                proteinG = null,
                fatG = null,
                carbsG = null,
                fiberG = null,
                missingEnergyItems = 0,
            )
        }

        val rawGrams = items.sumOf { it.grams }

        /** Null as soon as one ingredient does not know the value: a partial sum is a wrong total. */
        fun sum(per100: (Product) -> Double?): Double? {
            var total = 0.0
            for (item in items) {
                val value = per100(item.product) ?: return null
                total += value * item.grams / 100.0
            }
            return total
        }

        return Totals(
            rawGrams = rawGrams,
            kcal = sum { it.kcal100g },
            proteinG = sum { it.protein100g },
            fatG = sum { it.fat100g },
            carbsG = sum { it.carbs100g },
            fiberG = sum { it.fiber100g },
            missingEnergyItems = items.count { it.product.kcal100g == null },
        )
    }

    /**
     * Which weight a portion is measured against.
     *
     * The cooked weight wins when it is a positive number; otherwise the raw sum is used and flagged. A cooked
     * weight of zero is treated as "not measured" rather than as a valid basis, because dividing by it would
     * produce an infinite calorie density.
     */
    fun weightBasis(totals: Totals, cookedWeightG: Double?): WeightBasis =
        if (cookedWeightG != null && cookedWeightG > 0.0) {
            WeightBasis(cookedWeightG, BasisKind.COOKED)
        } else {
            WeightBasis(totals.rawGrams, BasisKind.RAW_FALLBACK)
        }

    /** Per-100 g of the finished dish. Null when the dish has no weight at all. */
    fun per100g(totals: Totals, basis: WeightBasis): PortionNutrition? {
        if (basis.grams <= 0.0) return null
        return scaled(totals, factor = 100.0 / basis.grams, servedGrams = 100.0)
    }

    /**
     * The nutrition of [grams] of the finished dish, as served.
     *
     * Returns null when the dish has no measurable weight; the nutrition fields inside are null when the dish's
     * energy is unknown, which is a different state and is carried by [Totals.hasEnergy].
     */
    fun portion(totals: Totals, basis: WeightBasis, grams: Double): PortionNutrition? {
        require(grams > 0.0) { "a portion must be positive, was $grams" }
        if (basis.grams <= 0.0) return null
        return scaled(totals, factor = grams / basis.grams, servedGrams = grams)
    }

    /** One plate, when the pot is cut into [servings] equal parts. */
    fun perServing(totals: Totals, basis: WeightBasis, servings: Double): PortionNutrition? {
        if (basis.grams <= 0.0 || servings <= 0.0) return null
        val served = basis.grams / servings
        return scaled(totals, factor = 1.0 / servings, servedGrams = served)
    }

    /**
     * The kcal density to store on a logged entry: kcal per 100 g of the finished dish.
     *
     * Null when the dish has no energy, so the caller refuses to log instead of writing a zero.
     */
    fun kcalPer100g(totals: Totals, basis: WeightBasis): Double? = per100g(totals, basis)?.kcal

    private fun scaled(totals: Totals, factor: Double, servedGrams: Double): PortionNutrition =
        PortionNutrition(
            grams = servedGrams,
            kcal = totals.kcal?.times(factor),
            proteinG = totals.proteinG?.times(factor),
            fatG = totals.fatG?.times(factor),
            carbsG = totals.carbsG?.times(factor),
            fiberG = totals.fiberG?.times(factor),
            // The recipe model does not carry sugar or salt yet; null keeps "unknown" honest.
            sugarG = null,
            saltG = null,
        )
}
