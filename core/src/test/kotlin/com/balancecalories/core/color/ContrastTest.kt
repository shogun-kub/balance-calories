package com.balancecalories.core.color

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The contrast formula, pinned against values that can be checked by hand.
 *
 * WCAG's own worked examples are the reference: black on white is exactly 21, and the greys are the two thresholds
 * people quote — `#767676` is the lightest grey that still passes 4.5 : 1 on white, `#808080` sits just under 4 : 1.
 * If the linearisation breakpoint or the exponent is wrong, these numbers move and the test says so; a palette
 * test alone would not, because it only compares tones with each other.
 */
class ContrastTest {

    private fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

    @Test
    fun `black on white is exactly twenty one`() {
        assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 0.001)
    }

    @Test
    fun `a colour against itself has no contrast`() {
        assertEquals(1.0, Contrast.ratio(0x4682B4, 0x4682B4), 0.0001)
    }

    @Test
    fun `the ratio is symmetric`() {
        val a = 0x1C1B1F
        val b = 0xB39DDB
        assertEquals(Contrast.ratio(a, b), Contrast.ratio(b, a), 0.0000001)
    }

    @Test
    fun `the classic grey thresholds land where WCAG says they do`() {
        // the lightest grey that passes AA for normal text on white
        assertEquals(4.54, Contrast.ratio(0x767676, 0xFFFFFF), 0.01)
        // and the one a shade lighter, which does not
        assertEquals(3.95, Contrast.ratio(0x808080, 0xFFFFFF), 0.01)
    }

    @Test
    fun `luminance runs from zero to one`() {
        assertEquals(0.0, Contrast.relativeLuminance(0x000000), 0.0001)
        assertEquals(1.0, Contrast.relativeLuminance(0xFFFFFF), 0.0001)
        // the sRGB curve is not the raw channel fraction: mid grey is far darker than 0.5
        assertEquals(0.216, Contrast.relativeLuminance(0x808080), 0.001)
    }

    @Test
    fun `the palette's own failing tone is measured, not guessed`() {
        // #FFD54F on the light surface was the worst offender in the pre-M7.5 palette: bright yellow on cream.
        // This is the number that made the light theme's macro tones change, kept as the record of what it was.
        assertTrue(Contrast.ratio(0xFFD54F, 0xFFFBFE) < Contrast.GRAPHIC)
    }

    @Test
    fun `alpha is ignored`() {
        assertEquals(
            Contrast.relativeLuminance(0xFF81C784.toInt()),
            Contrast.relativeLuminance(0x0081C784),
            0.0,
        )
    }

    @Test
    fun `a channel outside a byte is refused`() {
        assertThrows(IllegalArgumentException::class.java) { Contrast.linearize(256) }
        assertThrows(IllegalArgumentException::class.java) { Contrast.linearize(-1) }
    }

    @Test
    fun `a mid green on cream passes the graphic floor`() {
        // the tone the light palette now uses, in the same units the palette test uses
        assertTrue(Contrast.ratio(0x4A7A4E, 0xFFFBFE) >= Contrast.TEXT)
    }
}
