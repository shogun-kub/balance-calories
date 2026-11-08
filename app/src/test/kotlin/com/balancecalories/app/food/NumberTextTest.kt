package com.balancecalories.app.food

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The edit form is prefilled with these strings, so each case below is a value that was or could be seen in
 * a real field: OpenFoodFacts float noise, a whole number, a label's three decimals.
 */
class NumberTextTest {

    @Test
    fun floatNoiseFromOpenFoodFactsIsRoundedAway() {
        // Observed in the edit form for "Nutella & go!" before this existed.
        assertEquals("7.69", trimNumber(7.6899999999999995))
        // Three decimals are kept, so this rounds at the third one rather than being cut short.
        assertEquals("0.541", trimNumber(0.54054054054054))
    }

    @Test
    fun wholeNumbersHaveNoDecimalPoint() {
        assertEquals("500", trimNumber(500.0))
        assertEquals("25", trimNumber(25.0))
        assertEquals("0", trimNumber(0.0))
        assertEquals("0", trimNumber(-0.0))
    }

    @Test
    fun labelPrecisionIsKeptUpToThreeDecimals() {
        assertEquals("0.107", trimNumber(0.107))
        assertEquals("0.1", trimNumber(0.1))
        assertEquals("63.46", trimNumber(63.46))
        assertEquals("519.23", trimNumber(519.23))
    }

    @Test
    fun trailingZerosAreStrippedRatherThanPadded() {
        assertEquals("1.5", trimNumber(1.500))
        assertEquals("2", trimNumber(2.000))
    }

    @Test
    fun roundingIsHalfUpSoTheShownValueDoesNotUnderstate() {
        assertEquals("0.125", trimNumber(0.1249))
        assertEquals("0.125", trimNumber(0.1254))
        // 0.1255 exactly is not representable, but the direction of the rounding still holds.
        assertEquals("0.126", trimNumber(0.1256))
    }

    @Test
    fun anImpossibleValueBecomesAnEmptyFieldRatherThanNaN() {
        assertEquals("", trimNumber(Double.NaN))
        assertEquals("", trimNumber(Double.POSITIVE_INFINITY))
    }
}
