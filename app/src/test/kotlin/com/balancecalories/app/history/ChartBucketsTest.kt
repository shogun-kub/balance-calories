package com.balancecalories.app.history

import com.balancecalories.core.activity.ActivityQuality
import com.balancecalories.data.repo.DaySummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weekly bucketing, the arithmetic behind "one bar per week".
 *
 * Three things here are easy to get silently wrong and are pinned instead: a seven-day range must stay
 * per-day, sums must actually be sums, and a week with one estimated burn must not be drawn as a measured
 * week — a solid bar is a claim about all seven days.
 */
class ChartBucketsTest {

    private val start = LocalDate.of(2026, 10, 14)

    private fun day(
        offset: Int,
        intake: Double = 0.0,
        burn: Double = 1800.0,
        logged: Boolean = true,
        measured: Boolean = true,
        proteinG: Double = 0.0,
        fatG: Double = 0.0,
        carbsG: Double = 0.0,
    ) = DaySummary(
        date = start.plusDays(offset.toLong()),
        intakeKcal = intake,
        logged = logged,
        burnKcal = burn,
        burnIsMeasured = measured,
        quality = if (measured) ActivityQuality.COMPLETE else ActivityQuality.PARTIAL,
        weightKg = null,
        proteinG = proteinG,
        fatG = fatG,
        carbsG = carbsG,
    )

    @Test
    fun aSevenDayRangeIsNotAggregated() {
        val days = (0 until 7).map { day(it, intake = 2000.0) }

        val bucketed = bucketForChart(days, rangeDays = 7)

        assertEquals(7, bucketed.size)
        assertSame("a week of days is returned untouched, not rebuilt", days[0], bucketed[0])
    }

    @Test
    fun thirtyDaysBecomeFiveBarsOfSevenAndTwo() {
        val days = (0 until 30).map { day(it, intake = 100.0, proteinG = 10.0) }

        val bucketed = bucketForChart(days, rangeDays = 30)

        assertEquals(5, bucketed.size)
        assertEquals(start, bucketed.first().date)
        // Four full weeks, then the two days that are left - the newest bucket is the week being lived.
        assertEquals(700.0, bucketed[0].intakeKcal, 1e-9)
        assertEquals(70.0, bucketed[0].proteinG, 1e-9)
        assertEquals(200.0, bucketed[4].intakeKcal, 1e-9)
    }

    @Test
    fun oneEstimatedDayMakesTheWeekAnEstimate() {
        val days = (0 until 14).map { day(it, measured = it != 3) }

        val bucketed = bucketForChart(days, rangeDays = 14)

        assertEquals(2, bucketed.size)
        assertFalse("a week holding an estimate must not look measured", bucketed[0].burnIsMeasured)
        assertTrue("a week of measured days stays measured", bucketed[1].burnIsMeasured)
    }

    @Test
    fun aWeekWithOneLoggedDayIsALoggedWeek() {
        val days = (0 until 14).map { day(it, logged = it == 2) }

        val bucketed = bucketForChart(days, rangeDays = 14)

        assertTrue(bucketed[0].logged)
        assertFalse(bucketed[1].logged)
    }
}
