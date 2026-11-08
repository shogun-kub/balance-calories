package com.balancecalories.core.energy

import java.time.LocalDate

/**
 * One day of evidence for an adaptive TDEE: what was eaten and what the scale said.
 *
 * Both are nullable because a day with no food log is not a zero-calorie day, and a day without a weigh-in is
 * not a day at the previous weight. Treating either as zero is the mistake that makes an adaptive estimate
 * drift by hundreds of calories.
 */
data class AdaptiveDay(
    val date: LocalDate,
    val intakeKcal: Double? = null,
    val weightKg: Double? = null,
)

/** How much the adaptive estimate can be trusted, in the same three-step spirit as the activity quality. */
enum class TdeeConfidence {
    /** Too little evidence to change anything; the formula TDEE stands. */
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * An adaptive TDEE derived from what was actually eaten and what the scale actually did.
 *
 * The arithmetic is deliberately simple and boring: over a window, the energy balance is
 * `weight change × kcalPerKg`, so the total daily expenditure implied by that balance is
 *
 * ```
 * tdee = average intake − (weight change × kcalPerKg / days)
 * ```
 *
 * A loss (negative change) therefore *raises* the estimate above intake, which is the whole point: the person
 * spent more than they ate and the difference shows on the scale.
 *
 * **Why this is better than the PAL formula.** The formula multiplies BMR by a self-reported activity level;
 * the scale and the food log are measurements of the same two things the balance needs. This estimate absorbs
 * every error — a mis-measured portion, an optimistic BMR, a band that over-counts — into one number, provided
 * the errors are not systematically one-directional.
 *
 * **Why it still refuses to answer.** Rather than fitting a number to thin evidence, every guard below returns
 * `null` with a reason. A wrong target is worse than no change: it is invisible, unarguable, and eventually
 * blamed on the app.
 */
object AdaptiveTdee {

    /** Energy in a kilogram of body mass. A convention, not a measurement: 7700 kcal/kg is the usual figure. */
    const val KCAL_PER_KG = 7700.0

    /** Days in the window. Two weeks smooths a weekend without hiding a real trend. */
    const val WINDOW_DAYS = 14

    /** At least this many of the window's days must have a food log, or the average intake is a guess. */
    const val MIN_LOGGED_DAYS = 10

    /** At least this many weigh-ins, at least [MIN_WEIGHT_SPAN_DAYS] apart, or the trend is noise. */
    const val MIN_WEIGHT_POINTS = 3
    const val MIN_WEIGHT_SPAN_DAYS = 7L

    /**
     * The most body mass a person plausibly gains or loses per week. Water and glycogen move kilograms in days,
     * which is why the guard is a rate over the span and not an absolute change: a real diet cannot produce
     * 2 kg/week for two weeks, but a scale on a rug can report it by accident.
     */
    const val MAX_WEEKLY_CHANGE_KG = 2.0

    /** Plausibility bounds as a multiple of BMR. Outside them the data is wrong, not the person. */
    const val MIN_BMR_FACTOR = 1.0
    const val MAX_BMR_FACTOR = 2.5

    /** The largest single update applied to a target, so one odd fortnight cannot swing the plan. */
    const val MAX_ADJUSTMENT_KCAL = 150.0

    /**
     * Why an estimate was refused. Named rather than a bare null so the UI can say what is missing and the
     * debug screen can show it.
     */
    enum class Refusal {
        NOT_ENOUGH_DAYS,
        NOT_ENOUGH_LOGGED_DAYS,
        NOT_ENOUGH_WEIGH_INS,
        WEIGH_INS_TOO_CLOSE,
        IMPLAUSIBLE_WEIGHT_CHANGE,
        IMPLAUSIBLE_TDEE,
    }

    data class Estimate(
        val tdeeKcal: Double?,
        val confidence: TdeeConfidence,
        val averageIntakeKcal: Double?,
        val weightChangeKg: Double?,
        val loggedDays: Int,
        val weighIns: Int,
        val refusal: Refusal? = null,
    ) {
        val available: Boolean get() = tdeeKcal != null
    }

    /**
     * Fits an estimate over [days], which must be the window in chronological order.
     *
     * [bmrKcal] is only used for the plausibility bounds — it is not part of the arithmetic, because an
     * estimate that leans on BMR would inherit the same self-reported error the adaptive estimate exists to
     * escape.
     */
    fun estimate(days: List<AdaptiveDay>, bmrKcal: Double): Estimate {
        val logged = days.mapNotNull { it.intakeKcal }
        val weighIns = days.mapNotNull { day -> day.weightKg?.let { day.date to it } }

        if (days.size < MIN_LOGGED_DAYS) return refuse(Refusal.NOT_ENOUGH_DAYS, logged, weighIns)
        if (logged.size < MIN_LOGGED_DAYS) return refuse(Refusal.NOT_ENOUGH_LOGGED_DAYS, logged, weighIns)
        if (weighIns.size < MIN_WEIGHT_POINTS) return refuse(Refusal.NOT_ENOUGH_WEIGH_INS, logged, weighIns)

        val spanDays = weighIns.last().first.toEpochDay() - weighIns.first().first.toEpochDay()
        if (spanDays < MIN_WEIGHT_SPAN_DAYS) return refuse(Refusal.WEIGH_INS_TOO_CLOSE, logged, weighIns)

        // Average a few points at each end to blunt a single bad weigh-in — but never let the two groups
        // overlap, or three weigh-ins would compare the set with itself and report a change of zero.
        val groupSize = if (weighIns.size >= 6) 3 else maxOf(1, weighIns.size / 2)
        val firstWeight = weighIns.take(groupSize).map { it.second }.average()
        val lastWeight = weighIns.takeLast(groupSize).map { it.second }.average()
        val changeKg = lastWeight - firstWeight

        val weeks = spanDays / 7.0
        if (weeks <= 0.0 || kotlin.math.abs(changeKg) / weeks > MAX_WEEKLY_CHANGE_KG) {
            return refuse(Refusal.IMPLAUSIBLE_WEIGHT_CHANGE, logged, weighIns)
        }

        val averageIntake = logged.average()
        val tdee = averageIntake - (changeKg * KCAL_PER_KG / spanDays.toDouble())

        if (bmrKcal > 0.0 && (tdee < bmrKcal * MIN_BMR_FACTOR || tdee > bmrKcal * MAX_BMR_FACTOR)) {
            return refuse(Refusal.IMPLAUSIBLE_TDEE, logged, weighIns)
        }

        return Estimate(
            tdeeKcal = tdee,
            confidence = confidenceFor(loggedDays = logged.size, windowDays = days.size, weighIns = weighIns.size),
            averageIntakeKcal = averageIntake,
            weightChangeKg = changeKg,
            loggedDays = logged.size,
            weighIns = weighIns.size,
        )
    }

    /**
     * Confidence from how complete the evidence is, never from how pleasing the answer looks.
     *
     * A flattering TDEE fitted to four logged days is still four logged days.
     */
    fun confidenceFor(loggedDays: Int, windowDays: Int, weighIns: Int): TdeeConfidence {
        if (windowDays <= 0) return TdeeConfidence.NONE
        val coverage = loggedDays.toDouble() / windowDays
        val weighInsEnough = weighIns >= MIN_WEIGHT_POINTS
        return when {
            coverage >= 0.95 && weighIns >= 7 -> TdeeConfidence.HIGH
            coverage >= 0.8 && weighInsEnough -> TdeeConfidence.MEDIUM
            coverage >= 0.6 && weighInsEnough -> TdeeConfidence.LOW
            else -> TdeeConfidence.NONE
        }
    }

    /**
     * The target an adaptive estimate implies, clamped.
     *
     * [currentTargetKcal] moves by at most [MAX_ADJUSTMENT_KCAL] per update and never below the safety floor,
     * so a strange fortnight nudges the plan instead of rewriting it. Returns [currentTargetKcal] unchanged
     * when there is no estimate or the floor would be violated.
     */
    fun nextTarget(
        currentTargetKcal: Int,
        estimateTdeeKcal: Double?,
        goalRateKgPerWeek: Double,
        bmrKcal: Double,
        sex: Sex,
    ): Int {
        if (estimateTdeeKcal == null) return currentTargetKcal
        val desired = estimateTdeeKcal + goalRateKgPerWeek * KCAL_PER_KG / 7.0
        val floored = maxOf(desired, TargetCalculator.floorKcal(sex, bmrKcal))
        val delta = (floored - currentTargetKcal).coerceIn(-MAX_ADJUSTMENT_KCAL, MAX_ADJUSTMENT_KCAL)
        return Math.round(currentTargetKcal + delta).toInt()
    }

    private fun refuse(
        refusal: Refusal,
        logged: List<Double>,
        weighIns: List<Pair<LocalDate, Double>>,
    ): Estimate = Estimate(
        tdeeKcal = null,
        confidence = TdeeConfidence.NONE,
        averageIntakeKcal = logged.averageOrNull(),
        weightChangeKg = null,
        loggedDays = logged.size,
        weighIns = weighIns.size,
        refusal = refusal,
    )

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}

/** Stored form of [TdeeConfidence] in `target_history.tdee_confidence`. */
fun TdeeConfidence.toStorage(): String = when (this) {
    TdeeConfidence.NONE -> "none"
    TdeeConfidence.LOW -> "low"
    TdeeConfidence.MEDIUM -> "medium"
    TdeeConfidence.HIGH -> "high"
}

/** Reads the stored form; an unknown value is [TdeeConfidence.NONE], the most conservative reading. */
fun tdeeConfidenceFromStorage(raw: String?): TdeeConfidence = when (raw?.trim()?.lowercase()) {
    "low" -> TdeeConfidence.LOW
    "medium" -> TdeeConfidence.MEDIUM
    "high" -> TdeeConfidence.HIGH
    else -> TdeeConfidence.NONE
}