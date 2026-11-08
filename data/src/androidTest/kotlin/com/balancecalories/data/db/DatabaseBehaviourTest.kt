package com.balancecalories.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.data.db.entity.BodyMeasurementEntity
import com.balancecalories.data.db.entity.DailyActivityEntity
import com.balancecalories.data.db.entity.ProfileEntity
import com.balancecalories.data.db.entity.TargetHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behaviour the schema must guarantee. Runs on a device because Room needs a real SQLite, and the two
 * target phones (Android 14 and Android 11) are the environments that matter.
 *
 * The profile, weight and activity figures below are synthetic test values, not anyone's data.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseBehaviourTest {

    private lateinit var db: BalanceCaloriesDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun profile(name: String = "Test") = ProfileEntity(
        name = name,
        sex = Sex.MALE,
        birthDate = "1990-01-01",
        heightCm = 175.0,
        activityLevel = ActivityLevel.LIGHT,
        goalRateKgPerWeek = -0.3,
        createdAt = 1_760_000_000_000,
    )

    @Test
    fun profileRoundTripPreservesEnumsAndDefaults() = runBlocking {
        val id = db.profileDao().insert(profile())

        val found = db.profileDao().byId(id)
        assertNotNull(found)
        val stored = found!!
        assertEquals(Sex.MALE, stored.sex)
        assertEquals(ActivityLevel.LIGHT, stored.activityLevel)
        assertEquals(4, stored.dayStartHour)
        assertEquals("metric", stored.unitSystem)
        assertTrue(!stored.bandEnabled)
        assertNull(stored.archivedAt)
    }

    @Test
    fun deletingProfileCascadesToMeasurements() = runBlocking {
        val id = db.profileDao().insert(profile())
        db.bodyMeasurementDao().upsert(
            BodyMeasurementEntity(
                profileId = id,
                measuredAt = 1_760_000_000_000,
                localDate = "2026-11-08",
                weightKg = 80.0,
                source = "manual",
            ),
        )
        assertTrue(db.bodyMeasurementDao().latest(id) != null)

        db.profileDao().delete(id)

        assertNull(db.bodyMeasurementDao().latest(id))
    }

    @Test
    fun onlyOneProfileCanReadTheBand() = runBlocking {
        val owner = db.profileDao().insert(profile("Owner"))
        val second = db.profileDao().insert(profile("Second"))
        db.profileDao().update(db.profileDao().byId(owner)!!.copy(bandEnabled = true))
        db.profileDao().update(db.profileDao().byId(second)!!.copy(bandEnabled = true))

        assertEquals(2, db.profileDao().bandEnabledProfiles().size)

        db.profileDao().clearBandExcept(owner)

        val enabled = db.profileDao().bandEnabledProfiles()
        assertEquals(listOf(owner), enabled.map { it.id })
    }

    @Test
    fun targetHistoryAnswersWhatWasInForceOnADate() = runBlocking {
        val id = db.profileDao().insert(profile())
        db.targetHistoryDao().upsert(
            TargetHistoryEntity(profileId = id, effectiveFrom = "2026-11-01", kcal = 2100, mode = "seed"),
        )
        db.targetHistoryDao().upsert(
            TargetHistoryEntity(profileId = id, effectiveFrom = "2026-11-05", kcal = 2000, mode = "adaptive"),
        )

        assertEquals(2100, db.targetHistoryDao().effectiveOn(id, "2026-11-03")?.kcal)
        assertEquals(2100, db.targetHistoryDao().effectiveOn(id, "2026-11-01")?.kcal)
        assertEquals(2000, db.targetHistoryDao().effectiveOn(id, "2026-11-05")?.kcal)
        assertNull(db.targetHistoryDao().effectiveOn(id, "2026-10-31"))
    }

    @Test
    fun dailyActivityUpsertKeepsOneRowPerDay() = runBlocking {
        val id = db.profileDao().insert(profile())
        val day = "2026-11-08"

        db.dailyActivityDao().upsert(
            DailyActivityEntity(profileId = id, localDate = day, steps = 500, quality = "partial", updatedAt = 1),
        )
        db.dailyActivityDao().upsert(
            DailyActivityEntity(profileId = id, localDate = day, steps = 1200, quality = "complete", updatedAt = 2),
        )

        val stored = db.dailyActivityDao().forDay(id, day)
        assertEquals(1200, stored?.steps)
        assertEquals("complete", stored?.quality)
    }

    @Test
    fun rereadingTheSameHealthConnectRecordDoesNotDuplicate() = runBlocking {
        val id = db.profileDao().insert(profile())
        val reading = BodyMeasurementEntity(
            profileId = id,
            measuredAt = 1_760_000_000_000,
            localDate = "2026-11-08",
            weightKg = 80.0,
            source = "health_connect",
        )

        db.bodyMeasurementDao().upsert(reading)
        db.bodyMeasurementDao().upsert(reading.copy(weightKg = 79.5))

        val latest = db.bodyMeasurementDao().latestFrom(id, "health_connect")
        assertEquals(79.5, latest?.weightKg ?: 0.0, 0.001)
    }
}
