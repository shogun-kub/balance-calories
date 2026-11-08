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

    val all: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
