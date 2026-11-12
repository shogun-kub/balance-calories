package com.balancecalories.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One meal slot of one profile (schema v7).
 *
 * The slots used to be an enum, which made the number of meals and their names the app's decision. They are now
 * rows: the person can add, rename and retime them, and the defaults (`breakfast` … `snack`) are only what a new
 * profile is seeded with.
 *
 * The primary key is `(profile_id, meal_key)`, and `meal_key` is what `food_entry.meal` stores. That is
 * deliberate: a rename writes `name` and nothing else, so a year of entries cannot be rewritten by renaming a
 * meal, and no entry has to be migrated when a slot is retimed or renamed.
 *
 * `name` is **empty** for a slot nobody renamed, and the screen then prints the localized default name for the
 * key - translations belong in the string resources, not in a database row copied out of them.
 */
@Entity(
    tableName = "meal",
    primaryKeys = ["profile_id", "meal_key"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class MealEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    @ColumnInfo(name = "meal_key")
    val key: String,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    /** Planned time in minutes from midnight; null for a slot with no time, like the snack bucket. */
    @ColumnInfo(name = "planned_minutes")
    val plannedMinutes: Int? = null,

    /** The snack bucket: cannot be renamed or deleted, so unplanned food always has somewhere to go. */
    @ColumnInfo(name = "fixed")
    val fixed: Boolean = false,
)
