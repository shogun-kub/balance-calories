package com.balancecalories.core.energy

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The vertical range of a weight chart.
 *
 * A weight chart's most common lie is the axis. Body weight moves a few hundred grams a day, and a graph that
 * scales to whatever the data happens to be turns "78.4, 78.2, 78.3" into a mountain range on which every normal
 * fluctuation reads as a decision. So the range has a **floor**: it never shows less than [minSpan] of body mass,
 * whatever the data did, and its bounds are rounded out to a readable step.
 *
 * Unit-agnostic on purpose: the caller passes numbers in the unit it is about to draw (kilograms or pounds) with
 * its own floor and step, so the labels land on values a scale can actually display in either system. The same
 * object answers both questions a drawing needs — where the top and bottom are, and where a value sits between
 * them — so the line, the dots and the dashed goal cannot disagree about the scale.
 */
data class WeightAxis(val minValue: Double, val maxValue: Double) {

    init {
        require(minValue.isFinite() && maxValue.isFinite()) { "axis bounds must be finite" }
        require(maxValue > minValue) { "axis needs a positive span, was $minValue..$maxValue" }
    }

    val span: Double get() = maxValue - minValue

    /** Where [value] sits in the range: 0 at the bottom, 1 at the top. Outside values clamp to the edges. */
    fun fraction(value: Double): Float =
        ((value - minValue) / span).coerceIn(0.0, 1.0).toFloat()

    companion object {
        /** The smallest range a weight chart is allowed to show, in kilograms. */
        const val MIN_SPAN_KG = 2.0

        /** Half a kilo, which is the precision a bathroom scale actually has. */
        const val STEP_KG = 0.5

        /** …and a pound, for the other system. */
        const val STEP_LB = 1.0

        /** Breathing room above and below the data, as a share of its span. */
        const val PAD_RATIO = 0.06

        /** …but never less than this, or a flat series touches the frame. */
        const val MIN_PAD = 0.3

        /**
         * The range that covers [values], or null when there is nothing to draw.
         *
         * Non-finite values are dropped rather than poisoning the range: a chart that silently refuses to draw
         * because one row was written as NaN is harder to diagnose than a chart that omits it.
         */
        fun of(
            values: Iterable<Double>,
            minSpan: Double = MIN_SPAN_KG,
            step: Double = STEP_KG,
        ): WeightAxis? {
            val finite = values.filter { it.isFinite() }.toList()
            if (finite.isEmpty()) return null
            require(minSpan > 0.0) { "minSpan must be positive, was $minSpan" }
            require(step > 0.0) { "step must be positive, was $step" }

            var low = finite.min()
            var high = finite.max()

            // Centre a too-narrow range instead of growing it upward: a flat week should look flat, and symmetric
            // padding is the least misleading way to show "nothing happened".
            if (high - low < minSpan) {
                val mid = (low + high) / 2.0
                low = mid - minSpan / 2.0
                high = mid + minSpan / 2.0
            }

            val pad = maxOf(MIN_PAD, (high - low) * PAD_RATIO)
            val from = floor((low - pad) / step) * step
            val to = ceil((high + pad) / step) * step
            return WeightAxis(from, to)
        }
    }
}
