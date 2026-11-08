package com.balancecalories.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One row per profile per local day, upserted and recomputed from scratch whenever new Health Connect
 * data arrives (docs/03-architecture.md §3, "one aggregate row per day"). Raw per-minute samples are
 * never stored: the Redmi's storage is the constraint, and the balance only needs the aggregate.
 *
 * [quality] is `complete|partial|no_band|estimated` and is a first-class part of the product rule that
 * a measured number must not look like an estimated one.
 */
@Entity(
    tableName = "daily_activity",
    primaryKeys = ["profile_id", "local_date"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DailyActivityEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /** `YYYY-MM-DD` in the profile's zone, honouring its day-start hour. */
    @ColumnInfo(name = "local_date")
    val localDate: String,

    @ColumnInfo(name = "steps")
    val steps: Int? = null,

    @ColumnInfo(name = "active_kcal")
    val activeKcal: Double? = null,

    /** As reported by Health Connect, null when absent. The app's own BMR is a separate calculation. */
    @ColumnInfo(name = "bmr_kcal")
    val bmrKcal: Double? = null,

    @ColumnInfo(name = "total_kcal")
    val totalKcal: Double? = null,

    @ColumnInfo(name = "distance_m")
    val distanceM: Double? = null,

    @ColumnInfo(name = "sleep_minutes")
    val sleepMinutes: Int? = null,

    @ColumnInfo(name = "resting_hr")
    val restingHr: Int? = null,

    @ColumnInfo(name = "quality")
    val quality: String,

    /**
     * Which apps contributed, comma-separated (`DataOrigin.packageName`), or null when unknown.
     *
     * Health Connect merges every writer into one aggregate, so without this a band day and a phone-written
     * baseline day are indistinguishable — observed on the Honor as 29 days of exactly 1858.25 kcal.
     */
    @ColumnInfo(name = "data_origins")
    val dataOrigins: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
