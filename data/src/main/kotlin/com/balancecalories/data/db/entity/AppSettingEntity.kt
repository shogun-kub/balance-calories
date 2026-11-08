package com.balancecalories.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Small key-value store for app-level state that belongs to the phone rather than to a person, such as
 * which profile is currently shown. Profile-scoped preferences stay on `profile` itself.
 *
 * Added in schema v2; see `Migrations.MIGRATION_1_2`.
 */
@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,
)
