package com.balancecalories.core.food

/**
 * Energy for 100 g of a food, together with how it was obtained.
 *
 * [estimated] is not pedantry: the product rule is that an estimate never renders with the same
 * confidence as a measurement (AGENTS.md, hard rule 4). A label that states kcal is measured; a value
 * derived from joules or from macros is an estimate and the UI says so.
 */
data class EnergyPer100g(val kcal: Double, val estimated: Boolean)

/**
 * Turns the energy fields of a food label into kilocalories.
 *
 * OpenFoodFacts stores energy inconsistently — `energy-kcal_100g` is often absent while
 * `energy-kj_100g` or `energy_100g` (kilojoules) is present, and some products carry no energy field at
 * all but do carry macros. The precedence is deliberately explicit and tested:
 *
 * 1. `kcal` present → use it, measured.
 * 2. else kilojoules present → convert, estimated.
 * 3. else macros present → Atwater (4/9/4, fibre 2), estimated.
 * 4. else nothing usable → null, so the caller can show "no data" instead of inventing a number.
 */
object LabelEnergy {

    /** 1 kcal = 4.184 kJ, the definition, not an approximation. */
    const val KJ_PER_KCAL = 4.184

    private const val PROTEIN_KCAL_PER_G = 4.0
    private const val FAT_KCAL_PER_G = 9.0
    private const val CARBS_KCAL_PER_G = 4.0
    private const val FIBER_KCAL_PER_G = 2.0

    fun resolve(
        kcalPer100g: Double?,
        kjPer100g: Double?,
        proteinG: Double?,
        fatG: Double?,
        carbsG: Double?,
        fiberG: Double? = null,
    ): EnergyPer100g? {
        if (kcalPer100g != null && kcalPer100g >= 0.0) {
            return EnergyPer100g(kcal = kcalPer100g, estimated = false)
        }
        if (kjPer100g != null && kjPer100g > 0.0) {
            return EnergyPer100g(kcal = kjPer100g / KJ_PER_KCAL, estimated = true)
        }
        if (proteinG == null && fatG == null && carbsG == null) return null

        val total = (proteinG ?: 0.0) * PROTEIN_KCAL_PER_G +
            (fatG ?: 0.0) * FAT_KCAL_PER_G +
            (carbsG ?: 0.0) * CARBS_KCAL_PER_G +
            (fiberG ?: 0.0) * FIBER_KCAL_PER_G
        return if (total > 0.0) EnergyPer100g(kcal = total, estimated = true) else null
    }
}
