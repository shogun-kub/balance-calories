package com.balancecalories.core.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The macro presets.
 *
 * The numbers are pinned because they reach a screen a person reads: "30/30/40" that computes to something
 * else, or a stored norm reported as a preset it is not, would both be quiet lies rather than crashes.
 */
class MacroPresetTest {

    @Test
    fun `by target reproduces the convention the app already used, in whole grams`() {
        // 1540 kcal: 25/30/45 → 96.25 g protein, 51.33 g fat, 173.25 g carbs.
        val grams = MacroPreset.BY_TARGET.grams(1540)

        assertEquals(96.0, grams.proteinG, 1e-9)
        assertEquals(51.0, grams.fatG, 1e-9)
        assertEquals(173.0, grams.carbsG, 1e-9)
    }

    @Test
    fun `even at two thousand kcal is 150 67 200`() {
        val grams = MacroPreset.EVEN.grams(2000)

        assertEquals(150.0, grams.proteinG, 1e-9)
        assertEquals(67.0, grams.fatG, 1e-9)
        assertEquals(200.0, grams.carbsG, 1e-9)
    }

    @Test
    fun `every preset matches its own grams`() {
        for (preset in MacroPreset.entries) {
            assertEquals(
                preset,
                MacroPreset.matching(preset.grams(2283), 2283),
                "preset $preset does not recognise the grams it produced",
            )
        }
    }

    @Test
    fun `the derived norm is reported as by target`() {
        assertEquals(
            MacroPreset.BY_TARGET,
            MacroPreset.matching(MacroNorm.derived(1540), 1540),
        )
    }

    @Test
    fun `a persons own numbers are not called a preset`() {
        assertNull(MacroPreset.matching(MacroNorm(proteinG = 200.0, fatG = 30.0, carbsG = 60.0), 2283))
    }

    @Test
    fun `shares of a norm are of energy, not of mass`() {
        // 100 g protein and 100 g fat are equal in mass and not at all equal in energy.
        val shares = MacroPreset.sharesOf(MacroNorm(proteinG = 100.0, fatG = 100.0, carbsG = 0.0))!!

        assertEquals(4.0 / 13.0, shares[0], 1e-9)
        assertEquals(9.0 / 13.0, shares[1], 1e-9)
    }

    @Test
    fun `a norm without energy has no shares and matches nothing`() {
        assertNull(MacroPreset.sharesOf(MacroNorm(0.0, 0.0, 0.0)))
        assertNull(MacroPreset.matching(MacroNorm(0.0, 0.0, 0.0), 2283))
    }
}
