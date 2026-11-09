package com.balancecalories.core.food

import kotlin.math.roundToInt

/** The three macros, in the order the app prints and colours them. */
enum class Macro { PROTEIN, FAT, CARBS }

/**
 * The day's allowance for each macro, in grams.
 *
 * The app had no such number until the Today list started comparing portions against one: it knew the calorie
 * target and nothing per macro, so "protein is 5% of the daily norm" had no denominator, and inventing one
 * silently is exactly what hard rule 4 forbids.
 *
 * [derived] is therefore a **convention, not a measurement**: 25 % of the target's energy for protein, 30 % for
 * fat, 45 % for carbs, converted to grams with the same Atwater factors used everywhere else in the app. The
 * Settings screen prints these numbers and lets the person replace them per profile, and a stored norm always
 * wins (ADR-035).
 */
data class MacroNorm(
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
) {

    fun gramsOf(macro: Macro): Double = when (macro) {
        Macro.PROTEIN -> proteinG
        Macro.FAT -> fatG
        Macro.CARBS -> carbsG
    }

    /**
     * What fraction of this macro's allowance [grams] covers: 0.25 means a quarter of the day's protein.
     *
     * A norm of zero or less has no share to speak of and answers 0 rather than infinity, so a broken norm
     * cannot win an argument about which macro is largest.
     */
    fun share(macro: Macro, grams: Double?): Double {
        val allowance = gramsOf(macro)
        if (allowance <= 0.0 || grams == null) return 0.0
        return (grams.coerceAtLeast(0.0) / allowance)
    }

    /**
     * The share as a whole percent, for display and for the spoken description: 0.2649 → 26.
     *
     * Rounds the way the rest of the app rounds energy (`roundToInt`, ties away from zero) rather than with
     * `round`, which rounds ties to even and would print 12 for 12.5 % next to a kcal figure that rounded up.
     */
    fun percentOf(macro: Macro, grams: Double?): Int = (share(macro, grams) * 100.0).roundToInt()

    /**
     * Which macro(s) a portion is proportionally richest in — the largest share of its **own** allowance.
     *
     * Compared within the row, never between rows: 30 g of protein is a quarter of a day's protein and 30 g of
     * carbs is a seventh of a day's carbs, so the same 30 g wins for the chicken and loses for the rice.
     *
     * Returns **every** macro tied for the lead, because a portion genuinely high in two macros should say so;
     * returns none when there is nothing to compare (no macro data, or all of them zero), which is what keeps a
     * row of dashes from wearing a highlight it has not earned.
     */
    fun leaders(proteinG: Double?, fatG: Double?, carbsG: Double?): Set<Macro> {
        val shares = mapOf(
            Macro.PROTEIN to share(Macro.PROTEIN, proteinG),
            Macro.FAT to share(Macro.FAT, fatG),
            Macro.CARBS to share(Macro.CARBS, carbsG),
        )
        val best = shares.values.maxOrNull() ?: return emptySet()
        if (best <= 0.0) return emptySet()
        return shares.filterValues { it >= best - TIE_EPSILON }.keys
    }

    companion object {

        /** Energy shares of the target that [derived] uses. A convention, so it lives in exactly one place. */
        const val PROTEIN_ENERGY_SHARE = 0.25
        const val FAT_ENERGY_SHARE = 0.30
        const val CARBS_ENERGY_SHARE = 0.45

        /**
         * Two shares this close are a tie. Float division of two identical numbers is exact, but the shares come
         * from grams that were themselves rounded for display, and "equal" should not depend on the 15th digit.
         */
        const val TIE_EPSILON = 1e-6

        /** The norms implied by a calorie target, or all zeros when the target is unusable. */
        fun derived(targetKcal: Int): MacroNorm {
            if (targetKcal <= 0) return MacroNorm(0.0, 0.0, 0.0)
            val kcal = targetKcal.toDouble()
            return MacroNorm(
                proteinG = kcal * PROTEIN_ENERGY_SHARE / MacroEnergy.KCAL_PER_G_PROTEIN,
                fatG = kcal * FAT_ENERGY_SHARE / MacroEnergy.KCAL_PER_G_FAT,
                carbsG = kcal * CARBS_ENERGY_SHARE / MacroEnergy.KCAL_PER_G_CARBS,
            )
        }

        /**
         * `"119,63.5,214.2"`, the form stored in `app_setting`.
         *
         * Commas separate, dots are the decimal point, and no trailing zeros: the value is read back by the same
         * app that wrote it, and a human reading the debug screen should not have to decode `1.19E2`.
         */
        fun encode(norm: MacroNorm): String = listOf(norm.proteinG, norm.fatG, norm.carbsG)
            .joinToString(",") { trimGrams(it) }

        /** Null for anything that is not three readable gram values, so a bad row cannot invent a norm. */
        fun decode(text: String?): MacroNorm? {
            if (text == null) return null
            val parts = text.split(",").map { it.trim() }
            if (parts.size != 3) return null
            val values = parts.map { part -> part.toDoubleOrNull() ?: return null }
            if (values.any { it < 0.0 || !it.isFinite() }) return null
            return MacroNorm(values[0], values[1], values[2])
        }

        private fun trimGrams(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
