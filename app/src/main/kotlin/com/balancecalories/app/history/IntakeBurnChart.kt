package com.balancecalories.app.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balancecalories.app.R
import com.balancecalories.app.ui.ChartPalette
import com.balancecalories.app.ui.LegendSwatch
import com.balancecalories.app.ui.SwatchShape
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.data.repo.DaySummary
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Eaten against burned, one group of bars per day, on one baseline and one scale.
 *
 * One scale is the whole point: a chart with two axes cannot answer "did I eat less than I burned", which is the
 * only question this picture exists to answer. The target is drawn as a dashed line across the same scale, so the
 * number can be seen rather than inferred.
 *
 * Two facts are kept visible rather than smoothed away:
 *
 * - a day with **no food log draws no eaten bar**. A zero-height bar would say "ate nothing", which is a claim
 *   about a person; a gap says "nothing recorded", which is the truth (hard rule 4);
 * - a day whose burn is a **formula estimate** draws the burn bar with a thinner, lighter fill, and the legend
 *   says which is which. An estimated burn is still a number the app shows on Today — it just must not look like
 *   a measured one here.
 *
 * Colour is decoration; the meaning lives in the legend, the segment dividers and the content description, so the
 * chart survives greyscale and red-green colour blindness (ADR-028).
 */
@Composable
fun IntakeBurnChart(
    days: List<DaySummary>,
    targetKcal: Int,
    rangeDays: Int,
    onRangeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The lighter palette shared with the Today gauge, so the two charts cannot disagree about what
    // protein looks like. app/ui/ChartPalette.kt explains why the tones are quiet.
    val proteinColor = ChartPalette.protein
    val fatColor = ChartPalette.fat
    val carbsColor = ChartPalette.carbs
    val otherColor = ChartPalette.other
    val burnColor = ChartPalette.burn
    val estimateColor = ChartPalette.burnEstimate
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.surface

    val textMeasurer = rememberTextMeasurer()
    val dayLabel = remember { DateTimeFormatter.ofPattern("d") }
    val weekLabel = remember { DateTimeFormatter.ofPattern("d.MM") }
    val monthDay = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    // Thirty and ninety days draw one bar per WEEK (see ChartBuckets): at 16 dp per day a bar was five
    // pixels wide, and a chart nobody can read is not a chart. Seven days stay per-day, because a week of
    // days is what a person actually wants to compare.
    val chartDays = remember(days, rangeDays) { bucketForChart(days, rangeDays) }
    val aggregated = chartDays.size != days.size
    val targetScale = if (aggregated) WEEK_DAYS else 1
    val barTarget = targetKcal * targetScale
    // Bucketed bars are few, so they never need to scroll: 90 days is thirteen of them.
    val scrollable = false

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (aggregated) {
                stringResource(R.string.chart_title_weeks)
            } else {
                stringResource(R.string.chart_title)
            },
            style = MaterialTheme.typography.titleSmall,
        )

        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in listOf(7, 30, 90)) {
                FilterChip(
                    selected = rangeDays == option,
                    onClick = { onRangeChange(option) },
                    label = {
                        Text(stringResource(R.string.chart_range_days, option))
                    },
                )
            }
        }

        val loggedDays = days.count { it.logged }
        val eatenTotal = days.sumOf { it.intakeKcal }.roundToInt()
        // An average, not a sum: thirty days of estimated burns add up to a number in the hundreds of thousands
        // that reads like a measurement of something. Per day it is comparable to the bars on the chart.
        val burnDays = days.filter { it.burnKcal > 0.0 }
        val burnedAverage = if (burnDays.isEmpty()) 0 else burnDays.sumOf { it.burnKcal }.div(burnDays.size).roundToInt()
        val estimatedBurns = days.count { it.burnKcal > 0.0 && !it.burnIsMeasured }
        val chartDescription = stringResource(
            R.string.chart_summary,
            loggedDays,
            days.size,
            eatenTotal,
            burnedAverage,
            estimatedBurns,
        )

        val slotWidth = 16.dp
        val chartHeight = 180.dp
        // fillMaxWidth inside a horizontal scroll resolves against an unbounded width and collapses to
        // zero, so the scrollable canvas is given its real width explicitly.
        val chartWidth = if (scrollable) slotWidth * days.size.coerceAtLeast(1) else null
        val content = @Composable {
            ChartCanvas(
                days = chartDays,
                targetKcal = barTarget,
                proteinColor = proteinColor,
                fatColor = fatColor,
                carbsColor = carbsColor,
                otherColor = otherColor,
                burnColor = burnColor,
                estimateColor = estimateColor,
                axisColor = axisColor,
                labelColor = labelColor,
                dividerColor = dividerColor,
                textMeasurer = textMeasurer,
                dayLabel = if (aggregated) weekLabel else dayLabel,
                perDayWidth = if (scrollable) slotWidth else null,
                modifier = (if (chartWidth != null) {
                    Modifier.width(chartWidth).height(chartHeight)
                } else {
                    Modifier.fillMaxWidth().height(chartHeight)
                }).semantics { contentDescription = chartDescription },
            )
        }

        if (scrollable) {
            // Opened at the newest day: ninety days of history is read from the right, and a chart that opens on
            // the oldest bar makes a person scroll before they can see their own week. An initial value cannot do
            // this — the maximum scroll is only known after layout — so it is scrolled once it is.
            val scrollState = rememberScrollState()
            LaunchedEffect(scrollState.maxValue) {
                if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
            }
            Box(modifier = Modifier.horizontalScroll(scrollState)) { content() }
        } else {
            content()
        }

        MacroLegend(days = days, proteinColor = proteinColor, fatColor = fatColor, carbsColor = carbsColor, otherColor = otherColor)

        // Wrapping, not a plain Row: at a 200% font scale three swatches do not fit, and the squeezed
        // third one broke its label inside the words -- "Цель 1904 ккал" came out as a vertical column
        // of letters (ADR-026).
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendSwatch(color = burnColor) { stringResource(R.string.chart_legend_burn) }
            LegendSwatch(color = estimateColor) { stringResource(R.string.chart_legend_burn_estimate) }
            LegendSwatch(color = axisColor, shape = SwatchShape.Dashed) {
                if (aggregated) {
                    stringResource(R.string.chart_legend_target_week, targetKcal)
                } else {
                    stringResource(R.string.chart_legend_target, targetKcal)
                }
            }
        }

        // The same sentence a screen reader gets, printed for everyone: the chart should be readable without
        // decoding colours, and this is the count of days that actually have a log.
        Text(
            text = chartDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The last day is the one a person looks for first, so it gets the date spelled out.
        days.lastOrNull { it.logged }?.let { day ->
            Text(
                text = stringResource(
                    R.string.chart_last_logged,
                    monthDay.format(day.date),
                    day.intakeKcal.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartCanvas(
    days: List<DaySummary>,
    targetKcal: Int,
    proteinColor: Color,
    fatColor: Color,
    carbsColor: Color,
    otherColor: Color,
    burnColor: Color,
    estimateColor: Color,
    axisColor: Color,
    labelColor: Color,
    dividerColor: Color,
    textMeasurer: TextMeasurer,
    dayLabel: DateTimeFormatter,
    perDayWidth: androidx.compose.ui.unit.Dp?,
    modifier: Modifier,
) {
    val dayCount = days.size.coerceAtLeast(1)
    Canvas(modifier = modifier) {
        val labelStrip = 18.dp.toPx()
        val plotHeight = size.height - labelStrip
        val slot = perDayWidth?.toPx() ?: (size.width / dayCount)
        val fullWidth = if (perDayWidth != null) slot * dayCount else size.width

        // One scale for both quantities, and the target has to fit in it too or the line would sit off-chart.
        val peak = maxOf(
            days.maxOfOrNull { if (it.logged) it.intakeKcal else 0.0 } ?: 0.0,
            days.maxOfOrNull { it.burnKcal } ?: 0.0,
            targetKcal.toDouble(),
        ).coerceAtLeast(1.0) * 1.1
        fun y(kcal: Double): Float = (plotHeight - (kcal / peak * plotHeight)).toFloat()

        // Axis: one hairline at the baseline, no grid — the bars are the grid.
        drawLine(
            color = axisColor,
            start = Offset(0f, plotHeight),
            end = Offset(fullWidth, plotHeight),
            strokeWidth = 1.dp.toPx(),
        )

        // Target line, dashed: the number in force today, drawn across the days it is being compared to.
        if (targetKcal > 0) {
            drawLine(
                color = axisColor,
                start = Offset(0f, y(targetKcal.toDouble())),
                end = Offset(fullWidth, y(targetKcal.toDouble())),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
        }

        val barWidth = (slot * 0.42f).coerceAtLeast(2f)
        val gap = slot * 0.04f

        fun centreOf(index: Int): Float =
            if (perDayWidth != null) index * slot + slot / 2f else slot * index + slot / 2f

        // Bars stand on the axis: they are drawn as capsules reaching half a width below the baseline, and
        // the clip takes that half away, so the top is round and the foot is square. The owner asked for
        // exactly this (2026-11-12): round everywhere made the bars look like floating pills.
        val foot = barWidth / 2f
        clipRect(top = 0f, bottom = plotHeight) {
            days.forEachIndexed { index, day ->
                val groupCenter = centreOf(index)
                val eatenLeft = groupCenter - barWidth - gap / 2f
                val burnLeft = groupCenter + gap / 2f

                if (day.logged && day.intakeKcal > 0.0) {
                    drawStackedBar(
                        left = eatenLeft,
                        barWidth = barWidth,
                        bottom = plotHeight + foot,
                        split = day.macroSplit,
                        proteinColor = proteinColor,
                        fatColor = fatColor,
                        carbsColor = carbsColor,
                        otherColor = otherColor,
                        dividerColor = dividerColor,
                        scale = plotHeight / peak.toFloat(),
                    )
                }

                if (day.burnKcal > 0.0) {
                    val top = y(day.burnKcal)
                    val height = (plotHeight + foot - top).coerceAtLeast(1f)
                    val corners = CornerRadius(foot, foot)
                    if (day.burnIsMeasured) {
                        drawRoundRect(
                            color = burnColor,
                            topLeft = Offset(burnLeft, top),
                            size = Size(barWidth, height),
                            cornerRadius = corners,
                        )
                    } else {
                        // An estimate is an outline: a fence of solid bars for days nobody measured
                        // would read as thirty measurements, and the eye should go to the solid ones.
                        drawRoundRect(
                            color = estimateColor,
                            topLeft = Offset(burnLeft, top),
                            size = Size(barWidth, height),
                            cornerRadius = corners,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
        }

        // Day numbers sit under the axis, outside the clip that squares the feet.
        val stride = when {
            days.size <= 13 -> 1
            days.size <= 31 -> 3
            else -> 10
        }
        days.forEachIndexed { index, day ->
            if (index % stride == 0 || index == days.lastIndex) {
                val text = dayLabel.format(day.date)
                val laid = textMeasurer.measure(
                    text = text,
                    style = TextStyle(fontSize = 10.sp, color = labelColor),
                )
                drawText(
                    textLayoutResult = laid,
                    topLeft = Offset(centreOf(index) - laid.size.width / 2f, plotHeight + 3.dp.toPx()),
                )
            }
        }
    }
}

/** One eaten bar: protein, fat, carbs, other, stacked from the baseline up, split by a hairline. */
private fun DrawScope.drawStackedBar(
    left: Float,
    barWidth: Float,
    bottom: Float,
    split: com.balancecalories.core.food.MacroSplit,
    proteinColor: Color,
    fatColor: Color,
    carbsColor: Color,
    otherColor: Color,
    dividerColor: Color,
    scale: Float,
) {
    val segments = listOf(
        split.proteinKcal to proteinColor,
        split.fatKcal to fatColor,
        split.carbsKcal to carbsColor,
        split.otherKcal to otherColor,
    )
    val totalHeight = (segments.sumOf { it.first } * scale).toFloat()
    if (totalHeight <= 0f) return

    // A capsule; the caller's clip squares its foot so the bar stands on the axis. Clipping here keeps the
    // segments themselves rectangular, so the internal dividers stay straight.
    val barPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = bottom - totalHeight,
                right = left + barWidth,
                bottom = bottom,
                radiusX = barWidth / 2f,
                radiusY = barWidth / 2f,
            ),
        )
    }

    clipPath(barPath) {
        var cursor = bottom
        segments.forEachIndexed { index, (kcal, color) ->
            if (kcal <= 0.0) return@forEachIndexed
            val segmentHeight = (kcal * scale).toFloat()
            val top = cursor - segmentHeight
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, segmentHeight),
            )
            // A 1 dp gap between segments so they stay distinguishable in greyscale and at a glance.
            if (index < segments.lastIndex && segments.drop(index + 1).any { it.first > 0.0 }) {
                drawLine(
                    color = dividerColor,
                    start = Offset(left, top),
                    end = Offset(left + barWidth, top),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            cursor = top
        }
    }
}

/**
 * The legend, always visible, carrying grams so the colours are never the only channel.
 *
 * It reads today's totals rather than one day's because the question "what did this week's bars consist of" is
 * the one a legend answers; the per-day numbers are in the list below the chart.
 */
@Composable
private fun MacroLegend(
    days: List<DaySummary>,
    proteinColor: Color,
    fatColor: Color,
    carbsColor: Color,
    otherColor: Color,
) {
    val logged = days.filter { it.logged }
    if (logged.isEmpty()) {
        Text(
            text = stringResource(R.string.chart_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Shares of energy, not grams. Grams summed over thirty days are a number nobody can act on
    // ("28.8 g of protein" says nothing without the number of days), while a share answers the same
    // question for any range -- and explaining the colours is exactly what the legend is for.
    val protein = logged.sumOf { it.macroSplit.proteinKcal }
    val fat = logged.sumOf { it.macroSplit.fatKcal }
    val carbs = logged.sumOf { it.macroSplit.carbsKcal }
    val other = logged.sumOf { it.macroSplit.otherKcal }
    val total = protein + fat + carbs + other
    fun share(kcal: Double): Long = if (total > 0.0) (kcal / total * 100).roundToInt().toLong() else 0L

    // A couple of percent of unlabelled energy is a rounding, not a category: it earns a legend
    // entry only when it is big enough to be visible in the bar.
    val hasOther = total > 0.0 && other / total > 0.02

    WrappingRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendSwatch(color = proteinColor) {
            stringResource(R.string.chart_legend_protein, share(protein))
        }
        LegendSwatch(color = fatColor) {
            stringResource(R.string.chart_legend_fat, share(fat))
        }
        LegendSwatch(color = carbsColor) {
            stringResource(R.string.chart_legend_carbs, share(carbs))
        }
        if (hasOther) {
            LegendSwatch(color = otherColor) { stringResource(R.string.chart_legend_other, share(other)) }
        }
    }
}
