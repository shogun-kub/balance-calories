package com.balancecalories.core.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DailyEnergyTest {

    @Test
    fun `deficit is intake minus bmr minus activity`() {
        // 2000 eaten, 1780 resting, 700 active -> 480 short
        val day = DailyEnergy(intakeKcal = 2000.0, bmrKcal = 1780.0, activeKcal = 700.0)

        assertEquals(2480.0, day.burnedKcal, 1e-9)
        assertEquals(-480.0, day.balanceKcal, 1e-9)
        assertTrue(day.inDeficit)
    }

    @Test
    fun `surplus is positive`() {
        val day = DailyEnergy(intakeKcal = 3000.0, bmrKcal = 1780.0, activeKcal = 300.0)

        assertEquals(920.0, day.balanceKcal, 1e-9)
        assertFalse(day.inDeficit)
    }

    @Test
    fun `maintenance is neither surplus nor deficit`() {
        val day = DailyEnergy(intakeKcal = 2480.0, bmrKcal = 1780.0, activeKcal = 700.0)

        assertEquals(0.0, day.balanceKcal, 1e-9)
        assertFalse(day.inDeficit)
    }

    @Test
    fun `rejects negative inputs instead of silently treating them as zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyEnergy(intakeKcal = -1.0, bmrKcal = 1780.0, activeKcal = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DailyEnergy(intakeKcal = 2000.0, bmrKcal = 1780.0, activeKcal = -5.0)
        }
    }
}
