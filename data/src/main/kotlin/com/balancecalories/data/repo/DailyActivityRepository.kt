package com.balancecalories.data.repo

import com.balancecalories.core.activity.ActivityBucketMapper
import com.balancecalories.core.activity.ActivityQuality
import com.balancecalories.core.activity.ActivityQualityRules
import com.balancecalories.core.activity.DailyBurn
import com.balancecalories.core.activity.PLATFORM_ORIGIN
import com.balancecalories.core.activity.activityQualityFromStorage
import com.balancecalories.core.activity.toStorage
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.time.DayWindow
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.DailyActivityEntity
import com.balancecalories.data.health.ActivityRead
import com.balancecalories.data.health.ActivitySource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps one `daily_activity` row per profile per local day, and answers "how much did I burn".
 *
 * The rows are **recomputed and upserted**, never appended: the balance needs one aggregate per day, and a
 * re-sync that could not correct yesterday would be a bug waiting to be blamed on the band.
 *
 * Day boundaries come from [DayWindow] and nowhere else (AGENTS.md rule 12). Today's window is clipped to
 * *now*, because reading into the future is meaningless and a partial day must not be mistaken for a
 * complete one — that is what [ActivityQualityRules] uses `isToday` for.
 */
class DailyActivityRepository(
    private val database: BalanceCaloriesDatabase,
    private val source: ActivitySource,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val activities = database.dailyActivityDao()
    private val settings = database.settingsDao()

    /** What a sync attempt did, so the UI can say something true instead of "synced". */
    sealed interface SyncResult {
        data class Synced(
            val days: Int,
            val daysWithEnergy: Int,
            val lastSyncAt: Long,
        ) : SyncResult

        /** Health Connect exists but the read permissions were never granted. Fixable by the user. */
        data object NoPermission : SyncResult

        /** No usable Health Connect on this device. Not fixable; the app runs in no-band mode. */
        data object Unavailable : SyncResult

        data class Failed(val reason: String) : SyncResult
    }

    /**
     * Reads [days] days ending today and upserts their rows.
     *
     * A day with no data still gets a row with `quality = no_band`: "we looked and found nothing" is an
     * observation worth storing, and it is what stops the UI from silently treating an empty day as zero burn.
     */
    suspend fun sync(profile: Profile, days: Int = 30, zone: ZoneId): SyncResult {
        require(days >= 1) { "a sync must cover at least one day, was $days" }
        val now = clock()
        val hour = profile.dayStartHour
        val today = DayWindow.dateOf(Instant.ofEpochMilli(now), zone, hour)
        val from = today.minusDays(days - 1L)

        val rangeStart = DayWindow.startOf(from, zone, hour)
        val todayEnd = DayWindow.endOf(today, zone, hour)
        val rangeEnd = minOf(todayEnd.toEpochMilli(), now)

        val read = source.read(Instant.ofEpochMilli(rangeStart.toEpochMilli()), Instant.ofEpochMilli(rangeEnd))
        val buckets = when (read) {
            is ActivityRead.Values -> read.buckets
            ActivityRead.NoPermission -> return SyncResult.NoPermission
            ActivityRead.Unavailable -> return SyncResult.Unavailable
            is ActivityRead.Failed -> return SyncResult.Failed(read.reason)
        }

        val totalsByDay = ActivityBucketMapper.map(buckets, zone, hour)
        var withEnergy = 0

        var date = from
        while (!date.isAfter(today)) {
            val totals = totalsByDay[date]
            val windowEnd = DayWindow.endOf(date, zone, hour).toEpochMilli()
            val quality = ActivityQualityRules.classify(
                hasMeasuredData = totals?.hasEnergy == true,
                isToday = date == today,
                syncedAfterWindowEnd = now >= windowEnd,
                hasAttributedOrigin = totals?.hasAttributedOrigin ?: true,
            )
            if (quality == ActivityQuality.COMPLETE || quality == ActivityQuality.PARTIAL) withEnergy++

            activities.upsert(
                DailyActivityEntity(
                    profileId = profile.id,
                    localDate = date.toString(),
                    steps = totals?.steps,
                    activeKcal = totals?.activeKcal,
                    bmrKcal = totals?.bmrKcal,
                    totalKcal = totals?.totalKcal,
                    distanceM = totals?.distanceM,
                    sleepMinutes = totals?.sleepMinutes,
                    restingHr = totals?.restingHr,
                    dataOrigins = totals?.origins?.takeIf { it.isNotEmpty() }?.joinToString(","),
                    quality = quality.toStorage(),
                    updatedAt = now,
                ),
            )
            date = date.plusDays(1)
        }

        setLastSyncAt(profile.id, now)
        return SyncResult.Synced(days = days, daysWithEnergy = withEnergy, lastSyncAt = now)
    }

    /**
     * The day's burn figure.
     *
     * [bmrKcal] and [palKcal] are supplied by the caller (the target maths already computes them from the
     * profile) rather than recalculated here, so the burn and the target can never disagree about BMR.
     */
    suspend fun burn(
        profileId: Long,
        date: LocalDate,
        bmrKcal: Double,
        palKcal: Double,
    ): DailyBurn {
        val row = activities.forDay(profileId, date.toString())
            ?: return DailyBurn.estimated(bmrKcal, palKcal)

        val quality = activityQualityFromStorage(row.quality)
        val hasEnergy = row.totalKcal != null || row.activeKcal != null
        if (!hasEnergy) return DailyBurn.estimated(bmrKcal, palKcal)

        // Energy that no app claims is Health Connect's own platform baseline. Observed twice: a constant
        // 1858.25 kcal/day with an **empty** origin on the Honor X7c, and a constant 1564.5 kcal/day tagged
        // `_platform` on the Redmi Note 8 Pro. Neither is a measurement of this person, so the formula stands in
        // and the screen says so, rather than lending a baseline the authority of a band reading.
        val attributed = row.dataOrigins.orEmpty()
            .split(',')
            .any { it.isNotBlank() && it != PLATFORM_ORIGIN }
        if (!attributed) return DailyBurn.estimated(bmrKcal, palKcal)

        return DailyBurn(
            quality = quality,
            steps = row.steps,
            activeKcal = row.activeKcal,
            totalKcal = row.totalKcal,
            distanceM = row.distanceM,
            sleepMinutes = row.sleepMinutes,
            restingHr = row.restingHr,
            bmrKcal = bmrKcal,
            palKcal = palKcal,
        )
    }

    /** When the last successful sync happened, or null if it never did. Shown as "last synced …". */
    suspend fun lastSyncAt(profileId: Long): Long? =
        settings.value(SYNC_KEY_PREFIX + profileId)?.toLongOrNull()

    private suspend fun setLastSyncAt(profileId: Long, at: Long) {
        settings.put(
            com.balancecalories.data.db.entity.AppSettingEntity(
                key = SYNC_KEY_PREFIX + profileId,
                value = at.toString(),
            ),
        )
    }

    companion object {
        /** One timestamp per profile: two people on one device must not share a sync clock. */
        const val SYNC_KEY_PREFIX = "activity_sync_at_"

        const val DEFAULT_BACKFILL_DAYS = 30
        const val DEFAULT_INCREMENTAL_DAYS = 7
    }
}
