package com.balancecalories.core.energy

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test vectors for the BMR equations. These numbers are the contract: if an equation is edited,
 * one of these must fail (AGENTS.md, hard rule 7).
 */
class BmrTest {

    @Test
    fun `mifflin male 80 kg 180 cm 30 y`() {
        // 10*80 + 6.25*180 - 5*30 + 5
        val bmr = Bmr.mifflinStJeor(Sex.MALE, weightKg = 80.0, heightCm = 180.0, ageYears = 30)
        assertEquals(1780.0, bmr, 1e-9)
    }

    @Test
    fun `mifflin female 60 kg 165 cm 30 y`() {
        // 600 + 1031.25 - 150 - 161
        val bmr = Bmr.mifflinStJeor(Sex.FEMALE, weightKg = 60.0, heightCm = 165.0, ageYears = 30)
        assertEquals(1320.25, bmr, 1e-9)
    }

    @Test
    fun `mifflin difference between sexes is a constant 166 kcal`() {
        val male = Bmr.mifflinStJeor(Sex.MALE, 70.0, 175.0, 40)
        val female = Bmr.mifflinStJeor(Sex.FEMALE, 70.0, 175.0, 40)
        assertEquals(166.0, male - female, 1e-9)
    }

    @Test
    fun `harris benedict revised male 80 kg 180 cm 30 y`() {
        // 88.362 + 13.397*80 + 4.799*180 - 5.677*30
        val bmr = Bmr.harrisBenedictRevised(Sex.MALE, 80.0, 180.0, 30)
        assertEquals(1853.632, bmr, 1e-6)
    }

    @Test
    fun `harris benedict revised female 60 kg 165 cm 30 y`() {
        // 447.593 + 9.247*60 + 3.098*165 - 4.330*30
        val bmr = Bmr.harrisBenedictRevised(Sex.FEMALE, 60.0, 165.0, 30)
        assertEquals(1383.683, bmr, 1e-6)
    }

    @Test
    fun `mifflin is lower than harris benedict for a typical male`() {
        val mifflin = Bmr.mifflinStJeor(Sex.MALE, 80.0, 180.0, 30)
        val harris = Bmr.harrisBenedictRevised(Sex.MALE, 80.0, 180.0, 30)
        assertTrue(mifflin < harris, "expected Mifflin ($mifflin) below Harris–Benedict ($harris)")
    }

    @Test
    fun `rejects non positive body measurements`() {
        assertThrows(IllegalArgumentException::class.java) {
            Bmr.mifflinStJeor(Sex.MALE, weightKg = 0.0, heightCm = 180.0, ageYears = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bmr.mifflinStJeor(Sex.MALE, weightKg = 80.0, heightCm = -1.0, ageYears = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bmr.mifflinStJeor(Sex.MALE, weightKg = 80.0, heightCm = 180.0, ageYears = 0)
        }
    }

    @Test
    fun `profile ages by completed years`() {
        val profile = EnergyProfile(
            sex = Sex.MALE,
            birthDate = LocalDate.of(1996, 1, 1),
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = ActivityLevel.MODERATE,
        )
        assertEquals(30, profile.ageAt(LocalDate.of(2026, 11, 7)))
        // The day before the birthday the person is still 29.
        assertEquals(29, profile.ageAt(LocalDate.of(2025, 12, 31)))
    }
}
