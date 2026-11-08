package com.balancecalories.core.energy

import java.time.LocalDate

/**
 * The weight trend: one number per day that does not jump because the scale did.
 *
 * Body weight moves by a kilogram with water and salt intake, so a chart of raw weigh-ins is mostly noise with a
 * slope somewhere inside it. An exponentially weighted moving average keeps the slope and drops the noise.
 *
 * **Per measurement, not per day.** The smoothing factor is applied once per weigh-in. A gap of four days does
 * not make the following reading count four times: it is one observation of a person whose weight is a slow
 * variable. This is the same choice the "Hacker's Diet" trend makes, and it is the reason a single salty dinner
 * cannot be read as a week of progress.
 */
object WeightTrend {

    /** Smoothing strength: each new weigh-in moves the trend by a quarter of its difference. */
    const val ALPHA = 0.25

    /**
     * Runs the EWMA over [points], which may be unordered and may skip days.
     *
     * Returns one `(date, trend)` pair per weigh-in, in the same order as the input was sorted, so a chart can
     * draw the trend through the days a person actually weighed themselves.
     */
    fun ewma(points: List<Pair<LocalDate, Double>>, alpha: Double = ALPHA): List<Pair<LocalDate, Double>> {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0,1], was $alpha" }
        val sorted = points.sortedBy { it.first }
        val out = ArrayList<Pair<LocalDate, Double>>(sorted.size)
        var trend: Double? = null
        for ((date, weight) in sorted) {
            val previous = trend
            trend = if (previous == null) weight else previous + alpha * (weight - previous)
            out += date to trend
        }
        return out
    }

    /** The most recent trend value, which is what a "current weight" line should show. */
    fun latest(points: List<Pair<LocalDate, Double>>, alpha: Double = ALPHA): Double? =
        ewma(points, alpha).lastOrNull()?.second
}

/**
 * A week in one glance: did the plan hold, and did the scale agree with the arithmetic.
 *
 * The interesting number is the disagreement between [predictedChangeKg] and [actualChangeKg]. Both cannot be
 * right: if the app thinks a fortnight of −500 kcal days costs a kilogram and the scale did not move, then either
 * the logging is optimistic or the expenditure is lower than assumed — and that is precisely the evidence the
 * adaptive TDEE uses.
 */
data class WeeklyRecap(
    val days: Int,
    val loggedDays: Int,
    val averageBalanceKcal: Double?,
    /** Total balance ÷ kcal per kg: what the food log implies the week cost. */
    val predictedChangeKg: Double?,
    /** First trend value to last, over the same window. */
    val actualChangeKg: Double?,
    /** Consecutive days with a food log, counted back from the most recent day. */
    val streakDays: Int,
) {
    /** Prediction and reality, in kilograms of body mass. Null when either side is missing. */
    val disagreementKg: Double?
        get() {
            val predicted = predictedChangeKg ?: return null
            val actual = actualChangeKg ?: return null
            return actual - predicted
        }
}

/** Builds a [WeeklyRecap] from a window of days, newest first or oldest first — order does not matter. */
object RecapCalculator {

    /**
     * [weightTrend] is expected from [WeightTrend.ewma] over a longer period than the window, so the first recap
     * day does not start its own trend from scratch.
     */
    fun recap(
        days: List<DayEnergy>,
        weightTrend: List<Pair<LocalDate, Double>>,
    ): WeeklyRecap {
        val logged = days.filter { it.logged }
        val totalBalance = if (logged.isEmpty()) null else logged.sumOf { it.balanceKcal }
        val predicted = totalBalance?.div(AdaptiveTdee.KCAL_PER_KG)

        val from = days.minOfOrNull { it.date }
        val to = days.maxOfOrNull { it.date }
        val trendInWindow = if (from == null || to == null) {
            emptyList()
        } else {
            weightTrend.filter { !it.first.isBefore(from) && !it.first.isAfter(to) }
        }
        val actual = if (trendInWindow.size >= 2) {
            trendInWindow.last().second - trendInWindow.first().second
        } else {
            null
        }

        return WeeklyRecap(
            days = days.size,
            loggedDays = logged.size,
            averageBalanceKcal = if (logged.isEmpty()) null else totalBalance!! / logged.size,
            predictedChangeKg = predicted,
            actualChangeKg = actual,
            streakDays = streak(days),
        )
    }

    /** Consecutive logged days ending at the most recent day in [days]. */
    fun streak(days: List<DayEnergy>): Int {
        val ordered = days.sortedByDescending { it.date }
        var count = 0
        for (day in ordered) {
            if (!day.logged) break
            count++
        }
        return count
    }
}

/** One day of the energy ledger, as the recap needs it: what was eaten, what was burned, and whether it counted. */
data class DayEnergy(
    val date: LocalDate,
    val intakeKcal: Double,
    val burnKcal: Double,
    val logged: Boolean = true,
) {
    val balanceKcal: Double get() = intakeKcal - burnKcal
}

/**
 * How far away the goal weight is at the current rate.
 *
 * Framed as an estimate everywhere it is shown: a projection assumes the rate holds, and rates do not hold. The
 * guards exist so the app does not print "1 340 days" or a date in 2031 — a number nobody believes is a number
 * nobody acts on.
 */
object Projection {

    /** Past this, the honest answer is "no projection", not a date three years out. */
    const val MAX_HORIZON_DAYS = 730

    /**
     * Days to move from [currentKg] to [goalKg] at [weeklyRateKg] (negative = losing).
     *
     * Null when the goal is already met, when the rate is zero, when the rate moves away from the goal, or when
     * the horizon exceeds [MAX_HORIZON_DAYS].
     */
    fun daysToGoal(currentKg: Double, goalKg: Double, weeklyRateKg: Double): Int? {
        if (weeklyRateKg == 0.0) return null
        val remaining = goalKg - currentKg
        if (remaining == 0.0) return null
        // A rate and a goal must point the same way, or the projection is arithmetic about nothing.
        if (remaining > 0.0 && weeklyRateKg < 0.0) return null
        if (remaining < 0.0 && weeklyRateKg > 0.0) return null

        val days = kotlin.math.abs(remaining / weeklyRateKg) * 7.0
        if (days > MAX_HORIZON_DAYS) return null
        return kotlin.math.ceil(days).toInt()
    }

    fun dateToGoal(today: LocalDate, days: Int): LocalDate = today.plusDays(days.toLong())
}
