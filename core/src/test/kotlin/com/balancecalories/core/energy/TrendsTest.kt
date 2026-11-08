package com.balancecalories.core.energy

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The trend, the recap and the projection. Every case here is a screen the owner will actually read. */
class TrendsTest {

    private val start = LocalDate.of(2026, 11, 1)

    @Test
    fun `the first weigh-in is the trend`() {
        val trend = WeightTrend.ewma(listOf(start to 80.0))
        assertEquals(80.0, trend.single().second, 1e-9)
    }

    @Test
    fun `a salty dinner moves the trend by a quarter, not by all of it`() {
        val trend = WeightTrend.ewma(
            listOf(
                start to 80.0,
                start.plusDays(1) to 81.0,
            ),
        )
        assertEquals(80.25, trend.last().second, 1e-9)
    }

    @Test
    fun `the trend follows a real loss without lagging forever`() {
        // A steady loss of 0.1 kg a day: after ten days the trend must have moved most of the way.
        val points = (0..10).map { start.plusDays(it.toLong()) to 80.0 - it * 0.1 }
        val trend = WeightTrend.ewma(points)

        assertEquals(80.0, trend.first().second, 1e-9)
        // The trend lags a linear ramp — that is what smoothing is — so it ends above the raw 79.0 and keeps
        // falling: what matters is direction and a much smaller amplitude than the day-to-day noise.
        assertTrue(trend.last().second > 79.0, "a smoothed trend trails the raw value")
        assertTrue(trend.last().second < 79.5, "but it must follow the loss, not ignore it")
        assertTrue(trend.last().second < trend[trend.size - 2].second, "the trend is still falling")
    }

    @Test
    fun `a gap of days does not weight the next reading more heavily`() {
        val withGap = WeightTrend.ewma(
            listOf(
                start to 80.0,
                start.plusDays(5) to 81.0,
            ),
        )
        val withoutGap = WeightTrend.ewma(
            listOf(
                start to 80.0,
                start.plusDays(1) to 81.0,
            ),
        )

        assertEquals(withoutGap.last().second, withGap.last().second, 1e-9)
    }

    @Test
    fun `unordered weigh-ins are sorted before smoothing`() {
        val trend = WeightTrend.ewma(
            listOf(
                start.plusDays(1) to 81.0,
                start to 80.0,
            ),
        )

        assertEquals(start, trend.first().first)
        assertEquals(80.25, trend.last().second, 1e-9)
    }

    @Test
    fun `the recap multiplies the average balance by the days`() {
        val days = (0..6).map { offset ->
            DayEnergy(start.plusDays(offset.toLong()), intakeKcal = 2000.0, burnKcal = 2500.0)
        }
        val recap = RecapCalculator.recap(days, weightTrend = emptyList())

        assertEquals(-500.0, recap.averageBalanceKcal!!, 1e-9)
        // -3500 kcal over the week = -0.4545 kg.
        assertEquals(-0.455, recap.predictedChangeKg!!, 0.001)
        assertNull(recap.actualChangeKg)
        assertNull(recap.disagreementKg)
        assertEquals(7, recap.streakDays)
    }

    @Test
    fun `the recap compares the prediction with the scale`() {
        val days = (0..6).map { offset ->
            DayEnergy(start.plusDays(offset.toLong()), intakeKcal = 2000.0, burnKcal = 2500.0)
        }
        val trend = WeightTrend.ewma(
            listOf(
                start to 80.0,
                start.plusDays(3) to 79.9,
                start.plusDays(6) to 79.8,
            ),
        )
        val recap = RecapCalculator.recap(days, trend)

        // The trend spans 80.0 -> 79.93 over the window (smoothing, not the raw 79.8): about -0.07 kg.
        assertEquals(-0.069, recap.actualChangeKg!!, 0.001)
        // The log promised -0.45; the scale delivered -0.07, so the app's expenditure is too optimistic by ~0.39 kg.
        assertEquals(0.386, recap.disagreementKg!!, 0.01)
    }

    @Test
    fun `a day without a log does not extend the streak`() {
        val days = listOf(
            DayEnergy(start.plusDays(2), 2000.0, 2400.0, logged = false),
            DayEnergy(start.plusDays(1), 2000.0, 2400.0),
            DayEnergy(start, 2000.0, 2400.0),
        )

        assertEquals(0, RecapCalculator.streak(days))
    }

    @Test
    fun `an empty week reports nothing rather than zeros`() {
        val recap = RecapCalculator.recap(emptyList(), emptyList())

        assertNull(recap.averageBalanceKcal)
        assertNull(recap.predictedChangeKg)
        assertEquals(0, recap.streakDays)
    }

    @Test
    fun `the projection counts days at the planned rate`() {
        // 5 kg at 0.5 kg/week = 10 weeks = 70 days.
        assertEquals(70, Projection.daysToGoal(currentKg = 80.0, goalKg = 75.0, weeklyRateKg = -0.5))
    }

    @Test
    fun `a rate pointing away from the goal has no projection`() {
        assertNull(Projection.daysToGoal(currentKg = 80.0, goalKg = 75.0, weeklyRateKg = 0.5))
        assertNull(Projection.daysToGoal(currentKg = 80.0, goalKg = 75.0, weeklyRateKg = 0.0))
    }

    @Test
    fun `a goal already met has no projection`() {
        assertNull(Projection.daysToGoal(currentKg = 75.0, goalKg = 75.0, weeklyRateKg = -0.5))
    }

    @Test
    fun `a projection beyond the horizon is refused rather than dated`() {
        // 20 kg at 0.1 kg/week is 1400 days: a date nobody would believe.
        assertNull(Projection.daysToGoal(currentKg = 95.0, goalKg = 75.0, weeklyRateKg = -0.1))
        assertNotNull(Projection.daysToGoal(currentKg = 95.0, goalKg = 75.0, weeklyRateKg = -0.3))
    }

    @Test
    fun `the projection date is the today plus the days`() {
        assertEquals(start.plusDays(70), Projection.dateToGoal(start, 70))
    }
}

/**
 * The automation rule: the default is to ask, and confidence gates the rest.
 *
 * These are the four combinations a person can actually be in, so they are the four cases.
 */
class TdeeApplyPolicyTest {

    @Test
    fun `by default nothing is applied without a tap`() {
        assertFalse(TdeeApplyPolicy.shouldApplyAutomatically(TdeeConfidence.HIGH, autoApplyEnabled = false))
        assertFalse(TdeeApplyPolicy.shouldApplyAutomatically(TdeeConfidence.MEDIUM, autoApplyEnabled = false))
    }

    @Test
    fun `opting in applies a medium estimate`() {
        assertTrue(TdeeApplyPolicy.shouldApplyAutomatically(TdeeConfidence.MEDIUM, autoApplyEnabled = true))
        assertTrue(TdeeApplyPolicy.shouldApplyAutomatically(TdeeConfidence.HIGH, autoApplyEnabled = true))
    }

    @Test
    fun `a low estimate never moves a target, even when opted in`() {
        // Four logged days describe a logging habit, not a metabolism.
        assertFalse(TdeeApplyPolicy.shouldApplyAutomatically(TdeeConfidence.LOW, autoApplyEnabled = true))
        assertFalse(TdeeApplyPolicy.shouldApplyAutomatically(TdeeConfidence.NONE, autoApplyEnabled = true))
    }

    @Test
    fun `only an estimate with some evidence is offered at all`() {
        assertTrue(TdeeApplyPolicy.isOfferable(TdeeConfidence.LOW))
        assertTrue(TdeeApplyPolicy.isOfferable(TdeeConfidence.MEDIUM))
        assertFalse(TdeeApplyPolicy.isOfferable(TdeeConfidence.NONE))
    }
}