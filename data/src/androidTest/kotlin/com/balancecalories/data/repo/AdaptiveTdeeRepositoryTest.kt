package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.energy.TdeeConfidence
import com.balancecalories.core.energy.toStorage
import com.balancecalories.core.food.MealDefaults
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.ProfileEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The adaptive TDEE against real rows in a real database.
 *
 * The arithmetic is pinned in `AdaptiveTdeeTest`; what these tests protect is the glue, because that is where a
 * plausible-looking number can be produced from evidence that does not exist: an unlogged day must not be read
 * as zero kcal, two weigh-ins on one day must become one point, and a thin window must be shown without
 * silently rewriting the target.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveTdeeRepositoryTest {

    private lateinit var db: BalanceCaloriesDatabase
    private lateinit var food: FoodEntryRepository
    private lateinit var body: BodyMeasurementRepository
    private lateinit var products: ProductRepository
    private lateinit var targets: TargetRepository
    private lateinit var repo: AdaptiveTdeeRepository
    private lateinit var profile: Profile

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 8)
    private val now = Instant.parse("2026-11-08T09:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                BalanceCaloriesDatabase::class.java,
            ).build()
            db.profileDao().insert(
                ProfileEntity(
                    id = 1L,
                    name = "Tester",
                    sex = Sex.MALE,
                    birthDate = "1990-01-01",
                    heightCm = 180.0,
                    activityLevel = ActivityLevel.MODERATE,
                    goalRateKgPerWeek = -0.5,
                    dayStartHour = 4,
                    createdAt = now,
                ),
            )
            food = FoodEntryRepository(db) { now }
            body = BodyMeasurementRepository(db) { now }
            products = ProductRepository(db) { now }
            targets = TargetRepository(db)
            repo = AdaptiveTdeeRepository(db) { now }
            profile = Profile(
                id = 1L,
                name = "Tester",
                sex = Sex.MALE,
                birthDate = LocalDate.of(1990, 1, 1),
                heightCm = 180.0,
                activityLevel = ActivityLevel.MODERATE,
                goalRateKgPerWeek = -0.5,
                dayStartHour = 4,
                createdAt = now,
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun logDay(date: LocalDate, kcal: Double) {
        val product = products.saveUserProduct(
            Product(
                name = "Test food",
                source = ProductSource.USER,
                kcal100g = kcal,
                protein100g = 0.0,
                fat100g = 0.0,
                carbs100g = 100.0,
            ),
        )
        food.log(profileId = profile.id, product = product, grams = 100.0, mealKey = MealDefaults.LUNCH, on = date)
    }

    private suspend fun weigh(
        date: LocalDate,
        kg: Double,
        source: String = BodyMeasurementRepository.SOURCE_MANUAL,
    ) {
        // A distinct measured_at per weigh-in: the table is keyed on (profile, measured_at), so a frozen clock
        // would let three weigh-ins overwrite each other and the window would look like a single point.
        body.record(
            profileId = profile.id,
            weightKg = kg,
            localDate = date,
            source = source,
            measuredAt = date.toEpochDay() * 86_400_000L + 8 * 3_600_000L,
        )
    }

    /**
     * A full window: 13 of the 14 days logged, and three weigh-ins 80.0 -> 79.5.
     *
     * Every weigh-in sits inside `[today-14, today-1]`, because the window ends yesterday by design — today's
     * intake is partial and would drag the average down.
     */
    private suspend fun fullWindow(intakeKcal: Double = 2000.0) {
        for (offset in 1..13) {
            logDay(today.minusDays(offset.toLong()), intakeKcal)
        }
        weigh(today.minusDays(13), 80.0)
        weigh(today.minusDays(7), 79.8)
        weigh(today.minusDays(1), 79.5)
    }

    @Test
    fun aFullFortnightProducesAnEstimateFromRealRows() = runBlocking {
        fullWindow()

        val outcome = repo.outcome(profile, bmrKcal = 1749.0, currentTargetKcal = 1924, zone = zone)

        assertTrue(outcome.estimate.available)
        assertEquals(2000.0, outcome.estimate.averageIntakeKcal!!, 1e-9)
        // -0.5 kg over the 12 days between the first and last weigh-in = 320.83 kcal/day, so TDEE = 2320.83.
        assertEquals(2320.83, outcome.estimate.tdeeKcal!!, 1.0)
        assertEquals(today.minusDays(1), outcome.to)
        assertEquals(today.minusDays(14), outcome.from)
    }

    @Test
    fun anUnloggedDayIsMissingEvidenceRatherThanZeroCalories() = runBlocking {
        fullWindow()

        val outcome = repo.outcome(profile, bmrKcal = 1749.0, currentTargetKcal = 1924, zone = zone)

        val neverLogged = outcome.days.first { it.date == today.minusDays(14) }
        assertNull("the first window day was never logged", neverLogged.intakeKcal)
        // Counting it as 0 would pull the average down by ~140 kcal; the average must stay at the logged value.
        assertEquals(2000.0, outcome.estimate.averageIntakeKcal!!, 1e-9)
    }

    @Test
    fun twoWeighInsOnOneDayBecomeOneCanonicalPoint() = runBlocking {
        fullWindow()
        // A second source on the same day, disagreeing by 1.5 kg.
        weigh(today.minusDays(1), 78.0, BodyMeasurementRepository.SOURCE_HEALTH_CONNECT)

        val outcome = repo.outcome(profile, bmrKcal = 1749.0, currentTargetKcal = 1924, zone = zone)

        val yesterday = outcome.days.filter { it.date == today.minusDays(1) }.mapNotNull { it.weightKg }
        assertEquals(1, yesterday.size)
        assertEquals("manual outranks Health Connect", 79.5, yesterday.single(), 1e-9)
    }

    @Test
    fun aSparseWindowIsShownButNotApplied() = runBlocking {
        // Four logged days and two weigh-ins: enough to look at, not enough to rewrite a target.
        for (offset in 1..4) logDay(today.minusDays(offset.toLong()), 2000.0)
        weigh(today.minusDays(10), 80.0)
        weigh(today.minusDays(1), 79.0)

        val outcome = repo.outcome(profile, bmrKcal = 1749.0, currentTargetKcal = 1924, zone = zone)
        val applied = repo.apply(outcome, profile.id, today)

        assertFalse(outcome.estimate.available)
        assertFalse("a refused estimate must not move the target", applied)
        assertNull(targets.effectiveOn(profile.id, today))
    }

    @Test
    fun applyingWritesATargetThatRemembersItsEvidence() = runBlocking {
        // Eating 2600 a day while losing weight implies a TDEE well above the current target, so the update has
        // somewhere to go: at the safety floor the correct answer is "no change", which the next test covers.
        fullWindow(intakeKcal = 2600.0)

        val outcome = repo.outcome(profile, bmrKcal = 1749.0, currentTargetKcal = 1924, zone = zone)
        val applied = repo.apply(outcome, profile.id, today)

        assertTrue("a clean fortnight is applied", applied)
        assertTrue("and it raises the target", outcome.proposedTargetKcal > outcome.currentTargetKcal)

        val stored = targets.effectiveOn(profile.id, today)!!
        assertEquals(TargetRepository.MODE_ADAPTIVE, stored.mode)
        assertEquals(outcome.proposedTargetKcal, stored.kcal)
        assertEquals(TdeeConfidence.MEDIUM.toStorage(), stored.tdeeConfidence)
        assertEquals(2920.83, stored.tdeeKcal!!, 1.0)
    }

    @Test
    fun aTargetAtTheFloorIsNotLoweredByALowEstimate() = runBlocking {
        fullWindow()

        val outcome = repo.outcome(profile, bmrKcal = 1749.0, currentTargetKcal = 1924, zone = zone)
        val applied = repo.apply(outcome, profile.id, today)

        // 2320.83 - 550 = 1770.83, which the floor lifts back to 1923.6; 1924 is already there.
        assertEquals(1924, outcome.proposedTargetKcal)
        assertFalse("nothing to change is not a failure to record", applied)
        assertNull(targets.effectiveOn(profile.id, today))
    }

    @Test
    fun applyingTwiceInADayDoesNotStackContradictoryTargets() = runBlocking {
        fullWindow(intakeKcal = 2600.0)
        val outcome = repo.outcome(profile, bmrKcal = 1749.0, currentTargetKcal = 1924, zone = zone)

        repo.apply(outcome, profile.id, today)
        repo.apply(outcome, profile.id, today)

        val stored = targets.effectiveOn(profile.id, today)!!
        assertEquals(outcome.proposedTargetKcal, stored.kcal)
    }
}
