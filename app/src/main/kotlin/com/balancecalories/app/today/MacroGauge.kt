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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    // Read before the Canvas: a DrawScope lambda is not a composable, and the palette is theme-aware now, so
    // the tones have to be resolved here and handed in rather than looked up while drawing.
    val proteinColor = ChartPalette.protein
    val fatColor = ChartPalette.fat
    val carbsColor = ChartPalette.carbs
    val otherColor = ChartPalette.other
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
                val stroke = Stroke(width = thickness, cap = StrokeCap.Round)

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
                        fill.protein to proteinColor,
                        fill.fat to fatColor,
                        fill.carbs to carbsColor,
                        fill.other to otherColor,
                    ).filter { it.first > 0.0 }

                    // Sharp joins, rounded only at the two ends of the coloured part (owner's note, 2026-11-12).
                    // The segments are butt-capped and drawn back to back, so green meets yellow meets red with
                    // no hairline between them; the outer ends are then rounded by a half-thickness disc in the
                    // first and in the last segment's own colour, which is precisely what a round cap is - just
                    // without giving every internal edge one.
                    val radius = diameter / 2f
                    var start = 180f
                    segments.forEach { (fraction, color) ->
                        val sweep = (fraction * 180.0).toFloat()
                        drawArc(
                            color = color,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = thickness, cap = StrokeCap.Butt),
                        )
                        start += sweep
                    }

                    // Angles run clockwise from three o'clock, so the point on the arc is centre + radius at that
                    // angle; the disc is centred on the stroke's midline, which is where a cap sits.
                    val centre = Offset(topLeft.x + radius, topLeft.y + radius)
                    fun pointOnArc(angle: Float): Offset {
                        val radians = Math.toRadians(angle.toDouble())
                        return Offset(
                            x = centre.x + radius * cos(radians).toFloat(),
                            y = centre.y + radius * sin(radians).toFloat(),
                        )
                    }
                    val capRadius = thickness / 2f
                    drawCircle(color = segments.first().second, radius = capRadius, center = pointOnArc(180f))
                    drawCircle(color = segments.last().second, radius = capRadius, center = pointOnArc(start))

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
            }
        }

        // One summary line under the arc rather than a third line inside it. "осталось 306 ккал" is the
        // answer to the day's question, so it gets its own line instead of the smallest type on the screen;
        // and it is absent with nothing logged, because the big number already reads "цель на сегодня" and
        // the section title below says the same thing in words.
        if (intake.meals.isNotEmpty()) {
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
            MacroChip(proteinColor, R.string.gauge_protein, intake.proteinG)
            MacroChip(fatColor, R.string.gauge_fat, intake.fatG)
            MacroChip(carbsColor, R.string.gauge_carbs, intake.carbsG)
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
        // A round dot rather than a square: the arc it explains is round, and a square swatch beside a rounded
        // button row read as a different visual language (owner feedback, 2026-11-12).
        Canvas(modifier = Modifier.size(9.dp)) { drawCircle(color = color) }
        Text(
            text = stringResource(labelRes, grams.roundToInt()),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}