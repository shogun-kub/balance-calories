package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.food.MealDefaults
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.db.BalanceCaloriesDatabase
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
    fun creatingASecondProfileDoesNotTakeTheBandFromTheFirst() = runBlocking {
        val owner = profiles.create(profile("Owner", band = true))
        val second = profiles.create(profile("Second", band = true))

        // One band per phone: the profile that reads it keeps it until someone moves it explicitly.
        // A second profile created for an afternoon used to take it silently, stopping the owner's sync.
        assertTrue(profiles.byId(owner.id)!!.bandEnabled)
        assertTrue(!profiles.byId(second.id)!!.bandEnabled)
    }

    @Test
    fun movingTheBandTakesItFromTheOtherAndLeavesOnlyOneReader() = runBlocking {
        val owner = profiles.create(profile("Owner", band = true))
        val second = profiles.create(profile("Second", band = true))

        profiles.setBandEnabled(second.id, true)

        assertTrue(profiles.byId(second.id)!!.bandEnabled)
        assertTrue(!profiles.byId(owner.id)!!.bandEnabled)
        val readers = profiles.observeActive().first().filter { it.bandEnabled }
        assertEquals(1, readers.size)
    }

    @Test
    fun theBandFallsBackToANewProfileWhenItsOwnerIsGone() = runBlocking {
        val owner = profiles.create(profile("Owner", band = true))
        profiles.setBandEnabled(owner.id, false)

        // Nobody reads the band now, so the next profile that asks for it gets it rather than ending up
        // on a phone where no profile reads the band and the Today screen simply loses its sync button.
        val second = profiles.create(profile("Second", band = true))

        assertTrue(profiles.byId(second.id)!!.bandEnabled)
    }

    @Test
    fun archivingHidesAProfileAndRestoringBringsItBack() = runBlocking {
        val created = profiles.create(profile())
        settings.setActiveProfile(created.id)

        profiles.archive(created.id)

        val stored = profiles.byId(created.id)!!
        assertTrue(stored.isArchived)
        assertEquals(1_760_000_000_000, stored.archivedAt)
        // A hidden profile must not stay the current one.
        assertNull(settings.activeProfileId())
        assertEquals(0, profiles.observeActive().first().size)
        assertEquals(1, profiles.observeAll().first().size)

        profiles.unarchive(created.id)

        assertTrue(!profiles.byId(created.id)!!.isArchived)
        assertEquals(1, profiles.observeActive().first().size)
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
    /**
     * Re-typing a weight on the same day corrects it instead of adding a reading.
     *
     * The weight screen saves whatever is in the field, and people weigh themselves, mistype, and type it again.
     * Two rows for one manual weigh-in would make the trend average a number the person had already replaced.
     */
    @Test
    fun manualWeightReplacesTheDayInsteadOfAppending() = runBlocking {
        val profileId = profiles.create(profile("Weight test")).id

        body.record(profileId, 80.0, today, BodyMeasurementRepository.SOURCE_MANUAL)
        body.record(profileId, 79.5, today, BodyMeasurementRepository.SOURCE_MANUAL)

        val rows = db.bodyMeasurementDao().weightsBetween(profileId, today.toString(), today.toString())
        assertEquals("one row per source per day", 1, rows.size)
        assertEquals(79.5, rows.single().weightKg!!, 0.001)

        // A different source on the same day is a different reading and must survive.
        body.record(profileId, 80.4, today, BodyMeasurementRepository.SOURCE_SCALE)
        val after = db.bodyMeasurementDao().weightsBetween(profileId, today.toString(), today.toString())
        assertEquals("two sources, two rows", 2, after.size)
    }

    /** The canonical day value prefers the typed weight over the instrument's. */
    @Test
    fun canonicalWeightPrefersManualOverScale() = runBlocking {
        val profileId = profiles.create(profile("Canonical test")).id

        body.record(profileId, 80.4, today, BodyMeasurementRepository.SOURCE_SCALE)
        body.record(profileId, 79.5, today, BodyMeasurementRepository.SOURCE_MANUAL)

        val perDay = CanonicalWeights(db).perDay(profileId, today, today)
        assertEquals(79.5, perDay.getValue(today), 0.001)
    }    /**
     * The chart's window query: energy and macros per day, and no row for a day without a log.
     *
     * A day with no entries must be **absent** rather than a zero row, because that is what tells the chart to
     * leave a gap instead of drawing a bar at zero — an unlogged day is missing, not empty.
     */
    @Test
    fun macroTotalsCoverOnlyLoggedDays() = runBlocking {
        val profileId = profiles.create(profile("Macro window")).id
        val store = ProductRepository(db) { 1_760_000_000_000 }
        val foods = FoodEntryRepository(db) { 1_760_000_000_000 }

        val oats = store.saveUserProduct(
            Product(
                name = "Oats, dry",
                source = ProductSource.USER,
                kcal100g = 389.0,
                protein100g = 16.9,
                fat100g = 6.9,
                carbs100g = 66.3,
                fiber100g = 10.6,
                sugar100g = 1.0,
                salt100g = 0.0,
            ),
        )

        val twoDaysAgo = today.minusDays(2)
        foods.log(
            profileId = profileId,
            product = oats,
            grams = 100.0,
            mealKey = MealDefaults.BREAKFAST,
            on = twoDaysAgo,
        )
        foods.log(
            profileId = profileId,
            product = oats,
            grams = 50.0,
            mealKey = MealDefaults.BREAKFAST,
            on = today,
        )
        // yesterday has no entry on purpose

        val rows = db.foodEntryDao().macroTotalsBetween(
            profileId,
            today.minusDays(6).toString(),
            today.toString(),
        )

        assertEquals("only the logged days come back", 2, rows.size)
        val byDate = rows.associateBy { it.localDate }
        assertNull("an unlogged day must be absent, not zero", byDate[today.minusDays(1).toString()])

        val twoAgo = byDate.getValue(twoDaysAgo.toString())
        assertEquals(389.0, twoAgo.kcal, 0.01)
        assertEquals(16.9, twoAgo.proteinG, 0.01)
        assertEquals(6.9, twoAgo.fatG, 0.01)
        assertEquals(66.3, twoAgo.carbsG, 0.01)

        val todayRow = byDate.getValue(today.toString())
        assertEquals(194.5, todayRow.kcal, 0.01)
        assertEquals(8.45, todayRow.proteinG, 0.01)
    }

    /** Soft-deleted entries are not eaten: the chart must not draw a bar for a meal that was removed. */
    @Test
    fun macroTotalsIgnoreDeletedEntries() = runBlocking {
        val profileId = profiles.create(profile("Macro deleted")).id
        val store = ProductRepository(db) { 1_760_000_000_000 }
        val foods = FoodEntryRepository(db) { 1_760_000_000_000 }

        val apple = store.saveUserProduct(
            Product(
                name = "Apple",
                source = ProductSource.USER,
                kcal100g = 52.0,
                protein100g = 0.3,
                fat100g = 0.2,
                carbs100g = 14.0,
                fiber100g = 2.4,
                sugar100g = 10.4,
                salt100g = 0.0,
            ),
        )

        val logged = foods.log(profileId = profileId, product = apple, grams = 200.0, mealKey = MealDefaults.SNACK, on = today)
        foods.delete(logged.id)

        val rows = db.foodEntryDao().macroTotalsBetween(profileId, today.toString(), today.toString())
        assertTrue("a deleted meal leaves no day row behind", rows.isEmpty())
    }}