package com.balancecalories.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex

/**
 * A person. Profiles are rows, not "the user": one phone can hold a second profile that simply has no
 * band (docs/03-architecture.md §3, design rule "band data belongs to the phone").
 *
 * The SQL model declares `CHECK (sex IN ('male','female'))`; Room cannot express CHECK constraints,
 * so the guarantee lives in [com.balancecalories.data.db.Converters] instead — an unknown value fails
 * loudly on read rather than being silently accepted.
 */
@Entity(
    tableName = "profile",
    indices = [Index(value = ["band_enabled"], name = "idx_profile_band_enabled")],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "sex")
    val sex: Sex,

    /** `YYYY-MM-DD`. Age is derived at read time, never stored. */
    @ColumnInfo(name = "birth_date")
    val birthDate: String,

    @ColumnInfo(name = "height_cm")
    val heightCm: Double,

    @ColumnInfo(name = "activity_level")
    val activityLevel: ActivityLevel,

    /** Negative to lose weight. */
    @ColumnInfo(name = "goal_rate_kg_per_week")
    val goalRateKgPerWeek: Double,

    /** Set when the user pins a number; otherwise the target is recomputed. */
    @ColumnInfo(name = "target_kcal_override")
    val targetKcalOverride: Int? = null,

    /**
     * The weight the person is aiming for, in kilograms, or null when they have not said.
     *
     * Schema **v6**, nullable and additive. The projection needs a destination; without this the app could only
     * report a rate of change, which is not what anyone actually wants to know.
     */
    @ColumnInfo(name = "goal_weight_kg")
    val goalWeightKg: Double? = null,

    /** At most one profile per device may read Health Connect. */
    @ColumnInfo(name = "band_enabled", defaultValue = "0")
    val bandEnabled: Boolean = false,

    /** Display only, e.g. "Huawei Band 9". */
    @ColumnInfo(name = "band_label")
    val bandLabel: String? = null,

    @ColumnInfo(name = "day_start_hour", defaultValue = "4")
    val dayStartHour: Int = 4,

    /** `metric` or `imperial`; metric is stored internally regardless. */
    @ColumnInfo(name = "unit_system", defaultValue = "metric")
    val unitSystem: String = "metric",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null,
)
