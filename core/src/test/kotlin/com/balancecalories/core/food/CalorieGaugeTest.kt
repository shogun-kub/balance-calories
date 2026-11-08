package com.balancecalories.core.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The Today gauge's fractions. Synthetic numbers only.
 *
 * Two properties are the reason this is not inline in the composable: the arc can never overflow, and a missing
 * target can never produce a fill.
 */
class CalorieGaugeTest {

    private fun split(proteinG: Double, fatG: Double, carbsG: Double, eaten: Double) =
        MacroEnergy.split(proteinG, fatG, carbsG, eaten)

    @Test
    @DisplayName("a half-eaten day fills half the arc and splits it by macro")
    fun halfOfTargetSplitsByMacro() {
        // 50 g protein, 20 g fat, 100 g carbs = 200 + 180 + 400 = 780 kcal, target 1560.
        val fill = CalorieGauge.of(780.0, 1560.0, split(50.0, 20.0, 100.0, 780.0))

        assertEquals(0.5, fill.used, 1e-9)
        assertEquals(0.0, fill.overKcal, 1e-9)
        // Each segment is its energy share of the day, halved because half the target is used.
        assertEquals(200.0 / 780.0 * 0.5, fill.protein, 1e-9)
        assertEquals(180.0 / 780.0 * 0.5, fill.fat, 1e-9)
        assertEquals(400.0 / 780.0 * 0.5, fill.carbs, 1e-9)
        assertEquals(fill.used, fill.protein + fill.fat + fill.carbs + fill.other, 1e-9)
    }

    @Test
    @DisplayName("exactly the target fills the arc completely and reports no excess")
    fun exactlyTargetFillsArc() {
        val fill = CalorieGauge.of(1900.0, 1900.0, split(100.0, 60.0, 200.0, 1900.0))

        assertEquals(1.0, fill.used, 1e-9)
        assertEquals(0.0, fill.overKcal, 1e-9)
    }

    @Test
    @DisplayName("eating past the target fills the arc and reports the excess, never a sweep past the half circle")
    fun overTargetClampsAndReports() {
        val fill = CalorieGauge.of(2400.0, 1900.0, split(120.0, 80.0, 250.0, 2400.0))

        assertEquals(1.0, fill.used, 1e-9, "the arc cannot wrap around")
        assertEquals(500.0, fill.overKcal, 1e-9)
        assertEquals(1.0, fill.protein + fill.fat + fill.carbs + fill.other, 1e-9)
    }

    @Test
    @DisplayName("no target means no fill: the gauge has no denominator to draw against")
    fun noTargetDrawsNothing() {
        val fill = CalorieGauge.of(800.0, 0.0, split(30.0, 20.0, 90.0, 800.0))

        assertEquals(0.0, fill.used, 1e-9)
        assertTrue(fill.isEmpty)
    }

    @Test
    @DisplayName("nothing eaten is an empty arc, not a full one")
    fun nothingEatenIsEmpty() {
        val fill = CalorieGauge.of(0.0, 1900.0, split(0.0, 0.0, 0.0, 0.0))

        assertEquals(0.0, fill.used, 1e-9)
        assertEquals(0.0, fill.overKcal, 1e-9)
        assertTrue(fill.isEmpty)
    }

    @Test
    @DisplayName("unlabelled energy still fills the arc")
    fun unlabelledEnergyFillsTheArc() {
        // A day whose products carry no macros: the whole fill is the neutral segment.
        val fill = CalorieGauge.of(950.0, 1900.0, split(0.0, 0.0, 0.0, 950.0))

        assertEquals(0.5, fill.used, 1e-9)
        assertEquals(0.0, fill.protein, 1e-9)
        assertEquals(0.5, fill.other, 1e-9)
    }
}
