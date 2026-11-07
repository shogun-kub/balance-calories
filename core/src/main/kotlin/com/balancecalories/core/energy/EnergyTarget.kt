package com.balancecalories.core.energy

/**
 * A daily intake target and the numbers it was derived from, so the UI can explain itself.
 *
 * [deficitKcal] is positive when the target is below expenditure (a deficit) and negative when the
 * target is above it (a surplus). It is `tdee - kcal`, i.e. what the day is supposed to end at.
 */
data class EnergyTarget(
    val kcal: Int,
    val bmrKcal: Double,
    val tdeeKcal: Double,
    val deficitKcal: Double,
    val limit: TargetLimit,
) {
    /** Fraction of expenditure removed (or added, if negative). */
    val deficitFraction: Double get() = if (tdeeKcal == 0.0) 0.0 else deficitKcal / tdeeKcal
}

/** Which safety rule, if any, overrode the user's requested rate. */
enum class TargetLimit {
    /** The requested rate was achievable as asked. */
    NONE,

    /** The deficit was cut to [TargetCalculator.MAX_DEFICIT_FRACTION] of expenditure. */
    DEFICIT_CAP,

    /** The target was raised to the safety floor (BMR × 1.1, or 1200/1500 kcal). */
    FLOOR,
}
