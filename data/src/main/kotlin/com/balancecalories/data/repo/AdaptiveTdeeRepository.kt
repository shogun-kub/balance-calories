package com.balancecalories.data.repo

import com.balancecalories.core.energy.AdaptiveDay
import com.balancecalories.core.energy.AdaptiveTdee
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.energy.TdeeConfidence
import com.balancecalories.core.energy.TdeeConfidence.LOW
import com.balancecalories.core.energy.TdeeConfidence.MEDIUM
import com.balancecalories.core.energy.toStorage
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.time.DayWindow
import com.balancecalories.data.db.BalanceCaloriesDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Gathers the evidence for an adaptive TDEE and, when it is good enough, rewrites the daily target.
 *
 * **Why the app collects this at all.** The formula TDEE is BMR × a self-reported activity level. The scale and
 * the food log are measurements of the same two quantities the balance already needs, so a fortnight of them
 * beats a questionnaire — provided the evidence is complete. The collection here is deliberately strict: a day
 * without a food log is missing, not 0 kcal, and two weigh-ins on one day are one point.
 *
 * **Why the window ends yesterday.** Today's intake is a partial day by definition; including it would drag the
 * average down every morning and produce a target that creeps upward as the day is logged.
 */
class AdaptiveTdeeRepository(
    private val database: BalanceCaloriesDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val food = database.foodEntryDao()
    private val body = database.bodyMeasurementDao()
    private val targets = TargetRepository(database)

    /**
     * Everything the UI needs to explain itself: the evidence, the estimate, and what it would do to the target.
     */
    data class Outcome(
        val estimate: AdaptiveTdee.Estimate,
        val from: LocalDate,
        val to: LocalDate,
        val currentTargetKcal: Int,
        val proposedTargetKcal: Int,
        val days: List<AdaptiveDay>,
    ) {
        val changesTarget: Boolean get() = proposedTargetKcal != currentTargetKcal
    }

    suspend fun outcome(
        profile: Profile,
        bmrKcal: Double,
        currentTargetKcal: Int,
        zone: ZoneId,
        windowDays: Int = AdaptiveTdee.WINDOW_DAYS,
    ): Outcome {
        val now = clock()
        val today = DayWindow.dateOf(Instant.ofEpochMilli(now), zone, profile.dayStartHour)
        val to = today.minusDays(1)
        val from = to.minusDays(windowDays - 1L)

        val weightsByDay = canonicalWeights(profile.id, from, to)
        val days = ArrayList<AdaptiveDay>(windowDays)
        var date = from
        while (!date.isAfter(to)) {
            val logged = food.countForDay(profile.id, date.toString()) > 0
            days += AdaptiveDay(
                date = date,
                // A day with no entries is missing evidence, not a zero-calorie day.
                intakeKcal = if (logged) food.kcalForDay(profile.id, date.toString()) else null,
                weightKg = weightsByDay[date],
            )
            date = date.plusDays(1)
        }

        val estimate = AdaptiveTdee.estimate(days, bmrKcal)
        val proposed = AdaptiveTdee.nextTarget(
            currentTargetKcal = currentTargetKcal,
            estimateTdeeKcal = estimate.tdeeKcal,
            goalRateKgPerWeek = profile.goalRateKgPerWeek,
            bmrKcal = bmrKcal,
            sex = profile.sex,
        )

        return Outcome(
            estimate = estimate,
            from = from,
            to = to,
            currentTargetKcal = currentTargetKcal,
            proposedTargetKcal = proposed,
            days = days,
        )
    }

    /**
     * Writes the proposed target, but only when the evidence clears [TdeeConfidence.MEDIUM].
     *
     * A `LOW` estimate is shown and not applied: with a third of the fortnight logged, the number says more
     * about the logging habit than about the metabolism, and silently moving someone's target on that basis is
     * exactly the kind of confident-wrong the product rules forbid.
     */
    suspend fun apply(outcome: Outcome, profileId: Long, on: LocalDate): Boolean {
        val confidence = outcome.estimate.confidence
        if (confidence != MEDIUM && confidence != TdeeConfidence.HIGH) return false
        if (!outcome.changesTarget) return false

        targets.recordAdaptive(
            profileId = profileId,
            on = on,
            kcal = outcome.proposedTargetKcal,
            tdeeKcal = outcome.estimate.tdeeKcal,
            confidence = confidence.toStorage(),
        )
        return true
    }

    /**
     * One weight per day, with a fixed source preference.
     *
     * Several writers can report the same morning: a manual entry, a connected scale, Gadgetbridge relaying the
     * band, and Health Connect. They disagree by a few hundred grams, and picking arbitrarily would make the
     * weight trend jump between sources. Manual wins because the person chose to correct it; then a real scale;
     * then whatever the band exported; then Health Connect as the broadest net.
     */
    private suspend fun canonicalWeights(
        profileId: Long,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Double> {
        val rows = body.weightsBetween(profileId, from.toString(), to.toString())
        val best = LinkedHashMap<LocalDate, Pair<Int, Double>>()
        for (row in rows) {
            val weight = row.weightKg ?: continue
            val date = runCatching { LocalDate.parse(row.localDate) }.getOrNull() ?: continue
            val rank = sourceRank(row.source)
            val existing = best[date]
            if (existing == null || rank < existing.first) best[date] = rank to weight
        }
        return best.mapValues { it.value.second }
    }

    private fun sourceRank(source: String): Int = when (source) {
        BodyMeasurementRepository.SOURCE_MANUAL -> 0
        BodyMeasurementRepository.SOURCE_SCALE -> 1
        BodyMeasurementRepository.SOURCE_GADGETBRIDGE -> 2
        BodyMeasurementRepository.SOURCE_HEALTH_CONNECT -> 3
        else -> 4
    }
}
