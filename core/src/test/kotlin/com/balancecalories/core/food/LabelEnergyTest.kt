package com.balancecalories.core.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LabelEnergyTest {

    @Test
    @DisplayName("a stated kcal value is used as-is and is not marked estimated")
    fun statedKcalWins() {
        val energy = LabelEnergy.resolve(
            kcalPer100g = 539.0,
            kjPer100g = 2252.0,
            proteinG = 6.3,
            fatG = 56.3,
            carbsG = 57.5,
        )

        assertEquals(539.0, energy!!.kcal, 1e-9)
        assertFalse(energy.estimated)
    }

    @Test
    @DisplayName("joules alone are converted with 1 kcal = 4.184 kJ and marked estimated")
    fun joulesAreConverted() {
        val energy = LabelEnergy.resolve(
            kcalPer100g = null,
            kjPer100g = 2252.0,
            proteinG = null,
            fatG = null,
            carbsG = null,
        )

        assertEquals(2252.0 / 4.184, energy!!.kcal, 1e-9) // 538.24 kcal
        assertTrue(energy.estimated)
    }

    @Test
    @DisplayName("macros alone give Atwater energy and are marked estimated")
    fun macrosGiveAtwaterEnergy() {
        // 6.3 * 4 + 56.3 * 9 + 57.5 * 4 = 25.2 + 506.7 + 230.0 = 761.9
        val energy = LabelEnergy.resolve(
            kcalPer100g = null,
            kjPer100g = null,
            proteinG = 6.3,
            fatG = 56.3,
            carbsG = 57.5,
        )

        assertEquals(761.9, energy!!.kcal, 1e-9)
        assertTrue(energy.estimated)
    }

    @Test
    @DisplayName("fibre contributes 2 kcal per gram when macros are the only source")
    fun fibreCountsInAtwater() {
        val energy = LabelEnergy.resolve(
            kcalPer100g = null,
            kjPer100g = null,
            proteinG = 10.0,
            fatG = 0.0,
            carbsG = 0.0,
            fiberG = 10.0,
        )

        assertEquals(60.0, energy!!.kcal, 1e-9) // 40 from protein + 20 from fibre
        assertTrue(energy.estimated)
    }

    @Test
    @DisplayName("a product with no energy and no macros resolves to null, not zero")
    fun nothingUsableIsNull() {
        assertNull(LabelEnergy.resolve(null, null, null, null, null))
    }

    @Test
    @DisplayName("zero macros do not invent a positive estimate")
    fun zeroMacrosAreNull() {
        assertNull(LabelEnergy.resolve(null, null, 0.0, 0.0, 0.0))
    }

    @Test
    @DisplayName("a stated zero kcal is honoured: water is really zero")
    fun statedZeroKcalIsKept() {
        val energy = LabelEnergy.resolve(0.0, null, 0.0, 0.0, 0.0)

        assertEquals(0.0, energy!!.kcal, 1e-9)
        assertFalse(energy.estimated)
    }

    @Test
    @DisplayName("partial macros still produce an estimate rather than nothing")
    fun partialMacrosEstimate() {
        val energy = LabelEnergy.resolve(null, null, proteinG = 20.0, fatG = null, carbsG = null)

        assertEquals(80.0, energy!!.kcal, 1e-9)
        assertTrue(energy.estimated)
    }
}
