package com.balancecalories.data.repo

import com.balancecalories.core.energy.EnergyTarget
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.BodyMeasurementEntity
import com.balancecalories.data.db.entity.TargetHistoryEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Weight readings from any origin, and the latest one the calorie maths should use.
 *
 * Readings are never merged across sources: a Health Connect record, a manual entry and a scale all
 * keep their own row, so the app can prefer a typed value over a synced one instead of letting the last
 * writer win (docs/02-research.md §1.6.1 — a stray foreign weight once landed in Health Connect).
 */
class BodyMeasurementRepository(
    private val database: BalanceCaloriesDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val dao = database.bodyMeasurementDao()

    suspend fun record(
        profileId: Long,
        weightKg: Double,
        localDate: LocalDate,
        source: String,
        measuredAt: Long = clock(),
    ) {
        dao.upsert(
            BodyMeasurementEntity(
                profileId = profileId,
                measuredAt = measuredAt,
                localDate = localDate.toString(),
                weightKg = weightKg,
                source = source,
            ),
        )
    }

    /** Latest weight regardless of source; null when nothing has ever been recorded. */
    suspend fun latestWeightKg(profileId: Long): Double? = dao.latestWeightKg(profileId)

    /** Latest weight that the user typed, which outranks a synced value when both exist. */
    suspend fun latestManualWeightKg(profileId: Long): Double? =
        dao.latestFrom(profileId, SOURCE_MANUAL)?.weightKg

    fun observeBetween(profileId: Long, from: LocalDate, to: LocalDate): Flow<List<BodyMeasurementEntity>> =
        dao.observeBetween(profileId, from.toString(), to.toString())

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_HEALTH_CONNECT = "health_connect"
        const val SOURCE_GADGETBRIDGE = "gadgetbridge"
        const val SOURCE_SCALE = "scale"
    }
}

/**
 * Writes the calorie target into history, so a later profile edit cannot silently rewrite what the app
 * told the user last week. Only the seed mode exists today; adaptive and pinned arrive with M2/M3.
 */
class TargetRepository(
    private val database: BalanceCaloriesDatabase,
) {

    private val dao = database.targetHistoryDao()

    suspend fun recordSeed(profile: Profile, weightKg: Double, on: LocalDate): EnergyTarget {
        val target = TargetCalculator.seed(
            profile = profile.toEnergyProfile(weightKg),
            goalRateKgPerWeek = profile.goalRateKgPerWeek,
            on = on,
        )
        dao.upsert(
            TargetHistoryEntity(
                profileId = profile.id,
                effectiveFrom = on.toString(),
                kcal = target.kcal,
                tdeeKcal = target.tdeeKcal,
                tdeeConfidence = CONFIDENCE_NONE,
                mode = MODE_SEED,
            ),
        )
        return target
    }

    suspend fun effectiveOn(profileId: Long, on: LocalDate): TargetHistoryEntity? =
        dao.effectiveOn(profileId, on.toString())

    companion object {
        const val MODE_SEED = "seed"
        const val MODE_ADAPTIVE = "adaptive"
        const val MODE_PINNED = "pinned"
        const val CONFIDENCE_NONE = "none"
    }
}
