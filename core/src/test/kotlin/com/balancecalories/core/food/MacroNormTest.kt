package com.balancecalories.core.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The norms are a convention, so the arithmetic is the only thing that can be wrong: 25/30/45 of the target's
 * energy through the Atwater factors, and a leader rule that must not award a highlight to a row with nothing
 * in it.
 */
class MacroNormTest {

    @Test
    fun `derived splits the target 25 30 45 through atwater`() {
        val norm = MacroNorm.derived(2000)

        // 2000 × 0.25 / 4 = 125, 2000 × 0.30 / 9 = 66.67, 2000 × 0.45 / 4 = 225
        assertEquals(125.0, norm.proteinG, 1e-9)
        assertEquals(66.66666666666667, norm.fatG, 1e-9)
        assertEquals(225.0, norm.carbsG, 1e-9)
    }

    @Test
    fun `derived handles the real target the app shows`() {
        val norm = MacroNorm.derived(1904)

        assertEquals(119.0, norm.proteinG, 1e-9)
        assertEquals(63.46666666666667, norm.fatG, 1e-9)
        assertEquals(214.2, norm.carbsG, 1e-9)
    }

    @Test
    fun `the shares add up to the whole target by construction`() {
        val norm = MacroNorm.derived(1904)
        val kcal = norm.proteinG * MacroEnergy.KCAL_PER_G_PROTEIN +
            norm.fatG * MacroEnergy.KCAL_PER_G_FAT +
            norm.carbsG * MacroEnergy.KCAL_PER_G_CARBS

        assertEquals(1904.0, kcal, 1e-9)
    }

    @Test
    fun `an unusable target produces zeros rather than nan`() {
        assertEquals(MacroNorm(0.0, 0.0, 0.0), MacroNorm.derived(0))
        assertEquals(MacroNorm(0.0, 0.0, 0.0), MacroNorm.derived(-500))
    }

    @Test
    fun `share is grams over that macro's own allowance`() {
        val norm = MacroNorm(proteinG = 120.0, fatG = 60.0, carbsG = 240.0)

        assertEquals(0.25, norm.share(Macro.PROTEIN, 30.0), 1e-9)
        assertEquals(0.5, norm.share(Macro.FAT, 30.0), 1e-9)
        assertEquals(0.125, norm.share(Macro.CARBS, 30.0), 1e-9)
    }

    @Test
    fun `the same grams win for one macro and lose for another`() {
        // 30 g of protein is a quarter of a day's protein; 30 g of carbs is an eighth of a day's carbs.
        val norm = MacroNorm(proteinG = 120.0, fatG = 60.0, carbsG = 240.0)

        assertEquals(setOf(Macro.PROTEIN), norm.leaders(30.0, 0.0, 0.0))
        assertEquals(setOf(Macro.CARBS), norm.leaders(0.0, 0.0, 30.0))
    }

    @Test
    fun `leaders picks the largest share, not the largest number of grams`() {
        val norm = MacroNorm.derived(1904)

        // A chicken breast: 31.5 g protein, 2.4 g fat, nothing else.
        assertEquals(setOf(Macro.PROTEIN), norm.leaders(31.5, 2.4, 0.0))
        // Olive oil: pure fat.
        assertEquals(setOf(Macro.FAT), norm.leaders(0.0, 15.0, 0.0))
        // Cooked rice: 56 g carbs dwarfs 5 g protein and 0.6 g fat.
        assertEquals(setOf(Macro.CARBS), norm.leaders(5.0, 0.6, 56.0))
    }

    @Test
    fun `a portion that is equally rich in two macros names both`() {
        val norm = MacroNorm(proteinG = 100.0, fatG = 50.0, carbsG = 200.0)

        // 10 g of protein and 5 g of fat are both 10 % of their own allowance.
        assertEquals(setOf(Macro.PROTEIN, Macro.FAT), norm.leaders(10.0, 5.0, 0.0))
    }

    @Test
    fun `three equal shares name all three`() {
        val norm = MacroNorm(proteinG = 100.0, fatG = 100.0, carbsG = 100.0)

        assertEquals(Macro.entries.toSet(), norm.leaders(10.0, 10.0, 10.0))
    }

    @Test
    fun `a row with no macro data has no leader`() {
        val norm = MacroNorm.derived(1904)

        assertEquals(emptySet<Macro>(), norm.leaders(null, null, null))
        assertEquals(emptySet<Macro>(), norm.leaders(0.0, 0.0, 0.0))
    }

    @Test
    fun `missing macros are skipped rather than treated as zero shares that could win`() {
        val norm = MacroNorm.derived(1904)

        assertEquals(setOf(Macro.FAT), norm.leaders(null, 15.0, null))
    }

    @Test
    fun `negative grams neither win nor turn the share upside down`() {
        val norm = MacroNorm.derived(1904)

        assertEquals(setOf(Macro.FAT), norm.leaders(-10.0, 15.0, -3.0))
        assertEquals(0.0, norm.share(Macro.PROTEIN, -10.0), 1e-9)
    }

    @Test
    fun `a zero norm cannot win a comparison`() {
        val broken = MacroNorm(proteinG = 0.0, fatG = 60.0, carbsG = 240.0)

        assertEquals(0.0, broken.share(Macro.PROTEIN, 100.0), 1e-9)
        assertEquals(setOf(Macro.FAT), broken.leaders(100.0, 10.0, 10.0))
    }

    @Test
    fun `percentOf reports whole percents of that macro's own norm`() {
        val norm = MacroNorm(proteinG = 120.0, fatG = 60.0, carbsG = 240.0)

        assertEquals(25, norm.percentOf(Macro.PROTEIN, 30.0))
        assertEquals(50, norm.percentOf(Macro.FAT, 30.0))
        assertEquals(13, norm.percentOf(Macro.CARBS, 30.0))
    }

    @Test
    fun `a trace rounds to zero rather than to one`() {
        val norm = MacroNorm.derived(1904)

        // 0.45 g of protein against 119 g is 0.4 %, which is not "1 % of the day".
        assertEquals(0, norm.percentOf(Macro.PROTEIN, 0.45))
    }

    @Test
    fun `percentOf of a missing macro is zero`() {
        assertEquals(0, MacroNorm.derived(1904).percentOf(Macro.FAT, null))
    }

    @Test
    fun `encode and decode round trip`() {
        val norm = MacroNorm(proteinG = 119.0, fatG = 63.5, carbsG = 214.2)

        assertEquals("119,63.5,214.2", MacroNorm.encode(norm))
        assertEquals(norm, MacroNorm.decode("119,63.5,214.2"))
    }

    @Test
    fun `decode refuses anything that is not three readable grams`() {
        assertNull(MacroNorm.decode(null))
        assertNull(MacroNorm.decode(""))
        assertNull(MacroNorm.decode("119,63.5"))
        assertNull(MacroNorm.decode("119,63.5,214.2,7"))
        assertNull(MacroNorm.decode("119,heavy,214.2"))
        assertNull(MacroNorm.decode("-1,2,3"))
        assertNull(MacroNorm.decode("NaN,2,3"))
        assertNull(MacroNorm.decode("Infinity,2,3"))
    }

    @Test
    fun `decode tolerates spaces around the numbers`() {
        assertEquals(MacroNorm(1.0, 2.0, 3.0), MacroNorm.decode(" 1 , 2 , 3 "))
    }

    @Test
    fun `encode does not print exponent or trailing zeros`() {
        assertEquals("100,50,200", MacroNorm.encode(MacroNorm(100.0, 50.0, 200.0)))
        assertEquals("0.107,1.5,3", MacroNorm.encode(MacroNorm(0.107, 1.5, 3.0)))
    }
}
