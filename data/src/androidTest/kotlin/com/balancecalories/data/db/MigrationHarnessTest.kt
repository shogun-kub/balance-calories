package com.balancecalories.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the migration harness works before any migration exists: it replays the committed v1 schema
 * and inspects the real database. When schema v2 arrives, its migration test goes here and uses the
 * same helper, so the first migration is written against a harness that already runs.
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
            val tables = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) {
                    tables += cursor.getString(0)
                }
            }

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

    private companion object {
        const val TEST_DB = "migration-harness-test.db"
    }
}
