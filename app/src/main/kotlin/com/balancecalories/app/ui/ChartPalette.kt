package com.balancecalories.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The six colours every chart in the app draws with, as ARGB ints, one set per theme.
 *
 * Two constraints shaped this palette, and both come from how the charts are read rather than from taste:
 *
 * 1. **Quiet tones, not warning colours.** A saturated red next to saturated green reads as "stop" and "go", and a
 *    food log is not a traffic light — a big carbohydrate bar is not a failure (ADR-029).
 * 2. **Contrast is measured, not assumed.** M7.5 added the dark theme and, while measuring, found that the light
 *    theme's tones sat between **1.4 : 1 and 2.9 : 1** against the surface they were drawn on — below the 3 : 1
 *    WCAG floor for graphical objects, so the fat bar in particular was a colour people could not reliably see.
 *    `ChartPaletteContrastTest` now refuses a tone under 4.5 : 1 in either mode, and `Contrast` in `:core` is the
 *    arithmetic it uses.
 *
 * The two sets are the **same six hues at the lightness each surface demands**: on cream the tones are dark
 * (700-level), on near-black they are light (300-level). Between them sits `LocalChartTones`, so a call site asks
 * `ChartPalette.protein` and never asks which theme it is in.
 *
 * Red still does not carry meaning alone: macros are named in a legend and printed as grams, so the chart survives
 * greyscale and survives the ~8% of men who cannot separate the red from the green (ADR-028).
 */
data class ChartTones(
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val other: Int,
    val burn: Int,
    val burnEstimate: Int,
)

/**
 * On cream (`#FFFBFE`) these are 4.7–6.2 : 1. Darkening the 300-level tones was the whole point: they were calm and
 * unreadable at the same time.
 */
val LightChartTones = ChartTones(
    protein = 0xFF4A7A4E.toInt(),
    fat = 0xFF8D6E00.toInt(),
    carbs = 0xFF9E4242.toInt(),
    other = 0xFF6B6B6B.toInt(),
    burn = 0xFF5B4A8C.toInt(),
    burnEstimate = 0xFF7E57C2.toInt(),
)

/**
 * On near-black (`#1C1B1F`) these are 5.1–12.1 : 1. The light theme's original tones live here, where they were
 * always legible — that is why the light failure took a measurement to find.
 */
val DarkChartTones = ChartTones(
    protein = 0xFF81C784.toInt(),
    fat = 0xFFFFD54F.toInt(),
    carbs = 0xFFE57373.toInt(),
    other = 0xFFBDBDBD.toInt(),
    burn = 0xFFB39DDB.toInt(),
    burnEstimate = 0xFFD1C4E9.toInt(),
)

/** The tones for the theme in force. `BalanceTheme` provides it; nothing else should. */
val LocalChartTones = staticCompositionLocalOf { LightChartTones }

/**
 * The readable surface. Call sites use these properties and stay unaware of the theme: every one of them is a
 * composable, so a `@Composable` getter is enough and the palette cannot be read outside composition by mistake.
 */
object ChartPalette {

    /** Protein. */
    val protein: Color @Composable get() = Color(LocalChartTones.current.protein)

    /** Fat, the warmest of the three, so it separates from protein without shouting. */
    val fat: Color @Composable get() = Color(LocalChartTones.current.fat)

    /** Carbs, a soft brick rather than a signal red. */
    val carbs: Color @Composable get() = Color(LocalChartTones.current.carbs)

    /** Energy no macro accounts for: alcohol, fibre, label rounding. Deliberately the least interesting colour. */
    val other: Color @Composable get() = Color(LocalChartTones.current.other)

    /** Burn, measured. */
    val burn: Color @Composable get() = Color(LocalChartTones.current.burn)

    /** Burn, estimated — the same hue, lighter again, and always drawn as an outline. */
    val burnEstimate: Color @Composable get() = Color(LocalChartTones.current.burnEstimate)

    /** The unfilled part of a gauge. */
    @Composable
    fun track(): Color = MaterialTheme.colorScheme.surfaceVariant
}
