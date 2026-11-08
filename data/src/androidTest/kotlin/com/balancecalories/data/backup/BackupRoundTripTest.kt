package com.balancecalories.data.backup

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.BodyMeasurementEntity
import com.balancecalories.data.db.entity.DailyActivityEntity
import com.balancecalories.data.db.entity.FoodEntryEntity
import com.balancecalories.data.db.entity.ProductEntity
import com.balancecalories.data.db.entity.ProfileEntity
import com.balancecalories.data.db.entity.RecipeEntity
import com.balancecalories.data.db.entity.RecipeItemEntity
import com.balancecalories.data.db.entity.TargetHistoryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The backup, round-tripped through a real database.
 *
 * A backup is the one feature whose failure is unrecoverable — the user finds out at the worst moment, on a new
 * phone, with the old one gone — so these tests exercise the properties that make it worth having rather than the
 * shape of the JSON: the data comes back identical, the file is stable enough to compare, a restore never leaves a
 * half-written database, and the search index still works afterwards.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var db: BalanceCaloriesDatabase
    private lateinit var sql: SupportSQLiteDatabase

    private val now = 1_760_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
        sql = db.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A small but complete database: one person, one product, one meal, one recipe, one day of activity. */
    private fun seed() {
        sql.execSQL(
            "INSERT INTO profile (id, name, sex, birth_date, height_cm, activity_level, goal_rate_kg_per_week, " +
                "goal_weight_kg, band_enabled, day_start_hour, unit_system, created_at) " +
                "VALUES (1, 'Tester', 'male', '1990-01-01', 180.0, 'moderate', -0.5, 75.0, 1, 4, 'metric', $now)",
        )
        sql.execSQL(
            "INSERT INTO body_measurement (id, profile_id, measured_at, local_date, weight_kg, body_fat_pct, source) " +
                "VALUES (1, 1, $now, '2026-11-08', 80.0, 20.5, 'manual')",
        )
        sql.execSQL(
            "INSERT INTO product (id, name, name_ru, source, kcal_100g, protein_100g, fat_100g, carbs_100g, " +
                "is_liquid, is_override, energy_estimated, created_at, updated_at) " +
                "VALUES (10, 'Ovsyanka', 'Овсянка', 'user', 350.0, 12.0, 6.0, 60.0, 0, 0, 0, $now, $now)",
        )
        sql.execSQL(
            "INSERT INTO product (id, name, source, kcal_100g, is_liquid, is_override, energy_estimated, " +
                "created_at, updated_at) " +
                "VALUES (11, 'Starter rice', 'starter', 340.0, 0, 0, 0, $now, $now)",
        )
        sql.execSQL(
            "INSERT INTO product (id, name, source, kcal_100g, is_liquid, is_override, energy_estimated, " +
                "created_at, updated_at) " +
            "VALUES (12, 'Starter rice, corrected', 'starter', 300.0, 0, 1, 0, $now, $now)",
        )
        sql.execSQL(
            "INSERT INTO food_entry (id, profile_id, consumed_at, local_date, meal, product_id, product_name, " +
                "grams, kcal, protein_g, fat_g, carbs_g, was_estimated, deleted_at) " +
                "VALUES (20, 1, $now, '2026-11-08', 'breakfast', 10, 'Ovsyanka', 60.0, 210.0, 7.2, 3.6, 36.0, 0, NULL)",
        )
        sql.execSQL(
            "INSERT INTO recipe (id, profile_id, name, cooked_weight_g, servings, created_at, updated_at) " +
                "VALUES (30, 1, 'Porridge', 500.0, 2.0, $now, $now)",
        )
        sql.execSQL(
            "INSERT INTO recipe_item (recipe_id, product_id, grams, position) VALUES (30, 10, 100.0, 0)",
        )
        sql.execSQL(
            "INSERT INTO daily_activity (profile_id, local_date, steps, active_kcal, bmr_kcal, total_kcal, " +
                "quality, data_origins, updated_at) " +
                "VALUES (1, '2026-11-08', 5321, 210.5, 1749.0, 1959.5, 'complete', 'nodomain.freeyourgadget.gadgetbridge', $now)",
        )
        sql.execSQL(
            "INSERT INTO target_history (profile_id, effective_from, kcal, tdee_kcal, mode, tdee_confidence) " +
                "VALUES (1, '2026-11-01', 1924, 2405.0, 'seed', NULL)",
        )
    }

    private fun export(): String = BackupWriter.write(
        db = sql,
        appVersion = "test",
        schemaVersion = 6,
        exportedAt = now,
    )

    private fun <T> queryOne(sqlText: String, mapper: (android.database.Cursor) -> T): T =
        sql.query(sqlText).use { cursor ->
            assertTrue("expected at least one row from: $sqlText", cursor.moveToFirst())
            mapper(cursor)
        }

    private fun count(table: String): Int = queryOne("SELECT COUNT(*) FROM \"$table\"") { it.getInt(0) }

    @Test
    fun aBackupCarriesEveryUserTableAndRestoresIt() {
        seed()
        val json = export()
        // Every table travels whole, products included: a food entry from the starter pack points at a starter
        // product, and a file without it would violate a foreign key.
        val before = BackupTables.TABLES.associate { table -> table.name to count(table.name) }

        sql.execSQL("DELETE FROM food_entry")
        sql.execSQL("DELETE FROM recipe_item")
        sql.execSQL("DELETE FROM recipe")
        sql.execSQL("DELETE FROM body_measurement")
        sql.execSQL("DELETE FROM daily_activity")
        sql.execSQL("DELETE FROM target_history")
        sql.execSQL("DELETE FROM product")
        sql.execSQL("DELETE FROM profile")
        assertEquals(0, count("food_entry"))

        val outcome = BackupReader.restore(sql, json, currentSchemaVersion = 6)

        assertTrue("restore should succeed, got $outcome", outcome is ImportOutcome.Restored)
        // Starter rows are not carried: only the user's product and the corrected starter row.
        BackupTables.TABLES.forEach { table ->
            assertEquals("row count for ${table.name}", before.getValue(table.name), count(table.name))
        }
        // Values, not just counts: a restore that inserted the right number of empty rows would pass otherwise.
        val weight = queryOne("SELECT weight_kg, source FROM body_measurement") {
            it.getDouble(0) to it.getString(1)
        }
        assertEquals(80.0 to "manual", weight)
        val entry = queryOne("SELECT product_name, kcal, grams FROM food_entry") {
            Triple(it.getString(0), it.getDouble(1), it.getDouble(2))
        }
        assertEquals(Triple("Ovsyanka", 210.0, 60.0), entry)
        // The foreign keys survived, which is the property ids have to preserve.
        assertEquals(1, count("recipe_item"))
    }

    @Test
    fun productSearchStillWorksAfterARestore() {
        seed()
        val json = export()

        val outcome = BackupReader.restore(sql, json, currentSchemaVersion = 6)
        assertTrue(outcome is ImportOutcome.Restored)

        // The FTS index is external-content: rows written directly into `product` are invisible until rebuilt, and
        // nothing would fail — the user would just stop finding products.
        val found = queryOne("SELECT name FROM product_fts WHERE product_fts MATCH 'Ovsyanka'") { it.getString(0) }
        assertEquals("Ovsyanka", found)
    }

    @Test
    fun twoExportsOfUnchangedDataAreIdentical() {
        seed()

        assertEquals("a backup must be comparable to the next one", export(), export())
    }

    @Test
    fun theStarterPackTravelsSoForeignKeysStayValid() {
        seed()
        // The regression this test exists for: an earlier version skipped starter products to save space, and the
        // first real restore refused its own file because a food entry pointed at one of them.
        sql.execSQL(
            "INSERT INTO food_entry (id, profile_id, consumed_at, local_date, meal, product_id, product_name, " +
                "grams, kcal) " +
                "VALUES (21, 1, $now, '2026-11-08', 'lunch', 11, 'Starter rice', 100.0, 340.0)",
        )

        val json = export()
        val products = org.json.JSONObject(json).getJSONObject("tables").getJSONArray("product")
        assertEquals(3, products.length())

        sql.execSQL("DELETE FROM food_entry")
        sql.execSQL("DELETE FROM recipe_item")
        sql.execSQL("DELETE FROM recipe")
        sql.execSQL("DELETE FROM body_measurement")
        sql.execSQL("DELETE FROM daily_activity")
        sql.execSQL("DELETE FROM target_history")
        sql.execSQL("DELETE FROM product")
        sql.execSQL("DELETE FROM profile")

        val outcome = BackupReader.restore(sql, json, currentSchemaVersion = 6)

        assertTrue("a file with its own foreign keys must restore, got $outcome", outcome is ImportOutcome.Restored)
        assertEquals(3, count("product"))
    }

    @Test
    fun aRestoreSaysWhetherTheStarterPackCameWithTheFile() {
        seed()

        val outcome = BackupReader.restore(sql, export(), currentSchemaVersion = 6) as ImportOutcome.Restored

        assertTrue("the products travelled, so the importer must not add a second copy", outcome.containsStarterProducts)
    }

    @Test
    fun aFileThatIsNotABackupIsRefusedByItsHeader() {
        val outcome = BackupReader.restore(sql, """{"hello":"world"}""", currentSchemaVersion = 6)

        assertEquals(ImportOutcome.Refused(ImportOutcome.Refusal.NOT_A_BACKUP), outcome)
    }

    @Test
    fun aBackupFromANewerSchemaIsRefusedRatherThanGuessed() {
        seed()
        val json = export().replace("\"schemaVersion\": 6", "\"schemaVersion\": 99")

        val outcome = BackupReader.restore(sql, json, currentSchemaVersion = 6)

        assertEquals(ImportOutcome.Refused(ImportOutcome.Refusal.NEWER_SCHEMA), outcome)
        assertEquals("and it changed nothing", 1, count("food_entry"))
    }

    @Test
    fun aTruncatedBackupIsRefusedAndLeavesTheDatabaseAlone() {
        seed()
        val json = export()
        // Drop the last table's array: a file that was cut off mid-write, which is what a failed transfer looks like.
        val truncated = json.substringBefore("\"daily_activity\"")

        val outcome = BackupReader.restore(sql, truncated, currentSchemaVersion = 6)

        assertFalse(outcome is ImportOutcome.Restored)
        assertEquals("the existing data is still there", 1, count("food_entry"))
        assertEquals(1, count("daily_activity"))
    }

    @Test
    fun aValidJsonFileMissingATableIsRefusedRatherThanPartlyRestored() {
        seed()
        // Keeps the file parseable while removing one required table, which is what an edited file looks like.
        val json = export().replace("\"daily_activity\":", "\"daily_activity_removed\":")

        val outcome = BackupReader.restore(sql, json, currentSchemaVersion = 6)

        assertEquals(ImportOutcome.Refused(ImportOutcome.Refusal.DAMAGED), outcome)
        assertEquals("nothing was deleted on the way to refusing", 1, count("food_entry"))
    }

    @Test
    fun aColumnThisBuildNoLongerHasIsDroppedAndReported() {
        seed()
        val json = export().replace("\"body_fat_pct\"", "\"body_fat_pct_removed\"")

        val outcome = BackupReader.restore(sql, json, currentSchemaVersion = 6)

        val restored = outcome as ImportOutcome.Restored
        assertEquals(setOf("body_measurement.body_fat_pct_removed"), restored.droppedColumns)
        // The row survives with the column it lost defaulted to null rather than the row being dropped.
        assertEquals(1, count("body_measurement"))
    }

    @Test
    fun anEmptyDatabaseExportsEveryTableAndRestoresCleanly() {
        val json = export()
        BackupTables.TABLES.forEach { table ->
            assertTrue("table ${table.name} must be present even when empty", json.contains("\"${table.name}\""))
        }

        val outcome = BackupReader.restore(sql, json, currentSchemaVersion = 6)

        assertTrue(outcome is ImportOutcome.Restored)
        assertEquals(0, (outcome as ImportOutcome.Restored).rows)
    }
}
