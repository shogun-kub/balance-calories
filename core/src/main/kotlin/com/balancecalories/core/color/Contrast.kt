package com.balancecalories.core.color

import kotlin.math.pow

/**
 * WCAG 2.x contrast, so a colour decision can be a number instead of an opinion.
 *
 * This lives in `:core` because it is arithmetic, and because the charts' palette has to be *checked*, not trusted:
 * the light-theme macro tones that looked calm on a white screen measured 1.4–2.9 : 1, which is below the 3 : 1
 * floor WCAG sets for graphical objects. A chart bar at 1.4 : 1 is a colour a person cannot reliably see.
 *
 * Colours are ARGB ints. Alpha is ignored on purpose — an opacity is how something is *drawn*, and a contrast
 * ratio is a property of the two opaque colours that end up adjacent on screen. Compositing is the caller's job.
 */
object Contrast {

    /** WCAG 1.4.3: normal-sized text. */
    const val TEXT = 4.5

    /** WCAG 1.4.11: graphical objects and UI components — chart bars, arcs, lines, swatches. */
    const val GRAPHIC = 3.0

    /**
     * One 8-bit sRGB channel, linearised.
     *
     * The 0.03928 breakpoint is the WCAG sRGB definition; below it the curve is a straight line, above it a power
     * of 2.4. Getting this wrong (using the raw 0..255 fraction) understates dark colours badly.
     */
    fun linearize(channel: Int): Double {
        require(channel in 0..255) { "channel out of range: $channel" }
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance, 0.0 (black) to 1.0 (white). */
    fun relativeLuminance(argb: Int): Double {
        val r = linearize((argb shr 16) and 0xFF)
        val g = linearize((argb shr 8) and 0xFF)
        val b = linearize(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * The contrast ratio between two colours, from 1.0 (identical) to 21.0 (black on white). Symmetric: which
     * colour is the background does not change the number.
     */
    fun ratio(first: Int, second: Int): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
