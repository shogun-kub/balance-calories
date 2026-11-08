package com.balancecalories.app.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.AppContainer
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.R
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.EnergyProfile
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.profile.UnitSystem
import com.balancecalories.core.profile.Units
import com.balancecalories.data.repo.BodyMeasurementRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class GoalOption(val rateKgPerWeek: Double, @StringRes val label: Int)

private val goalOptions = listOf(
    GoalOption(-0.5, R.string.goal_lose_fast),
    GoalOption(-0.25, R.string.goal_lose_steady),
    GoalOption(0.0, R.string.goal_maintain),
    GoalOption(0.25, R.string.goal_gain),
)

private val activityOptions = listOf(
    ActivityLevel.SEDENTARY to R.string.activity_sedentary,
    ActivityLevel.LIGHT to R.string.activity_light,
    ActivityLevel.MODERATE to R.string.activity_moderate,
    ActivityLevel.HARD to R.string.activity_hard,
    ActivityLevel.ATHLETE to R.string.activity_athlete,
)

/**
 * One screen for both creating and editing a profile: the fields are identical, and having two forms
 * would guarantee that validation drifts apart.
 *
 * Creating writes three rows (profile, first weight, seed target). Editing updates the profile and only
 * records a new weight when the number actually changed, so a rename does not litter the weight history
 * with a duplicate reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormScreen(
    container: AppContainer,
    existing: Profile?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val oldestAllowed = remember(today) { today.minusYears(14) }
    val isEditing = existing != null
    // Units are a profile preference; a new profile starts metric and can be switched in settings.
    val unitSystem = existing?.unitSystem ?: UnitSystem.METRIC

    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var sex by remember(existing?.id) { mutableStateOf(existing?.sex ?: Sex.MALE) }
    var birthDate by remember(existing?.id) { mutableStateOf(existing?.birthDate) }
    var heightText by remember(existing?.id) {
        mutableStateOf(existing?.heightCm?.let { formatHeight(it, unitSystem) } ?: "")
    }
    var weightText by remember(existing?.id) { mutableStateOf("") }
    var activity by remember(existing?.id) {
        mutableStateOf(existing?.activityLevel ?: ActivityLevel.LIGHT)
    }
    var goal by remember(existing?.id) {
        mutableStateOf(
            goalOptions.firstOrNull { it.rateKgPerWeek == existing?.goalRateKgPerWeek } ?: goalOptions[1],
        )
    }
    var goalWeightText by remember(existing?.id) {
        mutableStateOf(existing?.goalWeightKg?.let { formatWeight(it, unitSystem) } ?: "")
    }
    var originalWeight by remember(existing?.id) { mutableStateOf<Double?>(null) }
    var validationError by remember { mutableStateOf<Int?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var activityMenuOpen by remember { mutableStateOf(false) }
    var goalMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(existing?.id) {
        val id = existing?.id ?: return@LaunchedEffect
        val latest = container.body.latestWeightKg(id)
        originalWeight = latest
        if (weightText.isEmpty()) weightText = latest?.let { formatWeight(it, unitSystem) } ?: ""
    }

    // What the user typed, in their units.
    val heightEntered = parseDecimal(heightText)
    val weightEntered = parseDecimal(weightText)
    val metricHeight = heightEntered?.let { Units.heightToCm(it, unitSystem) }
    val metricWeight = weightEntered?.let { Units.weightToKg(it, unitSystem) }
    // Optional: an empty field means "no goal", not zero kilograms.
    val metricGoalWeight = parseDecimal(goalWeightText)?.let { Units.weightToKg(it, unitSystem) }
    val dob = birthDate

    val preview = remember(sex, dob, metricHeight, metricWeight, activity, goal, today, oldestAllowed) {
        if (dob != null && dob.isBefore(oldestAllowed) && metricHeight != null && metricWeight != null &&
            metricHeight in HEIGHT_RANGE_CM && metricWeight in WEIGHT_RANGE_KG
        ) {
            TargetCalculator.seed(
                profile = EnergyProfile(sex, dob, metricHeight, metricWeight, activity),
                goalRateKgPerWeek = goal.rateKgPerWeek,
                on = today,
            ).kcal
        } else {
            null
        }
    }

    fun save() {
        saveError = null
        val metricHeight = heightEntered?.let { Units.heightToCm(it, unitSystem) }
        val metricWeight = weightEntered?.let { Units.weightToKg(it, unitSystem) }
        validationError = when {
            name.isBlank() -> R.string.error_name
            dob == null || !dob.isBefore(oldestAllowed) || dob.isBefore(LocalDate.of(1900, 1, 1)) ->
                R.string.error_birth_date
            metricHeight == null || metricHeight !in HEIGHT_RANGE_CM ->
                if (unitSystem == UnitSystem.IMPERIAL) R.string.error_height_imperial else R.string.error_height
            metricWeight == null || metricWeight !in WEIGHT_RANGE_KG ->
                if (unitSystem == UnitSystem.IMPERIAL) R.string.error_weight_imperial else R.string.error_weight
            goalWeightText.isNotBlank() && (metricGoalWeight == null || metricGoalWeight !in WEIGHT_RANGE_KG) ->
                if (unitSystem == UnitSystem.IMPERIAL) R.string.error_weight_imperial else R.string.error_weight
            else -> null
        }
        if (validationError != null || dob == null || metricHeight == null || metricWeight == null) return

        val height = metricHeight
        val weight = metricWeight
        saving = true
        scope.launch {
            try {
                if (existing == null) {
                    val created = container.profiles.create(
                        Profile(
                            name = name.trim(),
                            sex = sex,
                            birthDate = dob,
                            heightCm = height,
                            activityLevel = activity,
                            goalRateKgPerWeek = goal.rateKgPerWeek,
                            goalWeightKg = metricGoalWeight,
                            bandEnabled = container.healthConnectAvailable,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    container.body.record(
                        profileId = created.id,
                        weightKg = weight,
                        localDate = today,
                        source = BodyMeasurementRepository.SOURCE_MANUAL,
                    )
                    container.targets.recordSeed(created, weightKg = weight, on = today)
                    container.settings.setActiveProfile(created.id)
                } else {
                    val updated = existing.copy(
                        name = name.trim(),
                        sex = sex,
                        birthDate = dob,
                        heightCm = height,
                        activityLevel = activity,
                        goalRateKgPerWeek = goal.rateKgPerWeek,
                        goalWeightKg = metricGoalWeight,
                    )
                    container.profiles.update(updated)
                    if (originalWeight == null || originalWeight!!.roundToInt() != weight.roundToInt()) {
                        container.body.record(
                            profileId = updated.id,
                            weightKg = weight,
                            localDate = today,
                            source = BodyMeasurementRepository.SOURCE_MANUAL,
                        )
                    }
                    container.targets.recordSeed(updated, weightKg = weight, on = today)
                }
                onSaved()
            } catch (failure: Exception) {
                saveError = failure.message ?: failure::class.java.simpleName
            } finally {
                saving = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(if (isEditing) R.string.profile_edit_title else R.string.onboarding_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!isEditing) {
            Text(stringResource(R.string.onboarding_note), style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.field_sex), style = MaterialTheme.typography.labelLarge)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sex == Sex.MALE,
                onClick = { sex = Sex.MALE },
                label = { Text(stringResource(R.string.sex_male)) },
            )
            FilterChip(
                selected = sex == Sex.FEMALE,
                onClick = { sex = Sex.FEMALE },
                label = { Text(stringResource(R.string.sex_female)) },
            )
        }

        Text(stringResource(R.string.field_birth_date), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { pickerOpen = true }) {
            Text(
                text = dob?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    ?: stringResource(R.string.field_birth_date_empty),
            )
        }

        OutlinedTextField(
            value = heightText,
            onValueChange = { heightText = filterDecimal(it) },
            label = {
                Text(
                    stringResource(
                        if (unitSystem == UnitSystem.IMPERIAL) R.string.field_height_imperial
                        else R.string.field_height,
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = filterDecimal(it) },
            label = {
                Text(
                    stringResource(
                        if (unitSystem == UnitSystem.IMPERIAL) R.string.field_weight_imperial
                        else R.string.field_weight,
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.field_activity), style = MaterialTheme.typography.labelLarge)
        Row {
            OutlinedButton(onClick = { activityMenuOpen = true }) {
                Text(stringResource(activityOptions.first { it.first == activity }.second))
            }
            DropdownMenu(expanded = activityMenuOpen, onDismissRequest = { activityMenuOpen = false }) {
                activityOptions.forEach { (level, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            activity = level
                            activityMenuOpen = false
                        },
                    )
                }
            }
        }

        Text(stringResource(R.string.field_goal), style = MaterialTheme.typography.labelLarge)
        Row {
            OutlinedButton(onClick = { goalMenuOpen = true }) {
                Text(stringResource(goal.label))
            }
            DropdownMenu(expanded = goalMenuOpen, onDismissRequest = { goalMenuOpen = false }) {
                goalOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.label)) },
                        onClick = {
                            goal = option
                            goalMenuOpen = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = goalWeightText,
            onValueChange = { goalWeightText = it },
            label = { Text(stringResource(R.string.field_goal_weight)) },
            supportingText = { Text(stringResource(R.string.field_goal_weight_help)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (preview != null) {
            Text(
                text = stringResource(R.string.preview_target, preview),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        validationError?.let { error ->
            Text(
                text = stringResource(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        saveError?.let { message ->
            Text(
                text = stringResource(R.string.error_save, message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(onClick = ::save, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (saving) R.string.saving else R.string.action_save))
        }
    }

    if (pickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (dob ?: today.minusYears(30))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            birthDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        pickerOpen = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val HEIGHT_RANGE_CM = 100.0..250.0
private val WEIGHT_RANGE_KG = 30.0..300.0

/** Accepts digits plus one decimal separator, because imperial entry needs 68.9 inches, not 68. */
private fun filterDecimal(text: String): String {
    val builder = StringBuilder()
    var separatorSeen = false
    for (char in text) {
        when {
            char.isDigit() -> builder.append(char)
            (char == '.' || char == ',') && !separatorSeen && builder.isNotEmpty() -> {
                builder.append('.')
                separatorSeen = true
            }
        }
    }
    return builder.toString()
}

private fun parseDecimal(text: String): Double? = text.toDoubleOrNull()

private fun formatHeight(cm: Double, system: UnitSystem): String = when (system) {
    UnitSystem.METRIC -> cm.roundToInt().toString()
    UnitSystem.IMPERIAL -> String.format(Locale.US, "%.1f", Units.heightFromCm(cm, system))
}

private fun formatWeight(kg: Double, system: UnitSystem): String = when (system) {
    UnitSystem.METRIC -> kg.roundToInt().toString()
    UnitSystem.IMPERIAL -> String.format(Locale.US, "%.1f", Units.weightFromKg(kg, system))
}