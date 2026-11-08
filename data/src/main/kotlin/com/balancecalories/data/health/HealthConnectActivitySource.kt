package com.balancecalories.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.balancecalories.core.activity.ActivityBucket
import com.balancecalories.core.activity.ActivityBucketMapper
import com.balancecalories.core.activity.ActivityMetric
import java.time.Duration
import java.time.Instant

/**
 * The answer to "what did the band record between these two instants".
 *
 * Four outcomes, not two. "No permission" and "this device has no Health Connect" are different problems with
 * different fixes, and collapsing either into an empty list would make the app claim a person did not move
 * when in fact it was never allowed to look. A read that threw is [Failed], not [Unavailable], so a bug in
 * this class cannot masquerade as a missing platform feature.
 */
sealed interface ActivityRead {
    data class Values(val buckets: List<ActivityBucket>) : ActivityRead
    data object NoPermission : ActivityRead
    data object Unavailable : ActivityRead
    data class Failed(val reason: String) : ActivityRead
}

/** Where activity comes from. An interface so the repository can be tested without Health Connect. */
fun interface ActivitySource {
    suspend fun read(start: Instant, end: Instant): ActivityRead
}

/**
 * Reads activity from Health Connect as hourly buckets.
 *
 * **Why buckets and not one aggregate per day.** This app's day starts at the profile's day-start hour, which
 * is usually 04:00, while Health Connect buckets by calendar day. Asking it for 30 day-shaped answers would
 * either be wrong (calendar days) or cost 30 round trips. One bucketed read covers a whole backfill, and
 * [ActivityBucketMapper] does the day mapping where the rest of the app's day logic lives.
 *
 * All five metrics travel in **one** request: `AggregateGroupByDurationRequest` takes a mixed metric set, and
 * the measured cost of one call is what keeps a 30-day backfill interactive.
 *
 * **Sleep and basal metabolic rate are deliberately not read yet.** Sleep is not part of the energy balance,
 * and the app computes its own BMR from the profile's weight, so a second, device-supplied BMR would be a
 * number with no clear owner. The columns exist; they stay null.
 */
class HealthConnectActivitySource(
    private val client: HealthConnectClient,
) : ActivitySource {

    /** Which permissions the app must be granted before any activity can be read. */
    val permissions: Set<String> = REQUIRED_PERMISSIONS

    /** True when every permission the reads need has been granted. */
    suspend fun hasPermissions(): Boolean =
        runCatching { client.permissionController.getGrantedPermissions().containsAll(permissions) }
            .getOrDefault(false)

    override suspend fun read(start: Instant, end: Instant): ActivityRead {
        if (!hasPermissions()) return ActivityRead.NoPermission
        return runCatching {
            val groups = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = METRICS,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Duration.ofMinutes(ActivityBucketMapper.BUCKET_MINUTES),
                ),
            )

            val buckets = ArrayList<ActivityBucket>(groups.size * METRICS.size)
            for (group in groups) {
                val result = group.result
                // One origin per bucket is the common case; a merged bucket joins them so nothing is lost.
                val origin = result.dataOrigins
                    .map { it.packageName }
                    .sorted()
                    .joinToString(",")
                    .ifEmpty { null }
                result[StepsRecord.COUNT_TOTAL]?.let {
                    buckets += ActivityBucket(ActivityMetric.STEPS, group.startTime, it.toDouble(), origin)
                }
                result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let {
                    buckets += ActivityBucket(ActivityMetric.ACTIVE_KCAL, group.startTime, it.inKilocalories, origin)
                }
                result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let {
                    buckets += ActivityBucket(ActivityMetric.TOTAL_KCAL, group.startTime, it.inKilocalories, origin)
                }
                result[DistanceRecord.DISTANCE_TOTAL]?.let {
                    buckets += ActivityBucket(ActivityMetric.DISTANCE_M, group.startTime, it.inMeters, origin)
                }
                result[RestingHeartRateRecord.BPM_AVG]?.let {
                    buckets += ActivityBucket(ActivityMetric.RESTING_HR, group.startTime, it.toDouble(), origin)
                }
            }
            ActivityRead.Values(buckets)
        }.getOrElse { ActivityRead.Failed(it.message ?: it::class.java.simpleName) }
    }

    companion object {

        /**
         * The aggregate metrics read in one request. `AggregateGroupByDurationRequest` accepts a mixed set, so
         * one round trip answers for all five record types.
         */
        private val METRICS: Set<AggregateMetric<*>> = setOf(
            StepsRecord.COUNT_TOTAL,
            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
            DistanceRecord.DISTANCE_TOTAL,
            RestingHeartRateRecord.BPM_AVG,
        )

        /** Only the record types the balance actually uses; asking for more is a wider permission dialog. */
        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        )

        /** Creates a source, or null when this device has no usable Health Connect. */
        fun createIfAvailable(context: Context): HealthConnectActivitySource? =
            if (HealthConnectSupport.state(context) == HealthConnectState.AVAILABLE) {
                runCatching {
                    HealthConnectActivitySource(HealthConnectClient.getOrCreate(context))
                }.getOrNull()
            } else {
                null
            }
    }
}
