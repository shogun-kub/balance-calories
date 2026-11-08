package com.balancecalories.core.activity

import com.balancecalories.core.time.DayWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The activity values the app aggregates. One enum keeps the mapping table in one place. */
enum class ActivityMetric {
    STEPS,
    ACTIVE_KCAL,
    TOTAL_KCAL,
    DISTANCE_M,
    SLEEP_MINUTES,
    RESTING_HR,
    /** Basal metabolic rate as Health Connect records it, kcal per day. */
    BMR_KCAL,
}

/**
 * One aggregated slice of activity, as returned by Health Connect's bucket queries.
 *
 * [value] is a *total for the bucket* for counts, energy, distance and sleep, an *average* for heart rate,
 * and a *level* for BMR. The distinction is carried by [ActivityMetric] rather than by convention, because
 * summing an average is the kind of mistake that produces a resting heart rate of 40 000.
 */
data class ActivityBucket(
    val metric: ActivityMetric,
    val start: Instant,
    val value: Double,
    /**
     * Which app wrote the data, as Health Connect reports it (`DataOrigin.packageName`), e.g. Gadgetbridge for
     * the band or the phone's own health app for a device baseline.
     *
     * Kept because Health Connect aggregates across **every** origin: without this the app cannot tell a
     * band-measured day from one written by the phone's built-in health app, and both would carry the same
     * `complete` quality. Observed on the Honor: 29 consecutive days of exactly 1858.25 kcal with no steps,
     * which is a device baseline rather than a measurement of a person.
     */
    val origin: String? = null,
)

/** One local day's totals, before any judgement about quality or estimates is applied. */
data class DayActivityTotals(
    /** The apps that contributed to this day, in first-seen order. Empty when nothing was recorded. */
    val origins: Set<String> = emptySet(),
    val steps: Int? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val distanceM: Double? = null,
    val sleepMinutes: Int? = null,
    val restingHr: Int? = null,
    val bmrKcal: Double? = null,
) {
    /** Whether the day has any band-derived energy at all. Steps alone are not a burn figure. */
    val hasEnergy: Boolean get() = activeKcal != null || totalKcal != null

    val hasAnyData: Boolean get() = steps != null || hasEnergy
}

/**
 * Folds Health Connect buckets into **our** local days.
 *
 * Health Connect buckets by calendar day in the device zone; this app's day starts at the profile's
 * day-start hour, which is usually 04:00. So the buckets are fetched with a plain duration and mapped here,
 * by their start instant, using [DayWindow] — the single place that knows what a day is (AGENTS.md rule 12).
 *
 * **Known edge.** Buckets are attributed by their start, so a bucket that spans a day boundary is counted
 * whole in the earlier day. With hourly buckets and a range that starts on a boundary that only happens
 * around daylight-saving changes, where the fixed bucket grid drifts an hour against the local clock: at most
 * one hour of activity can land on the neighbouring day, twice a year. Recorded in docs/03-architecture.md
 * rather than papered over with a rounding rule that would be wrong elsewhere.
 */
object ActivityBucketMapper {

    fun map(
        buckets: List<ActivityBucket>,
        zone: ZoneId,
        dayStartHour: Int,
    ): Map<LocalDate, DayActivityTotals> {
        val byDay = HashMap<LocalDate, MutableDay>()
        val heartRateWeighted = HashMap<LocalDate, Pair<Double, Int>>()

        for (bucket in buckets) {
            val date = DayWindow.dateOf(bucket.start, zone, dayStartHour)
            val day = byDay.getOrPut(date) { MutableDay() }
            bucket.origin?.let { day.origins += it }
            when (bucket.metric) {
                ActivityMetric.STEPS -> day.steps = (day.steps ?: 0.0) + bucket.value
                ActivityMetric.ACTIVE_KCAL -> day.activeKcal = (day.activeKcal ?: 0.0) + bucket.value
                ActivityMetric.TOTAL_KCAL -> day.totalKcal = (day.totalKcal ?: 0.0) + bucket.value
                ActivityMetric.DISTANCE_M -> day.distanceM = (day.distanceM ?: 0.0) + bucket.value
                ActivityMetric.SLEEP_MINUTES -> day.sleepMinutes = (day.sleepMinutes ?: 0.0) + bucket.value
                ActivityMetric.RESTING_HR -> {
                    // An average of averages: weight each bucket by its own sample count so a single reading
                    // does not carry the same weight as a full hour.
                    val (sum, count) = heartRateWeighted[date] ?: (0.0 to 0)
                    heartRateWeighted[date] = (sum + bucket.value) to (count + 1)
                }
                ActivityMetric.BMR_KCAL -> day.bmrKcal = bucket.value
            }
        }

        return byDay.mapValues { (date, day) ->
            DayActivityTotals(
                origins = day.origins,
                steps = day.steps?.let { kotlin.math.round(it).toInt() },
                activeKcal = day.activeKcal,
                totalKcal = day.totalKcal,
                distanceM = day.distanceM,
                sleepMinutes = day.sleepMinutes?.let { kotlin.math.round(it).toInt() },
                restingHr = heartRateWeighted[date]?.let { (sum, count) ->
                    if (count == 0) null else kotlin.math.round(sum / count).toInt()
                },
                bmrKcal = day.bmrKcal,
            )
        }
    }

    /** Bucket duration for a read: hourly is fine-grained enough for day totals and cheap for 30 days. */
    const val BUCKET_MINUTES = 60L

    private class MutableDay {
        val origins = LinkedHashSet<String>()
        var steps: Double? = null
        var activeKcal: Double? = null
        var totalKcal: Double? = null
        var distanceM: Double? = null
        var sleepMinutes: Double? = null
        var bmrKcal: Double? = null
    }
}
