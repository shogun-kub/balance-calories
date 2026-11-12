package com.balancecalories.data.repo

import androidx.room.ColumnInfo
import com.balancecalories.core.activity.ActivityQuality
import com.balancecalories.core.activity.PLATFORM_ORIGIN
import com.balancecalories.core.food.MacroEnergy
import com.balancecalories.core.food.MacroSplit
import com.balancecalories.core.energy.DayEnergy
import com.balancecalories.core.energy.RecapCalculator
import com.balancecalories.core.energy.WeeklyRecap
import com.balancecalories.core.energy.WeightTrend
import com.balancecalories.core.activity.activityQualityFromStorage
import com.balancecalories.core.time.DayWindow
import com.balancecalories.data.db.BalanceCaloriesDatabase
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day as the history screen needs it: what was eaten, what was burned, what the scale said, and how much the
 * burn figure deserves to be trusted.
 *
 * Assembled from three tables rather than stored, because a stored copy would be a fourth thing to keep in step
 * with the other three.
 */
data class DaySummary(
    val date: LocalDate,
    val intakeKcal: Double,
    val logged: Boolean,
    val burnKcal: Double,
    val burnIsMeasured: Boolean,
    val quality: ActivityQuality,
    val weightKg: Double?,
    /** Grams eaten that day; zero when the day has no log, which the chart reads as `logged == false`. */
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbsG: Double = 0.0,
) {
    /** Positive = surplus. The sign convention of the whole app. */
    val balanceKcal: Double get() = intakeKcal - burnKcal

    /** The energy behind the day's macros, split for the intake bar. See `MacroEnergy` in `:core`. */
    val macroSplit: MacroSplit
        get() = MacroEnergy.split(proteinG, fatG, carbsG, intakeKcal)
}

/** One row of `food_entry` summed per day, before the day's burn and weight are attached. */
data class DayMacroTotals(
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "kcal") val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
)

/**
 * Reads a window of days for history, charts and the weekly recap.
 *
 * **One BMR for the window.** The burn figure needs a BMR for each past day, and the app only stores the target
 * in force today. Weight history exists, but recomputing BMR per day from historical weights would make the
 * chart jump whenever a weigh-in moved, and the difference is a few kcal. The caller passes today's BMR and the
 * window shares it — a documented approximation rather than a silent one.
 */
class HistoryRepository(
    private val database: BalanceCaloriesDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Canonical weight per day, smoothed through [WeightTrend], for the chart and the recap. */
    suspend fun weightTrend(
        profileId: Long,
        today: LocalDate,
        days: Int,
    ): List<Pair<LocalDate, Double>> {
        val from = today.minusDays(days - 1L)
        val raw = CanonicalWeights(database).perDay(profileId, from, today)
        return WeightTrend.ewma(raw.toList())
    }

    /**
     * The weigh-ins themselves, one per day, unsmoothed.
     *
     * A chart wants the trend line; a list of readings wants the readings. Showing the smoothed series as
     * "recent weigh-ins" would print a number the scale never displayed.
     */
    suspend fun weightPoints(
        profileId: Long,
        today: LocalDate,
        days: Int,
    ): List<Pair<LocalDate, Double>> {
        val from = today.minusDays(days - 1L)
        return CanonicalWeights(database).perDay(profileId, from, today)
            .toSortedMap()
            .toList()
    }

    /**
     * The week in one glance. The trend is smoothed over twice the window so the first recap day does not start its
     * own baseline from a single weigh-in.
     */
    suspend fun recap(
        profileId: Long,
        today: LocalDate,
        days: Int,
        bmrKcal: Double,
        palKcal: Double,
    ): WeeklyRecap {
        val window = summaries(profileId, today, days, bmrKcal, palKcal)
        val trend = weightTrend(profileId, today, days * 2)
        return RecapCalculator.recap(
            days = window.map {
                DayEnergy(
                    date = it.date,
                    intakeKcal = it.intakeKcal,
                    burnKcal = it.burnKcal,
                    logged = it.logged,
                )
            },
            weightTrend = trend,
        )
    }

    suspend fun summaries(
        profileId: Long,
        today: LocalDate,
        days: Int,
        bmrKcal: Double,
        palKcal: Double,
    ): List<DaySummary> {
        require(days >= 1) { "a history window must cover at least one day, was $days" }
        val activities = database.dailyActivityDao()
        val food = database.foodEntryDao()
        val body = database.bodyMeasurementDao()

        val from = today.minusDays(days - 1L)
        val weights = body.weightsBetween(profileId, from.toString(), today.toString())
            .filter { it.weightKg != null }
            .associate { it.localDate to it.weightKg!! }

        // One query for the window: the chart asks for 90 days and a per-day loop would be 90 round trips.
        val macrosByDay = food.macroTotalsBetween(profileId, from.toString(), today.toString())
            .associateBy { it.localDate }

        val summaries = ArrayList<DaySummary>(days)
        var date = today
        while (!date.isAfter(today) && !date.isBefore(from)) {
            val dayKey = date.toString()
            val macros = macrosByDay[dayKey]
            val logged = macros != null
            val intake = macros?.kcal ?: 0.0
            val row = activities.forDay(profileId, dayKey)
            val hasEnergy = row != null && (row.totalKcal != null || row.activeKcal != null)
            // Same rule as the Today screen: Health Connect's own platform layer writes a constant baseline
            // (empty origin on the Honor, `_platform` on the Redmi), and a baseline is not a measurement.
            val measured = hasEnergy && row!!.dataOrigins.orEmpty()
                .split(',')
                .any { it.isNotBlank() && it != PLATFORM_ORIGIN }

            val burn = when {
                measured -> row!!.totalKcal ?: (bmrKcal + row.activeKcal!!)
                else -> palKcal
            }
            val quality = when {
                measured -> activityQualityFromStorage(row!!.quality)
                row != null -> activityQualityFromStorage(row.quality)
                else -> ActivityQuality.NO_BAND
            }

            summaries += DaySummary(
                date = date,
                intakeKcal = intake,
                logged = logged,
                burnKcal = burn,
                burnIsMeasured = measured,
                quality = quality,
                weightKg = weights[dayKey],
                proteinG = macros?.proteinG ?: 0.0,
                fatG = macros?.fatG ?: 0.0,
                carbsG = macros?.carbsG ?: 0.0,
            )
            date = date.minusDays(1)
        }
        return summaries
    }

}

