package com.balancecalories.app.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.food.trimNumber
import com.balancecalories.app.ui.LabelValueRow
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.profile.UnitSystem
import com.balancecalories.core.profile.Units
import com.balancecalories.core.time.DayWindow
import com.balancecalories.data.repo.BodyMeasurementRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/**
 * Weight: the number the adaptive TDEE needs, in one field.
 *
 * The entry is the point of this screen. Weighing yourself is a twice-a-week habit, and until now it lived four
 * taps deep inside the profile form — which is why it was asked for. Everything else here is a view over the same
 * `body_measurement` rows the rest of the app reads, written with `source = manual` so a typed number outranks
 * anything a band or Health Connect inferred.
 *
 * Below the entry: the chart (dots for what the scale said, a line for the trend, the goal dashed) over 30, 90 or
 * 365 days, then the recent readings as numbers. The chart answers "is this working" at a glance; the list answers
 * "what did I weigh on the 14th".
 */
@Composable
fun WeightScreen(
    container: AppContainer,
    profile: Profile,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(profile.id) { DayWindow.dateOf(Instant.now(), zone, profile.dayStartHour) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val unitLabel = stringResource(
        if (profile.unitSystem == UnitSystem.IMPERIAL) R.string.unit_pounds else R.string.unit_kilograms,
    )

    var latest by remember(profile.id) { mutableStateOf<Double?>(null) }
    var trend by remember(profile.id) { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) }
    var points by remember(profile.id) { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) }
    var rangeDays by remember(profile.id) { mutableIntStateOf(30) }
    var text by remember(profile.id) { mutableStateOf("") }
    var saved by remember(profile.id) { mutableStateOf<Double?>(null) }
    var error by remember(profile.id) { mutableStateOf<Int?>(null) }
    var refresh by remember(profile.id) { mutableIntStateOf(0) }

    LaunchedEffect(profile.id, refresh, rangeDays) {
        val weight = container.body.latestWeightKg(profile.id)
        latest = weight
        trend = container.history.weightTrend(profile.id, today, days = rangeDays)
        points = container.history.weightPoints(profile.id, today, days = rangeDays)
        // Prefill with the last reading: the usual case is the same weight, or a little different.
        if (refresh == 0 && weight != null) {
            text = trimNumber(Units.weightFromKg(weight, profile.unitSystem))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.weight_title), style = MaterialTheme.typography.headlineMedium)

        Text(
            text = latest?.let {
                stringResource(
                    R.string.weight_latest,
                    trimNumber(Units.weightFromKg(it, profile.unitSystem)),
                    unitLabel,
                )
            } ?: stringResource(R.string.weight_none),
            style = MaterialTheme.typography.titleMedium,
        )

        HorizontalDivider()

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
                saved = null
            },
            label = { Text(stringResource(R.string.weight_entry_label)) },
            suffix = { Text(unitLabel) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                val typed = typedNumber(text)
                val kg = typed?.let { Units.weightToKg(it, profile.unitSystem) }
                when {
                    kg == null -> error = R.string.weight_error_number
                    // A typed body mass outside this range is a typo, not a measurement: refusing it here is
                    // cheaper than letting a 800 kg row skew the trend for a month.
                    kg < 20.0 || kg > 400.0 -> error = R.string.weight_error_range
                    else -> scope.launch {
                        container.body.record(
                            profileId = profile.id,
                            weightKg = kg,
                            localDate = today,
                            source = BodyMeasurementRepository.SOURCE_MANUAL,
                        )
                        // A new weight changes BMR, and therefore today's target: record the seed so the target
                        // is not left computed from last month's weight.
                        container.targets.recordSeed(profile, kg, today)
                        latest = kg
                        saved = kg
                        refresh++
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.weight_save))
        }

        saved?.let {
            Text(
                text = stringResource(
                    R.string.weight_saved,
                    trimNumber(Units.weightFromKg(it, profile.unitSystem)),
                    unitLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider()

        WeightChart(
            points = points,
            trend = trend,
            goalKg = profile.goalWeightKg,
            today = today,
            rangeDays = rangeDays,
            onRangeChange = { rangeDays = it },
            system = profile.unitSystem,
            unitLabel = unitLabel,
            modifier = Modifier.fillMaxWidth(),
        )

        // The rate belongs to the chart, not to the header: it is computed over the range the chips select, so
        // it changes when the range does and should be read next to the line it describes.
        ratePerWeek(trend, profile.unitSystem)?.let { rate ->
            Text(
                text = stringResource(R.string.weight_rate, signed(rate), unitLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        HorizontalDivider()
        Text(stringResource(R.string.weight_recent), style = MaterialTheme.typography.titleSmall)
        if (points.isEmpty()) {
            Text(stringResource(R.string.history_trend_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            // The readings, not the smoothed line: a list headed "recent weigh-ins" must print what the scale said.
            points.takeLast(10).asReversed().forEach { (date, kg) ->
                LabelValueRow(
                    label = formatter.format(date),
                    value = trimNumber(Units.weightFromKg(kg, profile.unitSystem)) + " " + unitLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Rate of change in the display unit per week, or null when there is not enough of a trend to state one. */
private fun ratePerWeek(points: List<Pair<LocalDate, Double>>, system: UnitSystem): Double? {
    if (points.size < 2) return null
    val days = ChronoUnit.DAYS.between(points.first().first, points.last().first)
    if (days <= 0) return null
    val kgPerWeek = (points.last().second - points.first().second) * 7.0 / days
    return Units.weightFromKg(kgPerWeek, system)
}

/**
 * Accepts a comma decimal separator, because that is the key on a Russian keyboard and typing a full stop there is
 * an extra tap for a number people enter twice a week.
 */
private fun typedNumber(raw: String): Double? = raw.replace(',', '.').trim().toDoubleOrNull()

private fun signed(value: Double): String {
    val text = trimNumber(kotlin.math.abs(value))
    return when {
        value > 0 -> "+$text"
        value < 0 -> "-$text"
        else -> text
    }
}
