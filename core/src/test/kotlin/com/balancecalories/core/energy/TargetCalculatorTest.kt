package com.balancecalories.core.energy

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TargetCalculatorTest {

    private val on: LocalDate = LocalDate.of(2026, 11, 7)

    private fun male(
        weightKg: Double = 80.0,
        heightCm: Double = 180.0,
        level: ActivityLevel = ActivityLevel.MODERATE,
    ) = EnergyProfile(
        sex = Sex.MALE,
        birthDate = LocalDate.of(1996, 1, 1),
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = level,
    )

    private fun female(
        weightKg: Double = 45.0,
        heightCm: Double = 150.0,
        birthDate: LocalDate = LocalDate.of(1981, 1, 1),
        level: ActivityLevel = ActivityLevel.SEDENTARY,
    ) = EnergyProfile(
        sex = Sex.FEMALE,
        birthDate = birthDate,
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = level,
    )

    @Test
    fun `moderate male losing half a kilo per week`() {
        // BMR 1780, TDEE 1780*1.55 = 2759, requested deficit 0.5*7700/7 = 550
        val target = TargetCalculator.seed(male(), goalRateKgPerWeek = -0.5, on = on)

        assertEquals(1780.0, target.bmrKcal, 1e-9)
        assertEquals(2759.0, target.tdeeKcal, 1e-9)
        assertEquals(550.0, target.deficitKcal, 1e-9)
        assertEquals(2209, target.kcal)
        assertEquals(TargetLimit.NONE, target.limit)
    }

    @Test
    fun `floor wins over an aggressive request for a small sedentary woman`() {
        // BMR 1001.5, TDEE 1201.8; 550 requested would leave 651.8 kcal
        val target = TargetCalculator.seed(female(), goalRateKgPerWeek = -0.5, on = on)

        assertEquals(1001.5, target.bmrKcal, 1e-9)
        assertEquals(1201.8, target.tdeeKcal, 1e-6)
        assertEquals(1200, target.kcal)
        assertEquals(TargetLimit.FLOOR, target.limit)
        // The floor turns the plan into a near-maintenance surplus of ~2 kcal.
        assertEquals(1.8, target.deficitKcal, 1e-6)
    }

    @Test
    fun `deficit is capped at a quarter of expenditure`() {
        // TDEE 3382; 1 kg/week would be 1100 kcal, cap is 845.5
        val target = TargetCalculator.seed(
            male(level = ActivityLevel.ATHLETE),
            goalRateKgPerWeek = -1.0,
            on = on,
        )

        assertEquals(3382.0, target.tdeeKcal, 1e-9)
        // Deficit is derived from the rounded target (3382 - 2537), so it is 845.0, not the
        // unrounded cap of 845.5. What is reported must be what the user can actually hit.
        assertEquals(845.0, target.deficitKcal, 1e-9)
        assertEquals(2537, target.kcal)
        assertEquals(TargetLimit.DEFICIT_CAP, target.limit)
    }

    @Test
    fun `a gain goal adds a surplus and is capped the same way`() {
        val modest = TargetCalculator.seed(male(), goalRateKgPerWeek = 0.25, on = on)
        assertEquals(-275.0, modest.deficitKcal, 1e-9)
        assertEquals(3034, modest.kcal)
        assertEquals(TargetLimit.NONE, modest.limit)

        val aggressive = TargetCalculator.seed(male(), goalRateKgPerWeek = 1.0, on = on)
        assertEquals(-690.0, aggressive.deficitKcal, 1e-9)
        assertEquals(3449, aggressive.kcal)
        assertEquals(TargetLimit.DEFICIT_CAP, aggressive.limit)
    }

    @Test
    fun `maintenance goal returns expenditure`() {
        val target = TargetCalculator.seed(male(), goalRateKgPerWeek = 0.0, on = on)
        assertEquals(2759, target.kcal)
        assertEquals(0.0, target.deficitKcal, 1e-9)
        assertEquals(TargetLimit.NONE, target.limit)
    }

    @Test
    fun `rejects an absurd goal rate`() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetCalculator.seed(male(), goalRateKgPerWeek = -3.0, on = on)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetCalculator.seed(male(), goalRateKgPerWeek = Double.NaN, on = on)
        }
    }

    @Test
    fun `adaptive change is clamped to five percent per week`() {
        assertEquals(2310, TargetCalculator.limitWeeklyChange(previousKcal = 2200, proposedKcal = 2400))
        assertEquals(2090, TargetCalculator.limitWeeklyChange(previousKcal = 2200, proposedKcal = 2000))
        // Within the band: untouched.
        assertEquals(2210, TargetCalculator.limitWeeklyChange(previousKcal = 2200, proposedKcal = 2210))
        // No previous target: accept the proposal as-is.
        assertEquals(1900, TargetCalculator.limitWeeklyChange(previousKcal = 0, proposedKcal = 1900))
    }

    @Test
    fun `floor never falls below one point one times bmr`() {
        // A large BMR dominates the absolute floor.
        assertEquals(3300.0, TargetCalculator.floorKcal(Sex.MALE, bmrKcal = 3000.0), 1e-9)
        // The absolute floor dominates a small BMR.
        assertEquals(1200.0, TargetCalculator.floorKcal(Sex.FEMALE, bmrKcal = 900.0), 1e-9)
        assertEquals(1500.0, TargetCalculator.floorKcal(Sex.MALE, bmrKcal = 900.0), 1e-9)
    }
}
