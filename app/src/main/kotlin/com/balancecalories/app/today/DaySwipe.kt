package com.balancecalories.app.today

import androidx.compose.ui.unit.dp

/** How far a finger has to travel sideways before Today treats it as "another day", not a shaky tap. */
internal val DaySwipeThreshold = 64.dp

/**
 * The change to Today's `dayOffset` a horizontal drag asks for: `1` is one day into the past, `-1` one day forward.
 *
 * A book, a calendar, a photo gallery: dragging to the **right** brings the earlier page, so the past sits on the
 * left. Zero means the drag was too short to be a decision, and the caller keeps the day it was on.
 */
internal fun swipeDayStep(totalDxPx: Float, thresholdPx: Float): Int = when {
    totalDxPx >= thresholdPx -> 1
    totalDxPx <= -thresholdPx -> -1
    else -> 0
}
