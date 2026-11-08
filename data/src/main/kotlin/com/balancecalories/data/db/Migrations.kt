package com.balancecalories.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history. Every entry is append-only and paired with a test in `MigrationHarnessTest` that
 * replays the old version, inserts real rows, migrates, and checks both the new schema and the data.
 */
object Migrations {

    /**
     * v1 → v2 adds `app_setting`, needed to remember which profile is currently shown. Nothing existing
     * is touched, so a user's weights, food and target history survive untouched.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_setting` (" +
                    "`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
            )
        }
    }

    /**
     * v2 → v3 gives products a Russian name. Additive and nullable, so existing rows keep working and
     * the UI falls back to `name`.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `product` ADD COLUMN `name_ru` TEXT")
        }
    }

    /**
     * v3 → v4 snapshots the product's names on the entry, alongside the nutrition snapshot.
     *
     * Additive and nullable: existing entries keep working, they simply have no stored name until edited.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `product_name` TEXT")
            db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `product_name_ru` TEXT")
        }
    }

    /**
     * 4 → 5: `daily_activity.data_origins`.
     *
     * Additive and nullable, so every existing row stays valid with "we do not know who wrote this day", which
     * is the truthful answer for rows written before the column existed. The next sync fills them in.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `daily_activity` ADD COLUMN `data_origins` TEXT")
        }
    }

    val all: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
