package com.balancecalories.data.mapper

import com.balancecalories.core.profile.Profile
import com.balancecalories.core.profile.UnitSystem
import com.balancecalories.data.db.entity.ProfileEntity
import java.time.LocalDate

/**
 * Boundary between Room rows and the domain. Kept in one file so a column rename has exactly one place
 * to break, and so `:app` never sees an entity.
 */
internal fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    name = name,
    sex = sex,
    birthDate = LocalDate.parse(birthDate),
    heightCm = heightCm,
    activityLevel = activityLevel,
    goalRateKgPerWeek = goalRateKgPerWeek,
    targetKcalOverride = targetKcalOverride,
        goalWeightKg = goalWeightKg,
    bandEnabled = bandEnabled,
    bandLabel = bandLabel,
    dayStartHour = dayStartHour,
    unitSystem = when (unitSystem) {
        "imperial" -> UnitSystem.IMPERIAL
        "metric" -> UnitSystem.METRIC
        else -> error("Unknown unit_system in database: $unitSystem")
    },
    createdAt = createdAt,
    archivedAt = archivedAt,
)

internal fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    sex = sex,
    birthDate = birthDate.toString(),
    heightCm = heightCm,
    activityLevel = activityLevel,
    goalRateKgPerWeek = goalRateKgPerWeek,
    targetKcalOverride = targetKcalOverride,
        goalWeightKg = goalWeightKg,
    bandEnabled = bandEnabled,
    bandLabel = bandLabel,
    dayStartHour = dayStartHour,
    unitSystem = when (unitSystem) {
        UnitSystem.IMPERIAL -> "imperial"
        UnitSystem.METRIC -> "metric"
    },
    createdAt = createdAt,
    archivedAt = archivedAt,
)
