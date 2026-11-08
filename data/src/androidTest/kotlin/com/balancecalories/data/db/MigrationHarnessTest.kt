package com.balancecalories.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration safety. The first test replays the committed v1 schema so the harness is known to work; the
 * second is the one that matters — it migrates a database that already holds a user's rows and proves
 * nothing was lost on the way to v2.
 */
@RunWith(AndroidJUnit4::class)
class MigrationHarnessTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BalanceCaloriesDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun schemaV1CanBeReplayedFromCommittedJson() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            val tables = db.tableNames()

            val expected = setOf(
                "profile",
                "body_measurement",
                "daily_activity",
                "product",
                "product_fts",
                "recipe",
                "recipe_item",
                "food_entry",
                "target_history",
                "pending_lookup",
                "sync_state",
            )
            assertEquals(expected, tables.filter { it in expected }.toSet())
            assertEquals(1, db.version)
        }
    }

    @Test
    fun migration1To2AddsAppSettingWithoutTouchingExistingRows() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO profile (id, name, sex, birth_date, height_cm, activity_level, " +
                    "goal_rate_kg_per_week, target_kcal_override, band_enabled, band_label, " +
                    "day_start_hour, unit_system, created_at, archived_at) " +
                    "VALUES (1, 'Test', 'male', '1990-01-01', 175.0, 'light', -0.3, NULL, 1, NULL, 4, " +
                    "'metric', 1760000000000, NULL)",
            )
            db.execSQL(
                "INSERT INTO body_measurement (id, profile_id, measured_at, local_date, weight_kg, " +
                    "body_fat_pct, source) VALUES (1, 1, 1760000000000, '2026-11-08', 80.0, NULL, 'manual')",
            )
            db.execSQL(
                "INSERT INTO target_history (profile_id, effective_from, kcal, protein_g, fat_g, carbs_g, " +
                    "tdee_kcal, tdee_confidence, mode) " +
                    "VALUES (1, '2026-11-08', 2033, NULL, NULL, NULL, 2363.28, 'none', 'seed')",
            )
        }

        // Validates the migrated schema against the exported v2 JSON, then hands back the database.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2).use { db ->
            assertTrue("app_setting should exist after v2", "app_setting" in db.tableNames())
            assertEquals(2, db.version)

            db.query("SELECT name, height_cm, band_enabled FROM profile WHERE id = 1").use { cursor ->
                assertTrue("the profile row must survive", cursor.moveToFirst())
                assertEquals("Test", cursor.getString(0))
                assertEquals(175.0, cursor.getDouble(1), 0.001)
                assertEquals(1, cursor.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM body_measurement").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT kcal, mode FROM target_history WHERE profile_id = 1").use { cursor ->
                assertTrue("the target row must survive", cursor.moveToFirst())
                assertEquals(2033, cursor.getInt(0))
                assertEquals("seed", cursor.getString(1))
            }

            // The new table is usable and keyed the way the repository expects.
            db.execSQL("INSERT INTO app_setting (`key`, `value`) VALUES ('active_profile_id', '1')")
            db.query("SELECT value FROM app_setting WHERE `key` = 'active_profile_id'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("1", cursor.getString(0))
            }
        }
    }

    @Test
    fun migration3To4SnapshotsTheProductNameAndKeepsTheEntry() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO food_entry (id, profile_id, consumed_at, local_date, meal, product_id, " +
                    "grams, kcal, protein_g, fat_g, carbs_g, fiber_g, sugar_g, salt_g, was_estimated, " +
                    "note, deleted_at) VALUES (1, 1, 1760000000000, '2026-11-08', 'lunch', NULL, " +
                    "150.0, 300.0, 10.0, 5.0, 40.0, NULL, NULL, NULL, 0, NULL, NULL)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4).use { db ->
            assertEquals(4, db.version)
            db.query(
                "SELECT grams, kcal, product_name, product_name_ru FROM food_entry WHERE id = 1",
            ).use { cursor ->
                assertTrue("the entry must survive", cursor.moveToFirst())
                assertEquals(150.0, cursor.getDouble(0), 0.001)
                assertEquals(300.0, cursor.getDouble(1), 0.001)
                assertTrue("no name for a pre-migration entry", cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }
        }
    }

    /**
     * 4 → 5 adds `daily_activity.data_origins` and must leave existing day rows readable, with the column null
     * because nobody recorded the origin before this version existed.
     */
    @Test
    fun migration4To5AddsDataOriginsAndKeepsExistingDays() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO `profile` (`id`,`name`,`sex`,`birth_date`,`height_cm`,`activity_level`," +
                    "`goal_rate_kg_per_week`,`day_start_hour`,`unit_system`,`created_at`) " +
                    "VALUES (1,'Owner','MALE','1990-01-01',180.0,'MODERATE',-0.5,4,'METRIC',1)",
            )
            db.execSQL(
                "INSERT INTO `daily_activity` (`profile_id`,`local_date`,`steps`,`quality`,`updated_at`) " +
                    "VALUES (1,'2026-11-07',4321,'complete',5)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, Migrations.MIGRATION_4_5).use { db ->
            db.query("SELECT profile_id, local_date, steps, quality, data_origins FROM daily_activity").use { c ->
                assertTrue("the day row must survive the migration", c.moveToFirst())
                assertEquals(4321, c.getInt(2))
                assertEquals("complete", c.getString(3))
                assertTrue("nothing recorded an origin before v5", c.isNull(4))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.tableNames(): List<String> {
        val names = mutableListOf<String>()
        query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        return names
    }

    @Test
    fun migration2To3GivesProductsARussianNameAndKeepsTheirValues() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO product (id, barcode, name, brand, source, kcal_100g, protein_100g, " +
                    "fat_100g, carbs_100g, fiber_100g, sugar_100g, salt_100g, is_liquid, " +
                    "serving_grams, pack_grams, image_url, off_rev, is_override, energy_estimated, " +
                    "created_at, updated_at) " +
                    "VALUES (1, '4600000000001', 'Test oats', NULL, 'starter', 389.0, 16.9, 6.9, " +
                    "66.3, 10.6, 1.0, 0.0, 0, NULL, NULL, NULL, NULL, 0, 0, 1760000000000, 1760000000000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3).use { db ->
            assertEquals(3, db.version)
            db.query("SELECT name, kcal_100g, name_ru FROM product WHERE id = 1").use { cursor ->
                assertTrue("the product row must survive", cursor.moveToFirst())
                assertEquals("Test oats", cursor.getString(0))
                assertEquals(389.0, cursor.getDouble(1), 0.001)
                assertTrue("name_ru must be null for existing rows", cursor.isNull(2))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-harness-test.db"
    }
}
