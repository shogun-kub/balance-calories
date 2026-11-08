package com.balancecalories.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The effective calorie target over time, keyed by the date it starts applying. History is append-only
 * so "why was my target 2033 last week?" stays answerable after a profile edit.
 */
@Entity(
    tableName = "target_history",
    primaryKeys = ["profile_id", "effective_from"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TargetHistoryEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /** `YYYY-MM-DD`. */
    @ColumnInfo(name = "effective_from")
    val effectiveFrom: String,

    @ColumnInfo(name = "kcal")
    val kcal: Int,

    @ColumnInfo(name = "protein_g")
    val proteinG: Double? = null,
    @ColumnInfo(name = "fat_g")
    val fatG: Double? = null,
    @ColumnInfo(name = "carbs_g")
    val carbsG: Double? = null,

    @ColumnInfo(name = "tdee_kcal")
    val tdeeKcal: Double? = null,

    /** `none|low|medium|high` — how much real data stands behind [tdeeKcal]. */
    @ColumnInfo(name = "tdee_confidence")
    val tdeeConfidence: String? = null,

    /** `seed|adaptive|pinned`. */
    @ColumnInfo(name = "mode")
    val mode: String,
)

/** A barcode that could not be resolved yet, retried when connectivity returns. */
@Entity(tableName = "pending_lookup")
data class PendingLookupEntity(
    @PrimaryKey
    @ColumnInfo(name = "barcode")
    val barcode: String,

    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** Cursor for incremental Health Connect / Gadgetbridge syncs, one row per source. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "last_success")
    val lastSuccess: Long? = null,

    @ColumnInfo(name = "last_cursor")
    val lastCursor: String? = null,
)
