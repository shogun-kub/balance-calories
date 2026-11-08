package com.balancecalories.app.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.food.trimNumber
import com.balancecalories.data.repo.AdaptiveTdeeRepository
import com.balancecalories.core.energy.AdaptiveTdee
import com.balancecalories.core.energy.TdeeConfidence
import com.balancecalories.core.energy.Projection
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.energy.WeeklyRecap
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.time.DayWindow
import com.balancecalories.data.repo.DaySummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * History: what the last days actually were, and the one number that can replace the formula.
 *
 * The screen exists to answer "is this working?" without asking the person to remember. Two blocks:
 *
 * 1. **Measured expenditure** — the adaptive TDEE. It is offered, explained, and only applied when the evidence
 *    clears medium confidence, because a target that moves on four logged days is a rumour with a button.
 * 2. **Recent days** — eaten, burned, balance and weight per day, with the burn's origin marked.
 */
@Composable
fun HistoryScreen(
    profile: Profile,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val hour = profile.dayStartHour
    val today = remember(profile.id) {
        DayWindow.dateOf(Instant.now(), zone, hour)
    }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    var days by remember(profile.id) { mutableStateOf<List<DaySummary>>(emptyList()) }
    var target by remember(profile.id) { mutableStateOf<Int?>(null) }
    var tdee by remember(profile.id) { mutableStateOf<AdaptiveTdeeRepository.Outcome?>(null) }
    var appliedTarget by remember(profile.id) { mutableStateOf<Int?>(null) }
    var recap by remember(profile.id) { mutableStateOf<WeeklyRecap?>(null) }
    var trend by remember(profile.id) { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(profile.id, refresh) {
        val weight = container.body.latestWeightKg(profile.id)
        if (weight == null) return@LaunchedEffect
        val current = TargetCalculator.seed(
            profile = profile.toEnergyProfile(weight),
            goalRateKgPerWeek = profile.goalRateKgPerWeek,
            on = today,
        )
        target = current.kcal
        days = container.history.summaries(
            profileId = profile.id,
            today = today,
            days = 30,
            bmrKcal = current.bmrKcal,
            palKcal = current.tdeeKcal,
        )
        tdee = container.adaptiveTdee.outcome(
            profile = profile,
            bmrKcal = current.bmrKcal,
            currentTargetKcal = current.kcal,
            zone = zone,
        )
        recap = container.history.recap(profile.id, today, days = 7, current.bmrKcal, current.tdeeKcal)
        trend = container.history.weightTrend(profile.id, today, days = 30)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Text(text = stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)

        val outcome = tdee
        if (outcome != null) {
            AdaptiveCard(
                outcome = outcome,
                appliedTarget = appliedTarget,
                onApply = {
                    scope.launch {
                        val on = today
                        val ok = container.adaptiveTdee.apply(outcome, profile.id, on)
                        if (ok) appliedTarget = outcome.proposedTargetKcal
                    }
                },
            )
        }

        recap?.let { RecapBlock(recap = it, trend = trend, profile = profile, today = today) }

        HorizontalDivider()
        Text(text = stringResource(R.string.history_week), style = MaterialTheme.typography.titleSmall)
        WeekBars(days = days.take(7).reversed())

        HorizontalDivider()
        Text(text = stringResource(R.string.history_recent), style = MaterialTheme.typography.titleSmall)
        if (days.isEmpty()) {
            Text(text = stringResource(R.string.history_no_days), style = MaterialTheme.typography.bodySmall)
        }
        for (day in days) {
            DayRow(day = day, label = dateFormatter.format(day.date))
        }
    }
}

@Composable
private fun AdaptiveCard(
    outcome: AdaptiveTdeeRepository.Outcome,
    appliedTarget: Int?,
    onApply: () -> Unit,
) {
    val estimate = outcome.estimate
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.history_adaptive), style = MaterialTheme.typography.titleMedium)

        val tdeeKcal = estimate.tdeeKcal
        if (tdeeKcal == null) {
            // Say what is missing, not just "not enough data": the person can act on a specific number.
            val refusal = estimate.refusal
            Text(
                text = if (refusal == AdaptiveTdee.Refusal.NOT_ENOUGH_LOGGED_DAYS ||
                    refusal == AdaptiveTdee.Refusal.NOT_ENOUGH_DAYS
                ) {
                    stringResource(
                        R.string.history_need_more,
                        AdaptiveTdee.MIN_LOGGED_DAYS,
                        estimate.loggedDays,
                    )
                } else {
                    stringResource(
                        R.string.history_need_more,
                        AdaptiveTdee.MIN_WEIGHT_POINTS,
                        estimate.weighIns,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        Text(
            text = stringResource(R.string.kcal_int, tdeeKcal.roundToInt()),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.history_confidence, estimate.confidence.localized()),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.history_adaptive_detail,
                stringResource(R.string.kcal_decimal, estimate.averageIntakeKcal ?: 0.0),
                signedKg(estimate.weightChangeKg ?: 0.0),
                outcome.days.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )

        val applied = appliedTarget
        when {
            applied != null -> Text(
                text = stringResource(R.string.history_target_applied, applied),
                style = MaterialTheme.typography.bodyMedium,
            )
            outcome.changesTarget -> OutlinedButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.history_apply_target, outcome.proposedTargetKcal))
            }
            else -> Text(
                text = stringResource(R.string.history_target_applied, outcome.currentTargetKcal),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Seven bars, one per day, above and below a baseline: negative is a deficit, which is the direction the plan
 * wants. A hand-drawn row of boxes instead of a chart library — one picture is not worth a dependency, and this
 * keeps the numbers in the accessibility tree as text rather than pixels.
 */
@Composable
private fun WeekBars(days: List<DaySummary>) {
    val logged = days.filter { it.logged }
    if (logged.isEmpty()) {
        Text(text = stringResource(R.string.history_week_empty), style = MaterialTheme.typography.bodySmall)
        return
    }
    val maxAbs = logged.maxOf { kotlin.math.abs(it.balanceKcal) }.coerceAtLeast(1.0)
    val barColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (day in days) {
            val fraction = if (day.logged) (kotlin.math.abs(day.balanceKcal) / maxAbs) else 0.0
            val height = (fraction * 80.0).roundToInt().coerceAtLeast(if (day.logged) 2 else 0)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(height.dp)
                        .background(if (day.logged) barColor else Color.Transparent),
                )
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DayRow(day: DaySummary, label: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when {
                    !day.logged -> ""
                    day.balanceKcal < 0 ->
                        "-" + stringResource(R.string.kcal_int, kotlin.math.abs(day.balanceKcal).roundToInt())
                    else -> stringResource(
                        R.string.history_surplus,
                        stringResource(R.string.kcal_int, day.balanceKcal.roundToInt()),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val intake = stringResource(
            R.string.history_day_intake,
            stringResource(R.string.kcal_int, day.intakeKcal.roundToInt()),
        )
        val burned = stringResource(
            R.string.history_day_burn,
            stringResource(R.string.kcal_int, day.burnKcal.roundToInt()),
        )
        val estimateMark = if (day.burnIsMeasured) "" else " (" + stringResource(R.string.history_burn_estimate) + ")"
        Text(
            // A day with no food log is not a 2405 kcal deficit; it is a day nobody recorded. Saying "eaten 0"
            // would turn a missing entry into a claim about the person's eating.
            text = if (day.logged) {
                intake + " · " + burned + estimateMark
            } else {
                stringResource(R.string.history_no_entries)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        val weight = day.weightKg
        if (weight != null) {
            Text(
                text = stringResource(R.string.history_weight, trimNumber(weight)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TdeeConfidence.localized(): String = when (this) {
    TdeeConfidence.NONE -> stringResource(R.string.history_confidence_none)
    TdeeConfidence.LOW -> stringResource(R.string.history_confidence_low)
    TdeeConfidence.MEDIUM -> stringResource(R.string.history_confidence_medium)
    TdeeConfidence.HIGH -> stringResource(R.string.history_confidence_high)
}

/**
 * Weight change with an explicit sign: "-0.5 kg" reads as loss, "0.5 kg" would be ambiguous.
 *
 * The unit comes from a resource, like every other user-visible string — "kg" is not universal even though the
 * stored value always is.
 */
@Composable
private fun signedKg(value: Double): String {
    val unit = stringResource(R.string.weight_kg, trimNumber(kotlin.math.abs(value)))
    return when {
        value > 0.0 -> "+$unit"
        value < 0.0 -> "-$unit"
        else -> unit
    }
}

/**
 * Predicted against actual, plus the projection.
 *
 * The two numbers are shown side by side because they are the app's own honesty check: if the log implies a
 * kilogram and the scale did not move, either the portions are optimistic or the expenditure is lower than
 * assumed, and the adaptive TDEE is the mechanism that fixes the second case.
 */
@Composable
private fun RecapBlock(
    recap: WeeklyRecap,
    trend: List<Pair<LocalDate, Double>>,
    profile: Profile,
    today: LocalDate,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.history_recap_title), style = MaterialTheme.typography.titleMedium)

        val predicted = recap.predictedChangeKg
        val actual = recap.actualChangeKg
        when {
            recap.loggedDays == 0 -> Text(
                text = stringResource(R.string.history_recap_none),
                style = MaterialTheme.typography.bodySmall,
            )
            predicted != null -> Text(
                text = stringResource(R.string.history_recap_predicted, signedKg(predicted)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (actual != null) {
            Text(
                text = stringResource(R.string.history_recap_actual, signedKg(actual)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (recap.streakDays > 1) {
            Text(
                text = stringResource(R.string.history_recap_streak, recap.streakDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        HorizontalDivider()
        Text(text = stringResource(R.string.history_trend), style = MaterialTheme.typography.titleSmall)
        if (trend.size < 2) {
            Text(text = stringResource(R.string.history_trend_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            WeightTrendLine(trend = trend)
        }

        // A projection assumes the rate holds, so it is stated with its assumption and its date.
        val goalKg = profile.goalWeightKg
        val latestWeight = trend.lastOrNull()?.second
        if (goalKg != null && latestWeight != null) {
            val days = Projection.daysToGoal(latestWeight, goalKg, profile.goalRateKgPerWeek)
            if (days != null) {
                Text(
                    text = stringResource(
                        R.string.history_projection,
                        trimNumber(goalKg),
                        days,
                        formatter.format(Projection.dateToGoal(today, days)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.history_projection_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** The trend as a line: one polyline, drawn from the smoothed values, with the last value called out. */
@Composable
private fun WeightTrendLine(trend: List<Pair<LocalDate, Double>>) {
    val weights = trend.map { it.second }
    val min = weights.min()
    val max = weights.max()
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    val lineColor = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val stepX = size.width / (weights.size - 1).coerceAtLeast(1)
            val path = Path()
            weights.forEachIndexed { index, weight ->
                val x = index * stepX
                // Inverted: a heavier weight must sit higher on screen.
                val y = size.height * (1f - ((weight - min) / span).toFloat())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
            drawCircle(
                color = lineColor,
                radius = 8f,
                center = Offset(size.width, size.height * (1f - ((weights.last() - min) / span).toFloat())),
            )
        }
        Text(
            text = trimNumber(max) + " … " + trimNumber(min) + " kg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}