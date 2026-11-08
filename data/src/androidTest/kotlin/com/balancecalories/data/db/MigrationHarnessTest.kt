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

    private fun androidx.sqlite.db.SupportSQLiteDatabase.tableNames(): List<String> {
        val names = mutableListOf<String>()
        query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        return names
    }

    private companion object {
        const val TEST_DB = "migration-harness-test.db"
    }
}
