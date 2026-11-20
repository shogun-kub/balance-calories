package com.balancecalories.app.today

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one decision behind the swipe: which way, and whether it counts at all.
 *
 * The gesture itself cannot be tested without a device, but this can — and it is the part that would be wrong
 * silently (a swipe that goes forward into the future, or a drag on a chart that flips the day).
 */
class DaySwipeTest {

    private val threshold = 64f

    @Test
    fun draggingRightGoesBackADay() {
        assertEquals(1, swipeDayStep(totalDxPx = 200f, thresholdPx = threshold))
        assertEquals(1, swipeDayStep(totalDxPx = threshold, thresholdPx = threshold))
    }

    @Test
    fun draggingLeftComesForward() {
        assertEquals(-1, swipeDayStep(totalDxPx = -200f, thresholdPx = threshold))
        assertEquals(-1, swipeDayStep(totalDxPx = -threshold, thresholdPx = threshold))
    }

    @Test
    fun aShortDragIsNotASwipe() {
        assertEquals(0, swipeDayStep(totalDxPx = 0f, thresholdPx = threshold))
        assertEquals(0, swipeDayStep(totalDxPx = 63f, thresholdPx = threshold))
        assertEquals(0, swipeDayStep(totalDxPx = -63.9f, thresholdPx = threshold))
    }
}
