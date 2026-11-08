package com.balancecalories.core.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The precedence rules for a day's burn figure. Each case below is a situation that occurs in real use, not a
 * permutation of the parameters.
 */
class DailyBurnTest {

    @Test
    fun `the band's total wins and is not added to anything`() {
        val burn = DailyBurn(
            quality = ActivityQuality.COMPLETE,
            totalKcal = 2450.0,
            activeKcal = 700.0,
            bmrKcal = 1749.0,
            palKcal = 2405.0,
        )

        // The band's total already includes resting metabolism; adding BMR would double-count it.
        assertEquals(2450.0, burn.burnedKcal, 1e-9)
        assertTrue(burn.burnedIsMeasured)
        assertFalse(burn.mixesFormulaBmr)
        assertEquals(BurnSource.BAND_TOTAL, burn.source)
    }

    @Test
    fun `active calories alone are combined with the app's own BMR`() {
        val burn = DailyBurn(
            quality = ActivityQuality.COMPLETE,
            activeKcal = 700.0,
            bmrKcal = 1749.0,
            palKcal = 2405.0,
        )

        assertEquals(2449.0, burn.burnedKcal, 1e-9)
        assertTrue(burn.burnedIsMeasured)
        assertTrue(burn.mixesFormulaBmr, "the resting half is still a formula")
        assertEquals(BurnSource.BAND_ACTIVE_PLUS_BMR, burn.source)
    }

    @Test
    fun `no band data falls back to the PAL formula and says so`() {
        val burn = DailyBurn.estimated(bmrKcal = 1749.0, palKcal = 2405.0)

        assertEquals(2405.0, burn.burnedKcal, 1e-9)
        assertFalse(burn.burnedIsMeasured)
        assertEquals(ActivityQuality.ESTIMATED, burn.quality)
        assertEquals(BurnSource.PAL_ESTIMATE, burn.source)
    }

    @Test
    fun `zero is a legitimate measured value and must not trigger the estimate`() {
        // A day the band recorded as genuinely zero activity: the number is measured, not missing.
        val burn = DailyBurn(
            quality = ActivityQuality.COMPLETE,
            activeKcal = 0.0,
            totalKcal = 1749.0,
            bmrKcal = 1749.0,
            palKcal = 2405.0,
        )

        assertEquals(1749.0, burn.burnedKcal, 1e-9)
        assertTrue(burn.burnedIsMeasured)
    }

    @Test
    fun `an empty day still reports the formula rather than nothing`() {
        // Nothing recorded, so every optional field is null: the estimate is the only honest answer.
        val burn = DailyBurn(
            quality = ActivityQuality.NO_BAND,
            bmrKcal = 1749.0,
            palKcal = 2405.0,
        )

        assertEquals(2405.0, burn.burnedKcal, 1e-9)
        assertFalse(burn.burnedIsMeasured)
        assertEquals(BurnSource.PAL_ESTIMATE, burn.source)
    }
}

/**
 * Quality is decided from what the app can observe, and the tests are written as the situations a person
 * actually hits: a day in progress, a day nobody opened the app on, a day with no band at all.
 */
class ActivityQualityRulesTest {

    @Test
    fun `a finished day with data and a sync after it closed is complete`() {
        val quality = ActivityQualityRules.classify(
            hasMeasuredData = true,
            isToday = false,
            syncedAfterWindowEnd = true,
        )

        assertEquals(ActivityQuality.COMPLETE, quality)
    }

    @Test
    fun `today is partial no matter how much data arrived`() {
        val quality = ActivityQualityRules.classify(
            hasMeasuredData = true,
            isToday = true,
            syncedAfterWindowEnd = false,
        )

        assertEquals(ActivityQuality.PARTIAL, quality)
    }

    @Test
    fun `a past day nobody looked at since it closed is partial`() {
        // The app was opened days later: the data may be there, but it was fetched mid-day.
        val quality = ActivityQualityRules.classify(
            hasMeasuredData = true,
            isToday = false,
            syncedAfterWindowEnd = false,
        )

        assertEquals(ActivityQuality.PARTIAL, quality)
    }

    @Test
    fun `no data at all is no band, not a zero`() {
        val quality = ActivityQualityRules.classify(
            hasMeasuredData = false,
            isToday = false,
            syncedAfterWindowEnd = true,
        )

        assertEquals(ActivityQuality.NO_BAND, quality)
    }

    @Test
    fun `energy no app claims is not a complete day`() {
        // Health Connect's platform layer writes a constant daily total with an empty origin set.
        val baseline = ActivityQualityRules.classify(
            hasMeasuredData = true,
            isToday = false,
            syncedAfterWindowEnd = true,
            hasAttributedOrigin = false,
        )
        assertEquals(ActivityQuality.PARTIAL, baseline)

        val band = ActivityQualityRules.classify(
            hasMeasuredData = true,
            isToday = false,
            syncedAfterWindowEnd = true,
            hasAttributedOrigin = true,
        )
        assertEquals(ActivityQuality.COMPLETE, band)
    }

    @Test
    fun `the estimate is a separate state from having no data`() {
        // NO_BAND says "we have nothing"; ESTIMATED says "we have nothing and the formula stood in".
        assertEquals(ActivityQuality.NO_BAND, ActivityQualityRules.classify(false, false, true))
        assertEquals(ActivityQuality.ESTIMATED, DailyBurn.estimated(1749.0, 2405.0).quality)
    }
}
