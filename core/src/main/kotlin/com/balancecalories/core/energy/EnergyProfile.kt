package com.balancecalories.core.energy

import java.time.LocalDate
import java.time.Period

/**
 * Everything the energy equations need about a person.
 *
 * Age is derived from the birth date rather than stored, so a target computed in December and one
 * computed in February cannot disagree by a year.
 */
data class EnergyProfile(
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
) {
    init {
        require(heightCm > 0.0) { "heightCm must be positive, was $heightCm" }
        require(weightKg > 0.0) { "weightKg must be positive, was $weightKg" }
    }

    /** Completed years at [on]. Callers pass the date explicitly: no hidden clock in :core. */
    fun ageAt(on: LocalDate): Int = Period.between(birthDate, on).years
}
