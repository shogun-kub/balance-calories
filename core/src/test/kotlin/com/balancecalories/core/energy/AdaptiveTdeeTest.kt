package com.balancecalories.core.energy

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The adaptive TDEE is the one place where the app replaces a formula with a measurement, so every guard is
 * pinned with the situation it protects against: a half-logged fortnight, a single weigh-in, a scale that
 * disagrees with arithmetic.
 */
class AdaptiveTdeeTest {

    private val start = LocalDate.of(2026, 10, 26)

    private fun day(
        offset: Int,
        intake: Double? = 2000.0,
        weight: Double? = null,
    ) = AdaptiveDay(start.plusDays(offset.toLong()), intake, weight)

    /** A clean fortnight at a steady intake, losing 0.5 kg overall. */
    private fun cleanWindow(): List<AdaptiveDay> = (0 until 14).map { offset ->
        day(
            offset = offset,
            intake = 2000.0,
            weight = when (offset) {
                0 -> 80.0
                7 -> 79.8
                13 -> 79.5
                else -> null
            },
        )
    }

    @Test
    fun `a clean fortnight produces the tdee implied by intake and weight change`() {
        // 2000 kcal/day, -0.5 kg over 13 days = 3850 kcal deficit = 296.15 kcal/day, so TDEE = 2296.15.
        val estimate = AdaptiveTdee.estimate(cleanWindow(), bmrKcal = 1749.0)

        assertTrue(estimate.available, "a clean window must produce an estimate")
        assertEquals(2296.15, estimate.tdeeKcal!!, 0.01)
        assertEquals(2000.0, estimate.averageIntakeKcal!!, 1e-9)
        assertEquals(-0.5, estimate.weightChangeKg!!, 1e-9)
        assertEquals(14, estimate.loggedDays)
        assertEquals(3, estimate.weighIns)
    }

    @Test
    fun `losing weight raises the estimate above what was eaten`() {
        val estimate = AdaptiveTdee.estimate(cleanWindow(), bmrKcal = 1749.0)
        assertTrue(
            estimate.tdeeKcal!! > estimate.averageIntakeKcal!!,
            "a deficit on the scale means expenditure above intake",
        )
    }

    @Test
    fun `gaining weight lowers the estimate below what was eaten`() {
        val window = (0 until 14).map { offset ->
            day(offset, intake = 2600.0, weight = when (offset) { 0 -> 80.0; 7 -> 80.5; 13 -> 81.0; else -> null })
        }

        val estimate = AdaptiveTdee.estimate(window, bmrKcal = 1749.0)

        // +1.0 kg over 13 days = 7700 surplus = 592 kcal/day, so TDEE = 2600 - 592 = 2008.
        assertEquals(2007.69, estimate.tdeeKcal!!, 0.01)
        assertTrue(estimate.tdeeKcal!! < estimate.averageIntakeKcal!!)
    }

    @Test
    fun `a half-logged window refuses instead of averaging what it has`() {
        val window = (0 until 14).map { offset ->
            day(
                offset = offset,
                intake = if (offset < 6) 2000.0 else null,
                weight = when (offset) { 0 -> 80.0; 7 -> 79.8; 13 -> 79.5; else -> null },
            )
        }

        val estimate = AdaptiveTdee.estimate(window, bmrKcal = 1749.0)

        assertNull(estimate.tdeeKcal)
        assertEquals(AdaptiveTdee.Refusal.NOT_ENOUGH_LOGGED_DAYS, estimate.refusal)
        assertEquals(6, estimate.loggedDays)
    }

    @Test
    fun `one weigh-in is not a trend`() {
        val window = (0 until 14).map { offset -> day(offset, intake = 2000.0, weight = if (offset == 0) 80.0 else null) }

        val estimate = AdaptiveTdee.estimate(window, bmrKcal = 1749.0)

        assertEquals(AdaptiveTdee.Refusal.NOT_ENOUGH_WEIGH_INS, estimate.refusal)
    }

    @Test
    fun `weigh-ins on consecutive days do not cover enough time`() {
        val window = (0 until 14).map { offset ->
            day(offset, intake = 2000.0, weight = if (offset in 0..2) 80.0 + offset * 0.1 else null)
        }

        val estimate = AdaptiveTdee.estimate(window, bmrKcal = 1749.0)

        assertEquals(AdaptiveTdee.Refusal.WEIGH_INS_TOO_CLOSE, estimate.refusal)
    }

    @Test
    fun `an impossible weight change is refused rather than fitted`() {
        // 8 kg in a week is a bathroom scale on a rug, not a fortnight of dieting.
        val window = (0 until 14).map { offset ->
            day(offset, intake = 2000.0, weight = when (offset) { 0 -> 80.0; 7 -> 76.0; 13 -> 72.0; else -> null })
        }

        val estimate = AdaptiveTdee.estimate(window, bmrKcal = 1749.0)

        assertEquals(AdaptiveTdee.Refusal.IMPLAUSIBLE_WEIGHT_CHANGE, estimate.refusal)
        assertNull(estimate.tdeeKcal)
    }

    @Test
    fun `a tdee below bmr is refused as implausible`() {
        // Eating 1200 kcal while gaining fast implies an expenditure below resting metabolism.
        val window = (0 until 14).map { offset ->
            day(offset, intake = 1200.0, weight = when (offset) { 0 -> 80.0; 7 -> 80.9; 13 -> 81.8; else -> null })
        }

        val estimate = AdaptiveTdee.estimate(window, bmrKcal = 1749.0)

        assertEquals(AdaptiveTdee.Refusal.IMPLAUSIBLE_TDEE, estimate.refusal)
    }

    @Test
    fun `confidence follows coverage and never how flattering the number is`() {
        assertEquals(TdeeConfidence.HIGH, AdaptiveTdee.confidenceFor(14, 14, 7))
        assertEquals(TdeeConfidence.MEDIUM, AdaptiveTdee.confidenceFor(12, 14, 3))
        assertEquals(TdeeConfidence.LOW, AdaptiveTdee.confidenceFor(9, 14, 3))
        assertEquals(TdeeConfidence.NONE, AdaptiveTdee.confidenceFor(4, 14, 3))
    }

    @Test
    fun `a single update moves the target by at most the clamp`() {
        val next = AdaptiveTdee.nextTarget(
            currentTargetKcal = 1924,
            // 3000 - 550 = 2450, i.e. +526 unclamped; the clamp is what this test is about.
            estimateTdeeKcal = 3000.0,
            goalRateKgPerWeek = -0.5,
            bmrKcal = 1749.0,
            sex = Sex.MALE,
        )

        assertEquals(1924 + 150, next)
    }

    @Test
    fun `the update never goes below the safety floor`() {
        val next = AdaptiveTdee.nextTarget(
            currentTargetKcal = 1924,
            estimateTdeeKcal = 1500.0,
            goalRateKgPerWeek = -1.0,
            bmrKcal = 1749.0,
            sex = Sex.MALE,
        )

        // tdee 1500 - 1100 = 400, floored to max(1.1 x 1749, 1500 male) = 1923.6, so the target may move only
        // by the -0.4 kcal the floor allows — never the -150 the clamp would otherwise permit.
        assertEquals(1924, next)
        assertTrue(next >= 1923, "the target must not fall through the floor")
    }

    @Test
    fun `no estimate leaves the target alone`() {
        val next = AdaptiveTdee.nextTarget(
            currentTargetKcal = 1924,
            estimateTdeeKcal = null,
            goalRateKgPerWeek = -0.5,
            bmrKcal = 1749.0,
            sex = Sex.MALE,
        )

        assertEquals(1924, next)
    }

    @Test
    fun `an estimate inside the clamps lands on the value it implies`() {
        // tdee 2000, goal -0.5 kg/week = -550 kcal/day, so 1450, which the floor lifts to 1923.6 -> clamped.
        val next = AdaptiveTdee.nextTarget(
            currentTargetKcal = 2100,
            estimateTdeeKcal = 1900.0,
            goalRateKgPerWeek = -0.25,
            bmrKcal = 1749.0,
            sex = Sex.MALE,
        )

        // 1900 + (-0.25 * 7700 / 7) = 1900 - 275 = 1625, floored to 1923.6, clamp from 2100 is -150 -> 1950.
        assertEquals(1950, next)
    }
}
