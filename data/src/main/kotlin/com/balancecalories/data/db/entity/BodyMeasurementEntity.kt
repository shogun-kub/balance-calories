package com.balancecalories.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A weight (or body-fat) reading from any origin. HC records, band values, manual entries and a
 * connected scale all land here with a distinct [source], so one canonical point per day can be chosen
 * rather than letting the last writer win (docs/03-architecture.md §3).
 */
@Entity(
    tableName = "body_measurement",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "local_date"], name = "idx_bm_profile_date"),
        Index(value = ["profile_id", "measured_at", "source"], unique = true, name = "idx_bm_unique_reading"),
    ],
)
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /** UTC epoch millis. */
    @ColumnInfo(name = "measured_at")
    val measuredAt: Long,

    /** `YYYY-MM-DD` in the profile's zone at write time. */
    @ColumnInfo(name = "local_date")
    val localDate: String,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double? = null,

    @ColumnInfo(name = "body_fat_pct")
    val bodyFatPct: Double? = null,

    /** `manual|health_connect|gadgetbridge|scale`. */
    @ColumnInfo(name = "source")
    val source: String,
)
