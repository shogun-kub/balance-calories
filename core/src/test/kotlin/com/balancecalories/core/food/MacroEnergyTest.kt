package com.balancecalories.core.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The intake bar's split. Synthetic numbers only.
 *
 * The property that matters is that the segments sum to the day's logged energy: a stacked bar whose parts do not
 * add up to its height is a lie drawn in colour, and this is the chart a person reads instead of the numbers.
 */
class MacroEnergyTest {

    @Test
    @DisplayName("Atwater energy is 4/9/4 per gram")
    fun atwaterMatchesTheStandardFactors() {
        // 30 g protein, 20 g fat, 50 g carbs.
        assertEquals(120.0 + 180.0 + 200.0, MacroEnergy.atwaterKcal(30.0, 20.0, 50.0), 1e-9)
    }

    @Test
    @DisplayName("macros that account for less than the logged energy leave an 'other' segment")
    fun remainderBecomesOther() {
        // 10/10/10 g = 40 + 90 + 40 = 170 kcal, but the product states 200.
        val split = MacroEnergy.split(proteinG = 10.0, fatG = 10.0, carbsG = 10.0, eatenKcal = 200.0)

        assertEquals(40.0, split.proteinKcal, 1e-9)
        assertEquals(90.0, split.fatKcal, 1e-9)
        assertEquals(40.0, split.carbsKcal, 1e-9)
        assertEquals(30.0, split.otherKcal, 1e-9)
        assertEquals(200.0, split.totalKcal, 1e-9)
        assertFalse(split.isUnlabelled)
    }

    @Test
    @DisplayName("macros that account for more than the logged energy are scaled down to fit")
    fun overStatedMacrosShrink() {
        // Atwater says 170, the label says 100: the bar is the label's height, so the segments shrink by 100/170.
        val split = MacroEnergy.split(proteinG = 10.0, fatG = 10.0, carbsG = 10.0, eatenKcal = 100.0)

        assertEquals(100.0, split.totalKcal, 1e-9)
        assertEquals(0.0, split.otherKcal, 1e-9)
        val factor = 100.0 / 170.0
        assertEquals(40.0 * factor, split.proteinKcal, 1e-9)
        assertEquals(90.0 * factor, split.fatKcal, 1e-9)
        assertEquals(40.0 * factor, split.carbsKcal, 1e-9)
    }

    @Test
    @DisplayName("a rounding-sized remainder is absorbed instead of drawn as a sliver")
    fun tinyRemainderIsAbsorbedIntoFat() {
        // 169.5 kcal of macros against a stated 170: half a kcal is not a food group.
        val split = MacroEnergy.split(proteinG = 10.0, fatG = 10.0, carbsG = 10.0, eatenKcal = 170.5)

        assertEquals(170.5, split.totalKcal, 1e-9)
        assertEquals(0.0, split.otherKcal, 1e-9)
        assertEquals(90.5, split.fatKcal, 1e-9)
    }

    @Test
    @DisplayName("a day with no macros is one unlabelled block, not three zeros")
    fun noMacrosIsUnlabelled() {
        val split = MacroEnergy.split(proteinG = 0.0, fatG = 0.0, carbsG = 0.0, eatenKcal = 480.0)

        assertEquals(480.0, split.totalKcal, 1e-9)
        assertEquals(480.0, split.otherKcal, 1e-9)
        assertTrue(split.isUnlabelled)
    }

    @Test
    @DisplayName("no logged energy means no bar, whatever the macros say")
    fun zeroEatenDrawsNothing() {
        val split = MacroEnergy.split(proteinG = 12.0, fatG = 5.0, carbsG = 40.0, eatenKcal = 0.0)

        assertEquals(0.0, split.totalKcal, 1e-9)
        assertEquals(0.0, split.otherKcal, 1e-9)
    }

    @Test
    @DisplayName("negative macros are clamped, not subtracted")
    fun negativeMacrosAreClamped() {
        // A malformed product must not make the protein segment point downwards.
        val split = MacroEnergy.split(proteinG = -10.0, fatG = 10.0, carbsG = 10.0, eatenKcal = 130.0)

        assertEquals(0.0, split.proteinKcal, 1e-9)
        assertEquals(90.0, split.fatKcal, 1e-9)
        assertEquals(40.0, split.carbsKcal, 1e-9)
        assertEquals(130.0, split.totalKcal, 1e-9)
    }

    @Test
    @DisplayName("segments always add up to the logged energy, over random days")
    fun segmentsAlwaysSumToTheBar() {
        // The property the chart depends on, checked over a spread of shapes rather than one example.
        val random = kotlin.random.Random(20261108)
        repeat(500) {
            val protein = random.nextDouble(0.0, 200.0)
            val fat = random.nextDouble(0.0, 200.0)
            val carbs = random.nextDouble(0.0, 400.0)
            val atwater = MacroEnergy.atwaterKcal(protein, fat, carbs)
            // Deliberately includes labels that disagree with their own macros in both directions.
            val stated = atwater * random.nextDouble(0.5, 1.5) + random.nextDouble(0.0, 5.0)

            val split = MacroEnergy.split(protein, fat, carbs, stated)
            assertEquals(stated, split.totalKcal, 1e-6, "segments must fill the bar exactly")
            assertTrue(split.proteinKcal >= 0.0 && split.fatKcal >= 0.0 && split.carbsKcal >= 0.0)
            assertTrue(split.otherKcal >= 0.0)
        }
    }
}
