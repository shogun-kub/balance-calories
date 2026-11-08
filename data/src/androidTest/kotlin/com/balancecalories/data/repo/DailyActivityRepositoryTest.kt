package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.activity.ActivityBucket
import com.balancecalories.core.activity.ActivityMetric
import com.balancecalories.core.activity.ActivityQuality
import com.balancecalories.core.activity.BurnSource
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.DailyActivityEntity
import com.balancecalories.data.db.entity.ProfileEntity
import com.balancecalories.data.health.ActivityRead
import com.balancecalories.data.health.ActivitySource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The activity repository against synthetic band readings.
 *
 * These tests are the reason the source is an interface: Health Connect cannot be driven from a test without
 * real permissions and a real band, so the property under test is the repository's own arithmetic — which day a
 * bucket belongs to, what quality a day ends up with, and what the burn figure claims about its origin.
 *
 * The rules being pinned, each of which is a way the balance could quietly lie:
 * - a bucket after midnight belongs to the previous day when the day starts at 04:00;
 * - a day with no readings is stored as `no_band`, not as zero burned;
 * - today is never `complete`, however much data arrived;
 * - a re-sync corrects the row instead of adding a second one;
 * - no permission writes nothing and says so.
 */
@RunWith(AndroidJUnit4::class)
class DailyActivityRepositoryTest {

    private lateinit var db: BalanceCaloriesDatabase
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val hour = 4

    /** 2026-11-08 12:00 Moscow — the "now" every test starts from. */
    private var now = Instant.parse("2026-11-08T09:00:00Z").toEpochMilli()

    private lateinit var profile: Profile

    private val BAND = "nodomain.freeyourgadget.gadgetbridge"

    private val buckets = mutableListOf<ActivityBucket>()
    private var canned: ActivityRead? = null

    private fun source() = ActivitySource { _, _ -> canned ?: ActivityRead.Values(buckets.toList()) }

    private fun repository() = DailyActivityRepository(db, source()) { now }

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
                    birthDate = LocalDate.of(1990, 1, 1).toString(),
                    heightCm = 180.0,
                    activityLevel = ActivityLevel.MODERATE,
                    goalRateKgPerWeek = -0.5,
                    bandEnabled = true,
                    dayStartHour = hour,
                    createdAt = now,
                ),
            )
            profile = Profile(
                id = 1L,
                name = "Tester",
                sex = Sex.MALE,
                birthDate = LocalDate.of(1990, 1, 1),
                heightCm = 180.0,
                activityLevel = ActivityLevel.MODERATE,
                goalRateKgPerWeek = -0.5,
                bandEnabled = true,
                dayStartHour = hour,
                createdAt = now,
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bucket(metric: ActivityMetric, iso: String, value: Double, origin: String? = BAND) {
        buckets += ActivityBucket(metric, Instant.parse(iso), value, origin)
    }

    private suspend fun row(date: LocalDate): DailyActivityEntity? =
        db.dailyActivityDao().forDay(profile.id, date.toString())

    @Test
    fun aBucketAfterMidnightLandsOnThePreviousLocalDay() = runBlocking {
        // 01:30 Moscow on the 8th is inside the 7th's window when the day starts at 04:00.
        bucket(ActivityMetric.STEPS, "2026-11-07T22:30:00Z", 800.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        bucket(ActivityMetric.ACTIVE_KCAL, "2026-11-07T22:30:00Z", 30.0, origin = "nodomain.freeyourgadget.gadgetbridge")

        repository().sync(profile, days = 2, zone = zone)

        val seventh = row(LocalDate.of(2026, 11, 7))
        assertNotNull(seventh)
        assertEquals(800, seventh!!.steps)
        assertEquals(30.0, seventh.activeKcal!!, 1e-9)
        assertNull("the 8th must not have inherited the night", row(LocalDate.of(2026, 11, 8))?.steps)
    }

    @Test
    fun aDayWithNoReadingsIsStoredAsNoBandRatherThanZero() = runBlocking {
        repository().sync(profile, days = 2, zone = zone)

        val eighth = row(LocalDate.of(2026, 11, 8))
        assertNotNull("every day in the range gets a row", eighth)
        assertEquals("no_band", eighth!!.quality)
        assertNull(eighth.activeKcal)
        assertNull(eighth.totalKcal)
    }

    @Test
    fun todayIsPartialAndYesterdayIsCompleteOnceSyncedAfterClosing() = runBlocking {
        bucket(ActivityMetric.TOTAL_KCAL, "2026-11-07T10:00:00Z", 2400.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        bucket(ActivityMetric.TOTAL_KCAL, "2026-11-08T07:00:00Z", 900.0, origin = "nodomain.freeyourgadget.gadgetbridge")

        repository().sync(profile, days = 2, zone = zone)

        assertEquals("partial", row(LocalDate.of(2026, 11, 8))!!.quality)
        assertEquals("complete", row(LocalDate.of(2026, 11, 7))!!.quality)
    }

    @Test
    fun aPastDaySyncedMidDayStaysPartial() = runBlocking {
        // Now is noon on the 8th; the window for the 7th ended at 04:00 on the 8th, which is in the past, so
        // this sync did look at it after it closed. The interesting case is the opposite: sync during the day.
        bucket(ActivityMetric.TOTAL_KCAL, "2026-11-07T10:00:00Z", 2400.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        now = Instant.parse("2026-11-07T15:00:00Z").toEpochMilli() // 18:00 on the 7th, window still open

        repository().sync(profile, days = 1, zone = zone)

        assertEquals("partial", row(LocalDate.of(2026, 11, 7))!!.quality)
    }

    @Test
    fun aResyncCorrectsTheRowInsteadOfAddingAnother() = runBlocking {
        bucket(ActivityMetric.STEPS, "2026-11-07T10:00:00Z", 1000.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        repository().sync(profile, days = 2, zone = zone)

        buckets.clear()
        bucket(ActivityMetric.STEPS, "2026-11-07T10:00:00Z", 4000.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        repository().sync(profile, days = 2, zone = zone)

        val seventh = row(LocalDate.of(2026, 11, 7))!!
        assertEquals(4000, seventh.steps)
        assertEquals("one row per day, replaced", 2, dayCount())
    }

    private suspend fun dayCount(): Int {
        var count = 0
        var day = LocalDate.of(2026, 11, 1)
        while (day.isBefore(LocalDate.of(2026, 11, 15))) {
            if (row(day) != null) count++
            day = day.plusDays(1)
        }
        return count
    }

    @Test
    fun aMeasuredTotalIsReportedAsMeasuredAndCarriesSteps() = runBlocking {
        bucket(ActivityMetric.TOTAL_KCAL, "2026-11-08T06:00:00Z", 1500.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        bucket(ActivityMetric.STEPS, "2026-11-08T06:00:00Z", 3200.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        bucket(ActivityMetric.RESTING_HR, "2026-11-08T06:00:00Z", 61.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        repository().sync(profile, days = 1, zone = zone)

        val burn = repository().burn(profile.id, LocalDate.of(2026, 11, 8), bmrKcal = 1749.0, palKcal = 2405.0)

        assertEquals(1500.0, burn.burnedKcal, 1e-9)
        assertTrue(burn.burnedIsMeasured)
        assertEquals(BurnSource.BAND_TOTAL, burn.source)
        assertEquals(ActivityQuality.PARTIAL, burn.quality)
        assertEquals(3200, burn.steps)
        assertEquals(61, burn.restingHr)
    }

    @Test
    fun activeCaloriesAloneAreCombinedWithTheSuppliedBmr() = runBlocking {
        bucket(ActivityMetric.ACTIVE_KCAL, "2026-11-08T06:00:00Z", 700.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        repository().sync(profile, days = 1, zone = zone)

        val burn = repository().burn(profile.id, LocalDate.of(2026, 11, 8), bmrKcal = 1749.0, palKcal = 2405.0)

        assertEquals(2449.0, burn.burnedKcal, 1e-9)
        assertTrue(burn.burnedIsMeasured)
        assertTrue(burn.mixesFormulaBmr)
    }

    @Test
    fun aDayWithNoBandDataReportsThePalEstimate() = runBlocking {
        repository().sync(profile, days = 1, zone = zone)

        val burn = repository().burn(profile.id, LocalDate.of(2026, 11, 8), bmrKcal = 1749.0, palKcal = 2405.0)

        assertEquals(2405.0, burn.burnedKcal, 1e-9)
        assertFalse(burn.burnedIsMeasured)
        assertEquals(ActivityQuality.ESTIMATED, burn.quality)
    }

    @Test
    fun aPlatformBaselineIsReportedAsAnEstimateNotAsAMeasurement() = runBlocking {
        // The Honor writes a constant daily total with no origin; 1858.25 kcal is not a measurement of a person.
        buckets += ActivityBucket(
            ActivityMetric.TOTAL_KCAL,
            Instant.parse("2026-11-08T06:00:00Z"),
            1858.25,
            origin = null,
        )
        repository().sync(profile, days = 1, zone = zone)

        val burn = repository().burn(profile.id, LocalDate.of(2026, 11, 8), bmrKcal = 1749.0, palKcal = 2405.0)

        assertEquals(2405.0, burn.burnedKcal, 1e-9)
        assertFalse("a baseline must not pass as a band measurement", burn.burnedIsMeasured)
        assertEquals(ActivityQuality.ESTIMATED, burn.quality)
        assertEquals("partial", row(LocalDate.of(2026, 11, 8))!!.quality)
    }

    @Test
    fun aDayNeverSyncedReportsThePalEstimate() = runBlocking {
        val burn = repository().burn(profile.id, LocalDate.of(2026, 10, 1), bmrKcal = 1749.0, palKcal = 2405.0)

        assertEquals(2405.0, burn.burnedKcal, 1e-9)
        assertEquals(ActivityQuality.ESTIMATED, burn.quality)
    }

    @Test
    fun noPermissionWritesNothingAndReportsWhy() = runBlocking {
        canned = ActivityRead.NoPermission

        val result = repository().sync(profile, days = 2, zone = zone)

        assertEquals(DailyActivityRepository.SyncResult.NoPermission, result)
        assertNull(row(LocalDate.of(2026, 11, 7)))
        assertNull(repository().lastSyncAt(profile.id))
    }

    @Test
    fun aFailedReadIsReportedAsFailureNotMissingPlatform() = runBlocking {
        canned = ActivityRead.Failed("boom")

        val result = repository().sync(profile, days = 1, zone = zone)

        assertEquals(DailyActivityRepository.SyncResult.Failed("boom"), result)
    }

    @Test
    fun theLastSyncTimestampIsRememberedPerProfile() = runBlocking {
        repository().sync(profile, days = 1, zone = zone)

        assertEquals(now, repository().lastSyncAt(profile.id))
        assertNull("another profile must not inherit it", repository().lastSyncAt(99L))
    }

    @Test
    fun theSyncReportsHowManyDaysHadBandEnergy() = runBlocking {
        bucket(ActivityMetric.TOTAL_KCAL, "2026-11-07T10:00:00Z", 2400.0, origin = "nodomain.freeyourgadget.gadgetbridge")
        bucket(ActivityMetric.TOTAL_KCAL, "2026-11-08T06:00:00Z", 900.0, origin = "nodomain.freeyourgadget.gadgetbridge")

        val result = repository().sync(profile, days = 3, zone = zone) as DailyActivityRepository.SyncResult.Synced

        assertEquals(3, result.days)
        assertEquals(2, result.daysWithEnergy)
    }
}
