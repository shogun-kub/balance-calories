package com.balancecalories.app.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The Today row prints "100 г · 187 ккал" and, under it, the macro grams. Both are read by the same person in
 * the same glance, so both must follow one convention: the phone's. The first device build mixed them —
 * "Белки 1.1" over "187 ккал" — because the macro line reused the *editable field* formatter, which is
 * deliberately ASCII so a typed value can be parsed back.
 */
class NumberDisplayTest {

    private lateinit var original: Locale

    @Before
    fun pinRussian() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun decimalsUseTheLocaleSeparator() {
        assertEquals("22,5", displayNumber(22.5))
        assertEquals("0,3", displayNumber(0.3))
    }

    @Test
    fun wholeNumbersCarryNoTrailingSeparator() {
        assertEquals("20", displayNumber(20.0))
        assertEquals("0", displayNumber(0.0))
        assertEquals("100", displayNumber(100.0))
    }

    @Test
    fun oneDecimalIsEnoughAndRoundsHalfUp() {
        assertEquals("0,3", displayNumber(0.25))
        assertEquals("1,1", displayNumber(1.09))
    }

    @Test
    fun anEnglishPhoneSeesADot() {
        Locale.setDefault(Locale.US)
        assertEquals("22.5", displayNumber(22.5))
        assertEquals("20", displayNumber(20.0))
    }

    @Test
    fun aNonFiniteValuePrintsNothing() {
        assertEquals("", displayNumber(Double.NaN))
        assertEquals("", displayNumber(Double.POSITIVE_INFINITY))
    }
}
