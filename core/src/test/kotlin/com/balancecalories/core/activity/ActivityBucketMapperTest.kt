package com.balancecalories.core.activity

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bucket folding. The cases that matter are the ones where a naive implementation is wrong: a day that starts
 * at 04:00 rather than midnight, a bucket landing after midnight but before the day start, and averages that
 * must not be summed.
 */
class ActivityBucketMapperTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val utc: ZoneId = ZoneId.of("UTC")

    private fun bucket(metric: ActivityMetric, iso: String, value: Double) =
        ActivityBucket(metric, Instant.parse(iso), value)

    @Test
    fun `activity after midnight belongs to the previous local day`() {
        // 2026-11-06 01:30 Berlin = 00:30Z, which is inside the 5th's window when the day starts at 04:00.
        val days = ActivityBucketMapper.map(
            listOf(bucket(ActivityMetric.STEPS, "2026-11-06T00:30:00Z", 120.0)),
            zone = berlin,
            dayStartHour = 4,
        )

        assertEquals(setOf(LocalDate.of(2026, 11, 5)), days.keys)
        assertEquals(120, days.getValue(LocalDate.of(2026, 11, 5)).steps)
    }

    @Test
    fun `the day start hour moves the boundary in both directions`() {
        val instant = "2026-11-06T00:30:00Z" // 01:30 Berlin

        val midnight = ActivityBucketMapper.map(
            listOf(bucket(ActivityMetric.STEPS, instant, 10.0)),
            zone = berlin,
            dayStartHour = 0,
        )
        assertEquals(setOf(LocalDate.of(2026, 11, 6)), midnight.keys)

        val fourAm = ActivityBucketMapper.map(
            listOf(bucket(ActivityMetric.STEPS, instant, 10.0)),
            zone = berlin,
            dayStartHour = 4,
        )
        assertEquals(setOf(LocalDate.of(2026, 11, 5)), fourAm.keys)
    }

    @Test
    fun `windows of the same day are summed`() {
        val days = ActivityBucketMapper.map(
            listOf(
                bucket(ActivityMetric.STEPS, "2026-11-06T06:00:00Z", 1000.0),
                bucket(ActivityMetric.STEPS, "2026-11-06T07:00:00Z", 500.0),
                bucket(ActivityMetric.ACTIVE_KCAL, "2026-11-06T06:00:00Z", 40.0),
                bucket(ActivityMetric.ACTIVE_KCAL, "2026-11-06T07:00:00Z", 25.5),
            ),
            zone = berlin,
            dayStartHour = 4,
        )

        val day = days.getValue(LocalDate.of(2026, 11, 6))
        assertEquals(1500, day.steps)
        assertEquals(65.5, day.activeKcal!!, 1e-9)
        assertTrue(day.hasEnergy)
    }

    @Test
    fun `resting heart rate is averaged, not summed`() {
        val days = ActivityBucketMapper.map(
            listOf(
                bucket(ActivityMetric.RESTING_HR, "2026-11-06T06:00:00Z", 58.0),
                bucket(ActivityMetric.RESTING_HR, "2026-11-06T07:00:00Z", 62.0),
            ),
            zone = berlin,
            dayStartHour = 4,
        )

        // Summing would produce 120 bpm and a bug report about a heart condition.
        assertEquals(60, days.getValue(LocalDate.of(2026, 11, 6)).restingHr)
    }

    @Test
    fun `steps alone are data but not energy`() {
        val days = ActivityBucketMapper.map(
            listOf(bucket(ActivityMetric.STEPS, "2026-11-06T06:00:00Z", 300.0)),
            zone = berlin,
            dayStartHour = 4,
        )

        val day = days.getValue(LocalDate.of(2026, 11, 6))
        assertTrue(day.hasAnyData)
        assertFalse(day.hasEnergy, "steps cannot be converted into a burn figure")
        assertNull(day.activeKcal)
    }

    @Test
    fun `origins are collected per day so a phone baseline can be told from the band`() {
        // Health Connect merges every writer; without the origins a phone-written day and a band day look the same.
        val days = ActivityBucketMapper.map(
            listOf(
                ActivityBucket(ActivityMetric.TOTAL_KCAL, Instant.parse("2026-11-07T10:00:00Z"), 1858.25, "com.hihonor.health"),
                ActivityBucket(ActivityMetric.TOTAL_KCAL, Instant.parse("2026-11-07T11:00:00Z"), 120.0, "nodomain.freeyourgadget.gadgetbridge"),
                ActivityBucket(ActivityMetric.STEPS, Instant.parse("2026-11-08T10:00:00Z"), 500.0, "nodomain.freeyourgadget.gadgetbridge"),
            ),
            zone = utc,
            dayStartHour = 0,
        )

        assertEquals(
            setOf("com.hihonor.health", "nodomain.freeyourgadget.gadgetbridge"),
            days.getValue(LocalDate.of(2026, 11, 7)).origins,
        )
        assertEquals(
            setOf("nodomain.freeyourgadget.gadgetbridge"),
            days.getValue(LocalDate.of(2026, 11, 8)).origins,
        )
    }

    @Test
    fun `a day with no buckets simply does not appear`() {
        val days = ActivityBucketMapper.map(emptyList(), zone = berlin, dayStartHour = 4)
        assertTrue(days.isEmpty())
    }

    @Test
    fun `values are rounded to whole steps and minutes but energy keeps its decimals`() {
        val days = ActivityBucketMapper.map(
            listOf(
                bucket(ActivityMetric.STEPS, "2026-11-06T06:00:00Z", 99.6),
                bucket(ActivityMetric.SLEEP_MINUTES, "2026-11-06T06:00:00Z", 30.4),
                bucket(ActivityMetric.ACTIVE_KCAL, "2026-11-06T06:00:00Z", 12.345),
            ),
            zone = utc,
            dayStartHour = 0,
        )

        val day = days.getValue(LocalDate.of(2026, 11, 6))
        assertEquals(100, day.steps)
        assertEquals(30, day.sleepMinutes)
        assertEquals(12.345, day.activeKcal!!, 1e-9)
    }
}
