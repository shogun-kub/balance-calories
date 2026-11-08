package com.balancecalories.app.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.balancecalories.app.R
import com.balancecalories.app.ui.ChartPalette
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.CalorieGauge
import com.balancecalories.core.food.DailyIntake
import kotlin.math.roundToInt

/**
 * Today at a glance: a half-doughnut of the day's energy, split by macro, with the grams beside it.
 *
 * The arc is the day's allowance: what is filled is what has been eaten, in the three colours of the macros, and
 * what is left grey is what remains. It is a half circle rather than a full one because the centre then has room
 * for the two numbers a person actually came for — eaten and remaining — without shrinking the arc to a ring.
 *
 * The colour is decoration. Protein, fat and carbs are named and printed in grams underneath (the request was
 * "the actual weight of pure protein, fat and carbs", and grams are the weight), the filled segments are separated
 * by a thin gap so they survive greyscale, and the whole drawing carries a content description in words.
 *
 * Three states are deliberately not "a ring at zero":
 * - nothing logged today → no fill at all, and the centre says so instead of printing 0 kcal;
 * - over target → the arc is full and the centre names the excess, because "142% of target" is harder to feel
 *   than "over by 500 kcal";
 * - no target (no weigh-in yet) → no fill, because a gauge without a denominator would be inventing one.
 */
@Composable
fun MacroGauge(
    intake: DailyIntake,
    targetKcal: Int,

    modifier: Modifier = Modifier,
) {
    val split = intake.macroSplit
    val fill = CalorieGauge.of(intake.eatenKcal, targetKcal.toDouble(), split)
    val trackColor = ChartPalette.track()
    // At a 200% font scale the centre numbers are twice as wide and twice as tall; shrinking the headline and
    // letting the phrases wrap keeps them inside the arc instead of across it.
    val largeType = LocalDensity.current.fontScale >= 1.5f
    val gaugeHeight = if (largeType) 176.dp else 150.dp
    val arcThickness = 22.dp

    val eaten = intake.eatenKcal.roundToInt()
    val remaining = (targetKcal - intake.eatenKcal).roundToInt()
    val centre = when {
        intake.meals.isEmpty() -> stringResource(R.string.gauge_nothing_logged)
        fill.overKcal > 0.5 -> stringResource(R.string.gauge_over, fill.overKcal.roundToInt())
        else -> stringResource(R.string.gauge_remaining, remaining)
    }
    val description = if (intake.meals.isEmpty()) {
        stringResource(R.string.gauge_description_empty, targetKcal)
    } else {
        stringResource(
            R.string.gauge_description,
            eaten,
            targetKcal,
            intake.proteinG.roundToInt(),
            intake.fatG.roundToInt(),
            intake.carbsG.roundToInt(),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(gaugeHeight)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // The inner opening of the arc is the width budget for the numbers: without it the text
            // runs out over the track, which is what happened at a 200% font scale.
            val innerWidth = minOf(maxWidth * 0.86f, gaugeHeight * 1.72f - arcThickness * 2)
                .coerceAtLeast(80.dp)
            Canvas(modifier = Modifier.fillMaxWidth().height(gaugeHeight)) {
                val thickness = arcThickness.toPx()
                // The arc is drawn to the size of the box minus half its thickness, so the stroke stays inside.
                val diameter = minOf(size.width, size.height * 2f) - thickness
                val topLeft = Offset(
                    x = (size.width - diameter) / 2f,
                    y = size.height - diameter / 2f,
                )
                val arcSize = Size(diameter, diameter)
                val stroke = Stroke(width = thickness, cap = StrokeCap.Butt)

                // Track: the whole allowance, drawn first so the fill sits on top of it.
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )

                if (fill.used > 0.0) {
                    val segments = listOf(
                        fill.protein to ChartPalette.protein,
                        fill.fat to ChartPalette.fat,
                        fill.carbs to ChartPalette.carbs,
                        fill.other to ChartPalette.other,
                    ).filter { it.first > 0.0 }

                    // A gap between segments, in degrees, so two adjacent macros never merge into one band.
                    val gapDegrees = if (segments.size > 1) 1.2f else 0f
                    var start = 180f
                    segments.forEachIndexed { index, (fraction, color) ->
                        val sweep = (fraction * 180.0).toFloat()
                        val drawn = if (index == segments.lastIndex) sweep else (sweep - gapDegrees).coerceAtLeast(0.4f)
                        drawArc(
                            color = color,
                            startAngle = start,
                            sweepAngle = drawn,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        start += sweep
                    }
                }
            }

            // The numbers sit inside the half circle, above its baseline, within its own width.
            Column(
                modifier = Modifier
                    .width(innerWidth)
                    .padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // With nothing logged the big number is today's allowance rather than a dash: a dash is
                    // a placeholder, and the one number a person wants before eating is how much they have.
                    // The subtitle names what the number is, so the big figure is never left to inference.
                    text = if (intake.meals.isEmpty()) {
                        stringResource(R.string.kcal_int, targetKcal)
                    } else {
                        stringResource(R.string.kcal_int, eaten)
                    },
                    style = if (largeType) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.headlineLarge
                    },
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (intake.meals.isEmpty()) {
                        stringResource(R.string.gauge_target_today)
                    } else {
                        stringResource(R.string.gauge_of_target, targetKcal)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // At a 200% font scale the phrases are twice as wide and no longer fit the opening, so the
                // remaining/over line moves under the arc instead of being squeezed into it.
                if (!largeType) {
                    Text(
                        text = centre,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // At a 200% font scale the phrases are twice as wide and no longer fit the opening, so the
        // remaining/over line lives under the arc instead of being squeezed inside it.
        if (largeType) {
            Text(
                text = centre,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        // Grams: the "actual weight" of each macro, next to its colour, always visible.
        WrappingRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MacroChip(ChartPalette.protein, R.string.gauge_protein, intake.proteinG)
            MacroChip(ChartPalette.fat, R.string.gauge_fat, intake.fatG)
            MacroChip(ChartPalette.carbs, R.string.gauge_carbs, intake.carbsG)
            if (split.isUnlabelled) {
                Text(
                    text = stringResource(R.string.gauge_unlabelled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

    }
}

@Composable
private fun MacroChip(color: Color, labelRes: Int, grams: Double) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawRect(color = color) }
        Text(
            text = stringResource(labelRes, grams.roundToInt()),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}