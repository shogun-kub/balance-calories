package com.balancecalories.core.profile

import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.EnergyProfile
import com.balancecalories.core.energy.Sex
import java.time.LocalDate

/** How lengths and weights are shown. Storage is always metric (docs/03-architecture.md §3). */
enum class UnitSystem { METRIC, IMPERIAL }

/**
 * A person using the app. This is the domain shape: no Room, no Android, so the UI and the maths can
 * both depend on it.
 *
 * Weight is deliberately absent — it changes daily and lives in `body_measurement`. Use
 * [toEnergyProfile] with the current weight to get something the calorie maths can consume.
 */
data class Profile(
    val id: Long = NEW,
    val name: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Double,
    val activityLevel: ActivityLevel,
    /** Negative to lose weight. */
    val goalRateKgPerWeek: Double,
    /** A number the user pinned; null means "recompute from the maths". */
    val targetKcalOverride: Int? = null,
    /** The weight they are aiming for, or null when they have not set one. Drives the projection. */
    val goalWeightKg: Double? = null,
    /** Whether this profile is the one allowed to read Health Connect on this phone. */
    val bandEnabled: Boolean = false,
    val bandLabel: String? = null,
    val dayStartHour: Int = 4,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val createdAt: Long,
    val archivedAt: Long? = null,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(heightCm > 0.0) { "heightCm must be positive, was $heightCm" }
        require(dayStartHour in 0..23) { "dayStartHour must be 0..23, was $dayStartHour" }
    }

    val isArchived: Boolean get() = archivedAt != null

    fun toEnergyProfile(weightKg: Double): EnergyProfile = EnergyProfile(
        sex = sex,
        birthDate = birthDate,
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = activityLevel,
    )

    companion object {
        /** The id of a profile that has not been stored yet. */
        const val NEW = 0L
    }
}
