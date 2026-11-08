package com.balancecalories.core.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The chart's vertical range.
 *
 * These cases are all about one property: the axis must not exaggerate. A weight chart that rescales itself to a
 * 0.2 kg week draws a cliff out of water retention, and the person reading it concludes something happened.
 */
class WeightAxisTest {

    @Test
    fun `no values means no axis`() {
        assertNull(WeightAxis.of(emptyList()))
    }

    @Test
    fun `a single weigh-in keeps the floor span`() {
        val axis = WeightAxis.of(listOf(78.2))!!
        // the floor is a minimum, and the breathing room added on top is allowed to widen it
        assertTrue(axis.span >= WeightAxis.MIN_SPAN_KG)
        // centred on the only number we have, so the dot sits in the middle rather than on an edge
        assertTrue(axis.fraction(78.2) > 0.3f && axis.fraction(78.2) < 0.7f)
    }

    @Test
    fun `a flat week stays flat`() {
        val axis = WeightAxis.of(listOf(78.2, 78.2, 78.2))!!
        assertTrue(axis.span >= WeightAxis.MIN_SPAN_KG)
        val fractions = listOf(78.2, 78.2, 78.2).map { axis.fraction(it) }
        assertEquals(1, fractions.toSet().size)
    }

    @Test
    fun `the floor is a floor, not a target`() {
        // 12 kg of real change must not be squeezed into 2 kg
        val axis = WeightAxis.of(listOf(70.0, 82.0))!!
        assertTrue(axis.span >= 12.0)
        assertTrue(axis.minValue < 70.0)
        assertTrue(axis.maxValue > 82.0)
    }

    @Test
    fun `the goal widens the range when it is outside the data`() {
        val data = listOf(80.0, 81.0)
        val withGoal = WeightAxis.of(data + 75.0)!!
        assertTrue(withGoal.minValue <= 75.0)
        assertTrue(withGoal.maxValue >= 81.0)
    }

    @Test
    fun `bounds land on readable steps`() {
        val axis = WeightAxis.of(listOf(78.31, 79.07))!!
        assertEquals(0.0, axis.minValue % WeightAxis.STEP_KG, 1e-9)
        assertEquals(0.0, axis.maxValue % WeightAxis.STEP_KG, 1e-9)
    }

    @Test
    fun `pounds get pound steps and their own floor`() {
        // the same two weigh-ins in pounds, with the imperial step and floor
        val axis = WeightAxis.of(listOf(172.6, 174.1), minSpan = 4.4, step = WeightAxis.STEP_LB)!!
        assertEquals(0.0, axis.minValue % 1.0, 1e-9)
        assertEquals(0.0, axis.maxValue % 1.0, 1e-9)
        assertTrue(axis.span >= 4.4)
    }

    @Test
    fun `fractions run from bottom to top and clamp outside`() {
        val axis = WeightAxis.of(listOf(76.0, 80.0))!!
        assertEquals(0f, axis.fraction(axis.minValue), 1e-6f)
        assertEquals(1f, axis.fraction(axis.maxValue), 1e-6f)
        assertTrue(axis.fraction(axis.minValue - 5) == 0f)
        assertTrue(axis.fraction(axis.maxValue + 5) == 1f)
        // a heavier body is drawn higher
        assertTrue(axis.fraction(79.0) > axis.fraction(77.0))
    }

    @Test
    fun `a non-finite row is dropped instead of poisoning the range`() {
        val axis = WeightAxis.of(listOf(78.0, Double.NaN, Double.POSITIVE_INFINITY, 80.0))!!
        assertTrue(axis.minValue.isFinite() && axis.maxValue.isFinite())
        assertTrue(axis.minValue < 78.0 && axis.maxValue > 80.0)
    }

    @Test
    fun `all-non-finite values behave like no values`() {
        assertNull(WeightAxis.of(listOf(Double.NaN, Double.NEGATIVE_INFINITY)))
    }
}
