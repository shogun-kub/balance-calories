package com.balancecalories.core.energy

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns body data and a goal rate into a daily kcal target, with the safety rails from
 * docs/02-research.md §5.6 and docs/03-architecture.md §8.3.
 *
 * Deliberately **not** an "eat back your exercise" calculator: the target is derived from total
 * expenditure, so the band's active calories feed the estimate rather than being added to today's
 * budget (ADR-004).
 */
object TargetCalculator {

    /** Energy in one kilogram of body fat, kcal. An approximation: see Hall (2008). */
    const val KCAL_PER_KG_FAT = 7700.0
    const val DAYS_PER_WEEK = 7.0

    /** Never remove more than a quarter of expenditure. */
    const val MAX_DEFICIT_FRACTION = 0.25

    /** Absolute floors below which no app should suggest eating. */
    const val MIN_KCAL_FEMALE = 1200.0
    const val MIN_KCAL_MALE = 1500.0

    /** The floor never sits below this multiple of BMR. */
    const val BMR_FLOOR_FACTOR = 1.1

    /** Adaptive updates move the target by at most 5% per week. */
    const val MAX_WEEKLY_CHANGE_FRACTION = 0.05

    /**
     * Seeds a target from body data alone.
     *
     * @param goalRateKgPerWeek negative to lose, positive to gain; e.g. `-0.5`
     * @param on evaluation date, used to age the profile
     */
    fun seed(profile: EnergyProfile, goalRateKgPerWeek: Double, on: LocalDate): EnergyTarget {
        require(goalRateKgPerWeek.isFinite() && abs(goalRateKgPerWeek) <= 2.0) {
            "goalRateKgPerWeek must be within ±2 kg/week, was $goalRateKgPerWeek"
        }

        val bmr = Bmr.mifflinStJeor(
            sex = profile.sex,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            ageYears = profile.ageAt(on),
        )
        val tdee = bmr * profile.activityLevel.pal

        // Positive when the user wants to lose: kcal/day to remove.
        val requestedDeficit = -goalRateKgPerWeek * KCAL_PER_KG_FAT / DAYS_PER_WEEK
        val cap = tdee * MAX_DEFICIT_FRACTION

        var deficit = requestedDeficit
        var limit = TargetLimit.NONE
        if (deficit > cap) {
            deficit = cap
            limit = TargetLimit.DEFICIT_CAP
        } else if (deficit < -cap) {
            deficit = -cap
            limit = TargetLimit.DEFICIT_CAP
        }

        var target = tdee - deficit
        val floor = floorKcal(profile.sex, bmr)
        if (target < floor) {
            target = floor
            limit = TargetLimit.FLOOR
        }

        val kcal = target.roundToInt()
        return EnergyTarget(
            kcal = kcal,
            bmrKcal = bmr,
            tdeeKcal = tdee,
            deficitKcal = tdee - kcal,
            limit = limit,
        )
    }

    /** `max(1.1 × BMR, 1200 female / 1500 male)`. */
    fun floorKcal(sex: Sex, bmrKcal: Double): Double {
        val absolute = when (sex) {
            Sex.FEMALE -> MIN_KCAL_FEMALE
            Sex.MALE -> MIN_KCAL_MALE
        }
        return maxOf(bmrKcal * BMR_FLOOR_FACTOR, absolute)
    }

    /**
     * Clamps an adaptive recalculation to ±[maxFraction] of the previous target, so a noisy week
     * cannot swing the budget. Returns [proposedKcal] unchanged when there is no previous target.
     */
    fun limitWeeklyChange(
        previousKcal: Int,
        proposedKcal: Int,
        maxFraction: Double = MAX_WEEKLY_CHANGE_FRACTION,
    ): Int {
        require(maxFraction > 0.0) { "maxFraction must be positive, was $maxFraction" }
        if (previousKcal <= 0) return proposedKcal
        val previous = previousKcal.toDouble()
        val maxDelta = previous * maxFraction
        val delta = (proposedKcal - previousKcal).toDouble().coerceIn(-maxDelta, maxDelta)
        return (previous + delta).roundToInt()
    }
}
