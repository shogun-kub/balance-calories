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

    /**
     * 5 → 6: `profile.goal_weight_kg`.
     *
     * Nullable, so every existing profile stays valid with "no goal set" — the truthful reading, and the one that
     * makes the projection say nothing instead of guessing a destination.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `goal_weight_kg` REAL")
        }
    }

    /**
     * v6 -> v7 turns the meal slots into data.
     *
     * Until now the four slots were an enum and the three times a per-profile setting. The owner asked for meals
     * he can add, rename, retime and delete (2026-11-12), so a `meal` row per profile carries the name, the
     * order and the planned time, while `food_entry.meal` keeps storing the slot's **key** - which for existing
     * rows is already exactly `breakfast` / `lunch` / `dinner` / `snack`, so no entry is rewritten and no
     * history moves.
     *
     * The times a person had already set in `meal_times_<id>` are copied into the rows they described and the
     * old keys are removed, so nothing is silently reset to 08:00/13:00/19:00 for somebody who changed them.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `meal` (" +
                    "`profile_id` INTEGER NOT NULL, `meal_key` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`sort_order` INTEGER NOT NULL, `planned_minutes` INTEGER, `fixed` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profile_id`, `meal_key`), " +
                    "FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_profile_id` ON `meal` (`profile_id`)")

            val profiles = mutableListOf<Long>()
            db.query("SELECT `id` FROM `profile`").use { cursor ->
                while (cursor.moveToNext()) profiles += cursor.getLong(0)
            }

            val storedTimes = mutableMapOf<Long, Triple<Int, Int, Int>>()
            db.query("SELECT `key`, `value` FROM `app_setting` WHERE `key` LIKE 'meal_times_%'").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0).removePrefix("meal_times_").toLongOrNull() ?: continue
                    val parts = cursor.getString(1).split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (parts.size == 3 && parts.all { it in 0 until 24 * 60 }) {
                        storedTimes[id] = Triple(parts[0], parts[1], parts[2])
                    }
                }
            }

            for (id in profiles) {
                val (breakfast, lunch, dinner) = storedTimes[id] ?: Triple(8 * 60, 13 * 60, 19 * 60)
                db.execSQL(
                    "INSERT INTO `meal`(`profile_id`, `meal_key`, `name`, `sort_order`, `planned_minutes`, `fixed`) " +
                        "VALUES ($id, 'breakfast', '', 0, $breakfast, 0)",
                )
                db.execSQL(
                    "INSERT INTO `meal`(`profile_id`, `meal_key`, `name`, `sort_order`, `planned_minutes`, `fixed`) " +
                        "VALUES ($id, 'lunch', '', 1, $lunch, 0)",
                )
                db.execSQL(
                    "INSERT INTO `meal`(`profile_id`, `meal_key`, `name`, `sort_order`, `planned_minutes`, `fixed`) " +
                        "VALUES ($id, 'dinner', '', 2, $dinner, 0)",
                )
                db.execSQL(
                    "INSERT INTO `meal`(`profile_id`, `meal_key`, `name`, `sort_order`, `planned_minutes`, `fixed`) " +
                        "VALUES ($id, 'snack', '', 3, NULL, 1)",
                )
            }

            db.execSQL("DELETE FROM `app_setting` WHERE `key` LIKE 'meal_times_%'")
        }
    }

    val all: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
