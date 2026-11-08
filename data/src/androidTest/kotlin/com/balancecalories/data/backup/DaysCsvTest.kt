package com.balancecalories.data.backup

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.data.db.BalanceCaloriesDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The spreadsheet report.
 *
 * The properties pinned here are the ones that make the file usable rather than merely written: the numbers agree
 * with the app, a missing value stays a missing cell instead of becoming a zero, the delimiter and decimal mark
 * survive Excel, and the date order is stable so two exports can be diffed.
 */
@RunWith(AndroidJUnit4::class)
class DaysCsvTest {

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
        sql.execSQL(
            "INSERT INTO profile (id, name, sex, birth_date, height_cm, activity_level, goal_rate_kg_per_week, " +
                "band_enabled, day_start_hour, unit_system, created_at) " +
                "VALUES (1, 'Tester', 'male', '1990-01-01', 180.0, 'moderate', -0.5, 0, 4, 'metric', $now)",
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(date: String, kcal: Double, id: Long) {
        sql.execSQL(
            "INSERT INTO food_entry (id, profile_id, consumed_at, local_date, meal, product_name, grams, kcal) " +
                "VALUES ($id, 1, $now, '$date', 'lunch', 'Test food', 100.0, $kcal)",
        )
    }

    private fun activity(date: String, steps: Int, active: Double, total: Double) {
        sql.execSQL(
            "INSERT INTO daily_activity (profile_id, local_date, steps, active_kcal, total_kcal, quality, updated_at) " +
                "VALUES (1, '$date', $steps, $active, $total, 'complete', $now)",
        )
    }

    /** A stand-in for `DailyBurn`: total when present, else active + BMR, which is the app's own precedence. */
    private fun burnFor(bmr: Double) = { _: String, activity: DaysCsv.ActivityInput? ->
        when {
            activity?.totalKcal != null -> DaysCsv.Burn(activity.totalKcal, isMeasured = true)
            activity?.activeKcal != null -> DaysCsv.Burn(bmr + activity.activeKcal, isMeasured = true)
            else -> null
        }
    }

    private fun lines(csv: String) = csv.split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun theHeaderNamesEveryColumnInAStableOrder() {
        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))

        assertEquals(
            "date;eaten_kcal;burned_kcal;balance_kcal;burn_is_measured;quality;steps;weight_kg;logged",
            lines(csv).single(),
        )
    }

    @Test
    fun aCompleteDayReportsEatenBurnedAndTheDifference() {
        entry("2026-11-07", kcal = 2100.0, id = 1)
        activity("2026-11-07", steps = 5000, active = 210.0, total = 1959.0)

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))
        val row = lines(csv)[1].split(";")

        assertEquals("2026-11-07", row[0])
        assertEquals("2100", row[1])
        assertEquals("1959", row[2])
        assertEquals("141", row[3])
        assertEquals("1", row[4])
        assertEquals("complete", row[5])
        assertEquals("5000", row[6])
        assertEquals("1", row[8])
    }

    @Test
    fun aDayWithoutABurnLeavesTheBalanceEmptyRatherThanNegativeEaten() {
        entry("2026-11-07", kcal = 2100.0, id = 1)

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))
        val row = lines(csv)[1].split(";")

        assertEquals("2100", row[1])
        assertEquals("no burn means no balance", "", row[3])
        assertEquals("", row[2])
    }

    @Test
    fun aDayWithNoFoodLogKeepsEatenEmptyAndNeverZero() {
        activity("2026-11-07", steps = 3000, active = 120.0, total = 1869.0)

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))
        val row = lines(csv)[1].split(";")

        assertEquals("an unlogged day is missing, not zero", "", row[1])
        assertEquals("0", row[8])
        assertEquals("1869", row[2])
        assertEquals("no balance without intake", "", row[3])
    }

    @Test
    fun aFormulaBurnIsMarkedAsNotMeasured() {
        entry("2026-11-07", kcal = 2000.0, id = 1)
        sql.execSQL(
            "INSERT INTO daily_activity (profile_id, local_date, steps, quality, updated_at) " +
                "VALUES (1, '2026-11-06', 1000, 'partial', $now)",
        )
        // A day with a row but no energy numbers: the caller falls back to the PAL estimate.
        val palBurn = { _: String, _: DaysCsv.ActivityInput? ->
            DaysCsv.Burn(2405.0, isMeasured = false)
        }

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = palBurn)
        val row = lines(csv).first { it.startsWith("2026-11-06") }.split(";")

        assertEquals("2405", row[2])
        assertEquals("an estimate must not look like a measurement", "0", row[4])
    }

    @Test
    fun decimalsUseADotAndTheSeparatorIsNotAComma() {
        entry("2026-11-07", kcal = 150.5, id = 1)
        sql.execSQL(
            "INSERT INTO body_measurement (id, profile_id, measured_at, local_date, weight_kg, source) " +
                "VALUES (1, 1, $now, '2026-11-07', 79.55, 'manual')",
        )

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))
        val row = lines(csv)[1].split(";")

        assertEquals("150.5", row[1])
        assertEquals("79.55", row[7])
        assertFalse("a comma would split the cell in Excel", lines(csv)[1].substringBefore(';').contains(','))
    }

    @Test
    fun rowsAreInDateOrderWhateverOrderTheyWereInserted() {
        entry("2026-11-08", kcal = 100.0, id = 1)
        entry("2026-11-05", kcal = 200.0, id = 2)
        entry("2026-11-06", kcal = 300.0, id = 3)

        val dates = lines(DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0)))
            .drop(1)
            .map { it.substringBefore(';') }

        assertEquals(listOf("2026-11-05", "2026-11-06", "2026-11-08"), dates)
    }

    @Test
    fun theCanonicalWeightWinsWhenADayHasSeveralReadings() {
        // Manual outranks Health Connect, which is the same preference the History screen applies.
        sql.execSQL(
            "INSERT INTO body_measurement (id, profile_id, measured_at, local_date, weight_kg, source) " +
                "VALUES (1, 1, $now, '2026-11-07', 78.0, 'health_connect')",
        )
        sql.execSQL(
            "INSERT INTO body_measurement (id, profile_id, measured_at, local_date, weight_kg, source) " +
                "VALUES (2, 1, ${now + 1000}, '2026-11-07', 79.5, 'manual')",
        )

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))
        val row = lines(csv)[1].split(";")

        assertEquals("79.5", row[7])
    }

    @Test
    fun deletedEntriesAreNotCountedInTheDaysIntake() {
        entry("2026-11-07", kcal = 300.0, id = 1)
        sql.execSQL("UPDATE food_entry SET deleted_at = $now WHERE id = 1")

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))

        assertTrue("a soft-deleted meal is not food", lines(csv).size == 1)
    }

    @Test
    fun anotherProfilesDaysDoNotLeak() {
        sql.execSQL(
            "INSERT INTO profile (id, name, sex, birth_date, height_cm, activity_level, goal_rate_kg_per_week, " +
                "band_enabled, day_start_hour, unit_system, created_at) " +
                "VALUES (2, 'Other', 'female', '1992-02-02', 165.0, 'light', -0.25, 0, 4, 'metric', $now)",
        )
        entry("2026-11-07", kcal = 2100.0, id = 1)
        sql.execSQL(
            "INSERT INTO food_entry (id, profile_id, consumed_at, local_date, meal, product_name, grams, kcal) " +
                "VALUES (2, 2, $now, '2026-11-07', 'lunch', 'Her food', 100.0, 999.0)",
        )

        val csv = DaysCsv.build(sql, profileId = 1, burnFor = burnFor(1749.0))

        assertEquals("only the requested profile", 1, lines(csv).drop(1).size)
        assertTrue(lines(csv)[1].startsWith("2026-11-07;2100;"))
    }
}
