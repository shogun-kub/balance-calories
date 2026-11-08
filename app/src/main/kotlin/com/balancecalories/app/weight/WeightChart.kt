package com.balancecalories.app.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balancecalories.app.R
import com.balancecalories.app.food.trimNumber
import com.balancecalories.app.ui.LegendSwatch
import com.balancecalories.app.ui.SwatchShape
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.energy.WeightAxis
import com.balancecalories.core.profile.UnitSystem
import com.balancecalories.core.profile.Units
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * The weight chart: what the scale said, and the line through it.
 *
 * **Both, always.** The dots are the weigh-ins — noise, water, yesterday's salt — and the line is the trend.
 * Drawing only the line would imply the scale is smoother than it is; drawing only the dots leaves the person
 * doing the smoothing in their head, which is how a 0.4 kg fluctuation becomes a decision to eat less.
 *
 * The vertical range comes from [WeightAxis], which refuses to show less than two kilograms, so a quiet week
 * looks quiet. The goal is dashed because it is a reference the person chose, not a measurement.
 */
@Composable
fun WeightChart(
    points: List<Pair<LocalDate, Double>>,
    trend: List<Pair<LocalDate, Double>>,
    goalKg: Double?,
    today: LocalDate,
    rangeDays: Int,
    onRangeChange: (Int) -> Unit,
    system: UnitSystem,
    unitLabel: String,
    modifier: Modifier = Modifier,
) {
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontSize = 11.sp) }
    val dateLabel = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    // Drawn in the unit the person thinks in, so the gridlines land on numbers a scale can show: half kilos in
    // metric and whole pounds in imperial, rather than 172.6 / 173.9.
    val imperial = system == UnitSystem.IMPERIAL
    val minSpan = if (imperial) Units.weightFromKg(WeightAxis.MIN_SPAN_KG, system) else WeightAxis.MIN_SPAN_KG
    val step = if (imperial) WeightAxis.STEP_LB else WeightAxis.STEP_KG
    fun display(kg: Double): Double = Units.weightFromKg(kg, system)

    val shownPoints = remember(points, system) { points.map { it.first to display(it.second) } }
    val shownTrend = remember(trend, system) { trend.map { it.first to display(it.second) } }
    val shownGoal = goalKg?.let { display(it) }

    val axis = remember(shownPoints, shownTrend, minSpan, step) {
        // The data drives the range, not the goal. A goal three kilos below a five-kilo window would otherwise
        // stretch the axis until the fortnight the person is actually looking at is a band a few pixels tall —
        // and the slope of that band is the whole question the chart exists to answer.
        WeightAxis.of(
            shownPoints.map { it.second } + shownTrend.map { it.second },
            minSpan = minSpan,
            step = step,
        )
    }

    // Where the goal sits relative to what is drawn: 0 inside, -1 below, +1 above, null when there is no goal.
    val goalSide = shownGoal?.let { goal ->
        when {
            axis == null -> 0
            goal < axis.minValue -> -1
            goal > axis.maxValue -> 1
            else -> 0
        }
    }

    val firstDay = today.minusDays((rangeDays - 1).toLong())
    val label = { value: Double -> trimNumber(value) + " " + unitLabel }
    val description = pluralStringResource(
        R.plurals.weight_chart_description,
        rangeDays,
        points.size,
        rangeDays,
        points.firstOrNull()?.let { label(display(it.second)) } ?: "-",
        points.lastOrNull()?.let { label(display(it.second)) } ?: "-",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in listOf(30, 90, 365)) {
                FilterChip(
                    selected = rangeDays == option,
                    onClick = { onRangeChange(option) },
                    label = { Text(stringResource(R.string.chart_range_days, option)) },
                )
            }
        }

        if (axis == null) {
            Text(
                text = stringResource(R.string.weight_none),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .semantics { contentDescription = description },
            ) {
                // Gutters are measured, not assumed: at a 200% font scale "80 кг" is twice as wide, and a fixed
                // gutter would have the axis numbers overlap the data (the ADR-026 failure in a new place).
                val maxGlyphs = textMeasurer.measure(label(axis.maxValue), labelStyle)
                val minGlyphs = textMeasurer.measure(label(axis.minValue), labelStyle)
                val firstDateGlyphs = textMeasurer.measure(dateLabel.format(firstDay), labelStyle)
                val lastDateGlyphs = textMeasurer.measure(dateLabel.format(today), labelStyle)
                val axisLabelWidth = maxOf(maxGlyphs.size.width, minGlyphs.size.width)
                val leftGutter = axisLabelWidth + 10.dp.toPx()
                val rightInset = 6.dp.toPx()
                val topInset = maxOf(8.dp.toPx(), maxGlyphs.size.height / 2f)
                val bottomGutter = maxOf(firstDateGlyphs.size.height, lastDateGlyphs.size.height) + 4.dp.toPx()
                val plotWidth = size.width - leftGutter - rightInset
                val plotHeight = size.height - topInset - bottomGutter
                if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

                fun x(date: LocalDate): Float {
                    val days = ChronoUnit.DAYS.between(firstDay, date).toFloat()
                    val span = (rangeDays - 1).coerceAtLeast(1).toFloat()
                    return leftGutter + (days / span).coerceIn(0f, 1f) * plotWidth
                }

                fun y(value: Double): Float = topInset + (1f - axis.fraction(value)) * plotHeight

                // Two hairlines and their numbers: enough to read a level, few enough to leave the data alone.
                for ((value, glyphs) in listOf(axis.minValue to minGlyphs, axis.maxValue to maxGlyphs)) {
                    val lineY = y(value)
                    drawLine(
                        color = axisColor.copy(alpha = 0.5f),
                        start = Offset(leftGutter, lineY),
                        end = Offset(size.width - rightInset, lineY),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawText(
                        textLayoutResult = glyphs,
                        color = labelColor,
                        topLeft = Offset(
                            x = leftGutter - glyphs.size.width - 6.dp.toPx(),
                            y = (lineY - glyphs.size.height / 2f).coerceAtLeast(0f),
                        ),
                    )
                }

                // The goal, dashed and behind the data: a reference line, not an observation. Drawn only when it
                // falls inside the range the data asked for; the legend says where it went when it does not.
                if (shownGoal != null && goalSide == 0) {
                    val goalY = y(shownGoal)
                    drawLine(
                        color = goalColor,
                        start = Offset(leftGutter, goalY),
                        end = Offset(size.width - rightInset, goalY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    )
                }

                // The trend through the days the person actually weighed in.
                if (shownTrend.size >= 2) {
                    val path = Path()
                    shownTrend.forEachIndexed { index, (date, value) ->
                        val point = Offset(x(date), y(value))
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                // The weigh-ins themselves, on top, because they are the evidence.
                val radius = if (shownPoints.size > 120) 1.8.dp.toPx() else 2.5.dp.toPx()
                for ((date, value) in shownPoints) {
                    drawCircle(color = dotColor, radius = radius, center = Offset(x(date), y(value)))
                }

                // Dates at both ends of the window, so the range chips and the axis cannot disagree.
                val firstLabel = textMeasurer.measure(dateLabel.format(firstDay), labelStyle)
                drawText(
                    textLayoutResult = firstLabel,
                    color = labelColor,
                    topLeft = Offset(leftGutter, size.height - bottomGutter + 2.dp.toPx()),
                )
                val lastLabel = textMeasurer.measure(dateLabel.format(today), labelStyle)
                drawText(
                    textLayoutResult = lastLabel,
                    color = labelColor,
                    topLeft = Offset(
                        x = size.width - rightInset - lastLabel.size.width,
                        y = size.height - bottomGutter + 2.dp.toPx(),
                    ),
                )
            }

            WrappingRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendSwatch(color = dotColor, shape = SwatchShape.Dot) {
                    stringResource(R.string.weight_chart_legend_points)
                }
                // A swatch promises something is drawn on the canvas: with one weigh-in there is no line yet, and
                // the sentence below says why instead of the legend implying otherwise.
                if (shownTrend.size >= 2) {
                    LegendSwatch(color = lineColor, shape = SwatchShape.Line) {
                        stringResource(R.string.weight_chart_legend_trend)
                    }
                }
                if (shownGoal != null) {
                    LegendSwatch(
                        color = goalColor,
                        shape = if (goalSide == 0) SwatchShape.Dashed else SwatchShape.None,
                    ) {
                        when (goalSide) {
                            -1 -> stringResource(R.string.weight_chart_goal_below, label(shownGoal))
                            1 -> stringResource(R.string.weight_chart_goal_above, label(shownGoal))
                            else -> stringResource(R.string.weight_chart_legend_goal, label(shownGoal))
                        }
                    }
                }
            }

            if (points.size < 2) {
                Text(
                    text = stringResource(R.string.history_trend_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                )
            }
        }
    }
}
