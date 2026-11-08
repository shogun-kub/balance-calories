package com.balancecalories.core.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit conversion is a place where a silent factor error turns 80 kg into 176 lb and nobody notices until
 * a target looks wrong, so the round trips and one known value are pinned here.
 */
class UnitsTest {

    @Test
    fun metricConversionIsIdentity() {
        assertEquals(175.0, Units.heightFromCm(175.0, UnitSystem.METRIC), 1e-9)
        assertEquals(80.0, Units.weightFromKg(80.0, UnitSystem.METRIC), 1e-9)
        assertEquals(175.0, Units.heightToCm(175.0, UnitSystem.METRIC), 1e-9)
        assertEquals(80.0, Units.weightToKg(80.0, UnitSystem.METRIC), 1e-9)
    }

    @Test
    fun knownValuesMatchTheDefinitions() {
        // 175 cm / 2.54 = 68.8976 in
        assertEquals(68.8976, Units.heightFromCm(175.0, UnitSystem.IMPERIAL), 1e-4)
        // 80 kg / 0.45359237 = 176.3698 lb
        assertEquals(176.3698, Units.weightFromKg(80.0, UnitSystem.IMPERIAL), 1e-4)
    }

    @Test
    fun roundTripsReturnTheOriginalMetricValue() {
        for (cm in listOf(150.0, 168.5, 175.0, 193.0)) {
            val there = Units.heightFromCm(cm, UnitSystem.IMPERIAL)
            assertEquals(cm, Units.heightToCm(there, UnitSystem.IMPERIAL), 1e-9)
        }
        for (kg in listOf(45.0, 62.3, 80.0, 120.0)) {
            val there = Units.weightFromKg(kg, UnitSystem.IMPERIAL)
            assertEquals(kg, Units.weightToKg(there, UnitSystem.IMPERIAL), 1e-9)
        }
    }
}
