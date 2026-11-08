package com.balancecalories.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * The colours every chart in the app draws with, in one place so two screens cannot disagree about what protein
 * looks like.
 *
 * Two constraints shaped this palette, and both come from how the charts are read rather than from taste:
 *
 * 1. **Lighter shades than a warning colour.** A saturated red next to saturated green reads as "stop" and "go",
 *    and a food log is not a traffic light — a big carbohydrate bar is not a failure. The 300-level tones are
 *    still distinguishable from each other and from the grey track, while staying quiet enough that a full arc
 *    does not look like an error.
 * 2. **Red is not asked to carry the meaning alone.** Protein, fat and carbs are always named in a legend and
 *    always printed as grams, so the colour is decoration on top of text: the chart survives greyscale, and it
 *    survives the ~8% of men who cannot separate the red from the green (ADR-028).
 *
 * These are fixed light-theme tones because the app is light-only today. When the dark theme lands (M7.5) this
 * object becomes the one place that has to know about both, which is why it exists as an object and not as
 * literals at each call site.
 */
object ChartPalette {

    /** Protein: a calm green. */
    val protein = Color(0xFF81C784)

    /** Fat: amber, the warmest of the three, so it separates from protein without shouting. */
    val fat = Color(0xFFFFD54F)

    /** Carbs: a soft red, not a signal red. */
    val carbs = Color(0xFFE57373)

    /** Energy no macro accounts for: alcohol, fibre, label rounding. Deliberately the least interesting colour. */
    val other = Color(0xFFBDBDBD)

    /** Burn, measured. A light violet that belongs to the app's own identity without weighing as much as text. */
    val burn = Color(0xFFB39DDB)

    /** Burn, estimated — the same hue, lighter again, always drawn as an outline. */
    val burnEstimate = Color(0xFFD1C4E9)

    /** The unfilled part of a gauge. */
    @Composable
    fun track(): Color = MaterialTheme.colorScheme.surfaceVariant
}
