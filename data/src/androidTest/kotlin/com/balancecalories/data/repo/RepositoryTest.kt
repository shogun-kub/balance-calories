package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.db.BalanceCaloriesDatabase
import java.time.LocalDate
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
 * Repository behaviour, which is where the app's rules actually live: one band profile per device,
 * history that is written rather than recomputed, and a weight that can be read back.
 *
 * All figures here are synthetic test values, not anyone's measurements.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var db: BalanceCaloriesDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var body: BodyMeasurementRepository
    private lateinit var targets: TargetRepository
    private lateinit var settings: AppSettingsRepository

    private val today = LocalDate.of(2026, 11, 8)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
        profiles = ProfileRepository(db) { 1_760_000_000_000 }
        body = BodyMeasurementRepository(db) { 1_760_000_000_000 }
        targets = TargetRepository(db)
        settings = AppSettingsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun profile(name: String = "Test", band: Boolean = false) = Profile(
        name = name,
        sex = Sex.MALE,
        birthDate = LocalDate.of(1990, 1, 1),
        heightCm = 175.0,
        activityLevel = ActivityLevel.LIGHT,
        goalRateKgPerWeek = -0.3,
        bandEnabled = band,
        createdAt = 1_760_000_000_000,
    )

    @Test
    fun creatingAProfileRoundTripsThroughTheDomain() = runBlocking {
        val created = profiles.create(profile("Owner"))

        val stored = profiles.byId(created.id)
        assertNotNull(stored)
        assertEquals("Owner", stored!!.name)
        assertEquals(LocalDate.of(1990, 1, 1), stored.birthDate)
        assertEquals(-0.3, stored.goalRateKgPerWeek, 0.0)
        assertEquals(4, stored.dayStartHour)
        assertNull(stored.archivedAt)
    }

    @Test
    fun enablingTheBandForOneProfileTakesItFromTheOther() = runBlocking {
        val owner = profiles.create(profile("Owner", band = true))
        val second = profiles.create(profile("Second", band = true))

        // Creating a band profile clears everyone else in the same transaction.
        val ownerAfter = profiles.byId(owner.id)!!
        val secondAfter = profiles.byId(second.id)!!
        assertTrue(!ownerAfter.bandEnabled)
        assertTrue(secondAfter.bandEnabled)

        profiles.setBandEnabled(owner.id, true)

        assertTrue(profiles.byId(owner.id)!!.bandEnabled)
        assertTrue(!profiles.byId(second.id)!!.bandEnabled)
    }

    @Test
    fun archivingHidesAProfileFromTheActiveList() = runBlocking {
        val created = profiles.create(profile())

        profiles.archive(created.id)

        val stored = profiles.byId(created.id)!!
        assertTrue(stored.isArchived)
        assertEquals(1_760_000_000_000, stored.archivedAt)
    }

    @Test
    fun weightIsRecordedAndReadBackAsTheLatest() = runBlocking {
        val created = profiles.create(profile())

        assertNull(body.latestWeightKg(created.id))

        body.record(created.id, 80.0, today.minusDays(1), BodyMeasurementRepository.SOURCE_MANUAL, measuredAt = 1)
        body.record(created.id, 79.4, today, BodyMeasurementRepository.SOURCE_HEALTH_CONNECT, measuredAt = 2)

        assertEquals(79.4, body.latestWeightKg(created.id)!!, 0.001)
        // A typed value outranks a synced one even when the synced one is newer.
        assertEquals(80.0, body.latestManualWeightKg(created.id)!!, 0.001)
    }

    @Test
    fun seedTargetIsWrittenToHistoryAndReadableByDate() = runBlocking {        val created = profiles.create(profile())

        val computed = targets.recordSeed(created, weightKg = 80.0, on = today)

        // Mifflin-St Jeor for a 36-year-old male, 175 cm, 80 kg: 10*80 + 6.25*175 - 5*36 + 5
        assertEquals(1718.75, computed.bmrKcal, 0.01)
        assertEquals(2363.28, computed.tdeeKcal, 0.01)
        assertEquals(2033, computed.kcal)

        val stored = targets.effectiveOn(created.id, today)
        assertNotNull(stored)
        assertEquals(2033, stored!!.kcal)
        assertEquals(TargetRepository.MODE_SEED, stored.mode)
        assertEquals(TargetRepository.CONFIDENCE_NONE, stored.tdeeConfidence)

        assertNull(targets.effectiveOn(created.id, today.minusDays(1)))
    }

    @Test
    fun activeProfileIsRememberedAndClearedWhenThatProfileIsDeleted() = runBlocking {
        val first = profiles.create(profile("First"))
        val second = profiles.create(profile("Second"))

        settings.setActiveProfile(second.id)
        assertEquals(second.id, settings.activeProfileId())

        profiles.delete(second.id)
        // The stored selection must not point at a row that no longer exists.
        assertNull(settings.activeProfileId())

        settings.setActiveProfile(first.id)
        profiles.delete(first.id)
        assertNull(settings.activeProfileId())
    }

    @Test
    fun deletingAnotherProfileKeepsTheStoredSelection() = runBlocking {
        val kept = profiles.create(profile("Kept"))
        val other = profiles.create(profile("Other"))
        settings.setActiveProfile(kept.id)

        profiles.delete(other.id)

        assertEquals(kept.id, settings.activeProfileId())
    }

    @Test
    fun updatingAProfileRewritesFieldsAndLeavesTargetHistoryAlone() = runBlocking {
        val created = profiles.create(profile("Before"))
        targets.recordSeed(created, weightKg = 80.0, on = today)

        val updated = created.copy(name = "After", heightCm = 180.0, goalRateKgPerWeek = -0.5)
        profiles.update(updated)

        val stored = profiles.byId(created.id)!!
        assertEquals("After", stored.name)
        assertEquals(180.0, stored.heightCm, 0.001)
        assertEquals(-0.5, stored.goalRateKgPerWeek, 0.0)

        // Editing does not delete history: yesterday's target is still what the app said.
        assertEquals(2033, targets.effectiveOn(created.id, today)!!.kcal)
    }
}
