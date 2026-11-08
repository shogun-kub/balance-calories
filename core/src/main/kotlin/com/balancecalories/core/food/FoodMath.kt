package com.balancecalories.core.food

/**
 * Nutrition for an actual portion, not for 100 g.
 *
 * Scaling is linear because the app stores per-100 g values and the user types grams. It is the one piece
 * of arithmetic in the food flow that must never disagree between screens, so it lives here and only here.
 */
data class PortionNutrition(
    val grams: Double,
    val kcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbsG: Double?,
    val fiberG: Double?,
    val sugarG: Double?,
    val saltG: Double?,
)

object FoodMath {

    /**
     * Scales per-100 g values to a portion.
     *
     * A missing per-100 g value stays missing in the portion: scaling null to 0.0 would turn "unknown
     * fibre" into "no fibre", which is a different claim.
     */
    fun portion(
        grams: Double,
        kcal100g: Double?,
        protein100g: Double? = null,
        fat100g: Double? = null,
        carbs100g: Double? = null,
        fiber100g: Double? = null,
        sugar100g: Double? = null,
        salt100g: Double? = null,
    ): PortionNutrition {
        require(grams >= 0.0) { "grams must not be negative, was $grams" }
        val factor = grams / 100.0
        return PortionNutrition(
            grams = grams,
            kcal = kcal100g?.times(factor),
            proteinG = protein100g?.times(factor),
            fatG = fat100g?.times(factor),
            carbsG = carbs100g?.times(factor),
            fiberG = fiber100g?.times(factor),
            sugarG = sugar100g?.times(factor),
            saltG = salt100g?.times(factor),
        )
    }

    /** Convenience for the common case: a product and a weight. */
    fun portion(product: Product, grams: Double): PortionNutrition = portion(
        grams = grams,
        kcal100g = product.kcal100g,
        protein100g = product.protein100g,
        fat100g = product.fat100g,
        carbs100g = product.carbs100g,
        fiber100g = product.fiber100g,
        sugar100g = product.sugar100g,
        salt100g = product.salt100g,
    )
}
