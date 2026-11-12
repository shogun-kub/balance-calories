package com.balancecalories.core.food

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The four energy splits offered instead of asking a person to type grams.
 *
 * The owner's point (2026-11-12) is that nobody knows what "96 g of protein" means, while "30/30/40" is
 * something a person has read on a pack or heard from a coach. A preset is therefore an *input method*, not a
 * new setting: choosing one computes grams for the current target and stores them exactly as a typed norm
 * would be stored ([MacroNorm.encode]). That keeps the meaning of a stored norm unchanged - it is still grams,
 * it still wins over the derived default, and a later change of target does not silently rewrite somebody's
 * decision (ADR-035).
 *
 * The shares are of the day's **energy**, which is why the grams divide by the Atwater factor of each macro
 * rather than by its share of the mass.
 */
enum class MacroPreset(
    val proteinShare: Double,
    val fatShare: Double,
    val carbsShare: Double,
) {
    /** The convention the app already used: a quarter of the energy for protein, 30 % fat, 45 % carbs. */
    BY_TARGET(0.25, 0.30, 0.45),

    /** The split most often printed on packaging as "balanced". */
    EVEN(0.30, 0.30, 0.40),

    /** A deficit split that protects muscle without asking for a kilo of cottage cheese. */
    PROTEIN(0.40, 0.30, 0.30),

    /** For a hard training block; fat is still above the hormonal floor at these targets. */
    HIGH_PROTEIN(0.45, 0.25, 0.30),
    ;

    fun percent(macro: Macro): Int = when (macro) {
        Macro.PROTEIN -> (proteinShare * 100.0).roundToInt()
        Macro.FAT -> (fatShare * 100.0).roundToInt()
        Macro.CARBS -> (carbsShare * 100.0).roundToInt()
    }

    /**
     * The grams this split implies for [targetKcal], to whole grams.
     *
     * Whole grams, not the 51.333 the derived default produced: a preset is a round number a person chose, and
     * printing a third decimal invited the question of where it came from. The energy of the rounded grams
     * misses the target by a kcal or two, which is below every number the app shows.
     */
    fun grams(targetKcal: Int): MacroNorm {
        if (targetKcal <= 0) return MacroNorm(0.0, 0.0, 0.0)
        val kcal = targetKcal.toDouble()
        return MacroNorm(
            proteinG = (kcal * proteinShare / MacroEnergy.KCAL_PER_G_PROTEIN).roundToInt().toDouble(),
            fatG = (kcal * fatShare / MacroEnergy.KCAL_PER_G_FAT).roundToInt().toDouble(),
            carbsG = (kcal * carbsShare / MacroEnergy.KCAL_PER_G_CARBS).roundToInt().toDouble(),
        )
    }

    companion object {

        /** Two splits closer than this are the same split; rounding to grams moves a share by about a percent. */
        const val SHARE_TOLERANCE = 0.02

        /**
         * Which preset a stored norm represents, or null when it is the person's own numbers.
         *
         * Two ways to match, in this order: the exact grams this preset would produce for the target (the case
         * after choosing a chip), then the energy shares within [SHARE_TOLERANCE] (the case of a norm stored
         * before the presets existed, or derived from an older target).
         */
        fun matching(norm: MacroNorm, targetKcal: Int): MacroPreset? {
            entries.firstOrNull { preset ->
                val grams = preset.grams(targetKcal)
                grams.proteinG == norm.proteinG &&
                    grams.fatG == norm.fatG &&
                    grams.carbsG == norm.carbsG
            }?.let { return it }

            val shares = sharesOf(norm) ?: return null
            return entries.firstOrNull { preset ->
                abs(shares[0] - preset.proteinShare) <= SHARE_TOLERANCE &&
                    abs(shares[1] - preset.fatShare) <= SHARE_TOLERANCE &&
                    abs(shares[2] - preset.carbsShare) <= SHARE_TOLERANCE
            }
        }

        /** The norm's energy split, or null when it carries no energy at all. */
        fun sharesOf(norm: MacroNorm): List<Double>? {
            val protein = norm.proteinG * MacroEnergy.KCAL_PER_G_PROTEIN
            val fat = norm.fatG * MacroEnergy.KCAL_PER_G_FAT
            val carbs = norm.carbsG * MacroEnergy.KCAL_PER_G_CARBS
            val total = protein + fat + carbs
            if (total <= 0.0) return null
            return listOf(protein / total, fat / total, carbs / total)
        }
    }
}
