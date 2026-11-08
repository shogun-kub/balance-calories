package com.balancecalories.app.onboarding

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
import androidx.compose.runtime.getValue
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
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.EnergyProfile
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.repo.BodyMeasurementRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

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
 * Onboarding in one scrollable screen: identity, body, goal. It writes three things in order — the
 * profile, its first weight reading and the seed target — because a profile without a weight cannot
 * produce a number, and a target that was never stored cannot be explained later.
 *
 * Validation happens before anything touches the database; a half-written profile is worse than an
 * error message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val oldestAllowed = remember(today) { today.minusYears(14) }

    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var birthDate by remember { mutableStateOf<LocalDate?>(null) }
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf(ActivityLevel.LIGHT) }
    var goal by remember { mutableStateOf(goalOptions[1]) }
    var validationError by remember { mutableStateOf<Int?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var activityMenuOpen by remember { mutableStateOf(false) }
    var goalMenuOpen by remember { mutableStateOf(false) }

    val height = heightText.toDoubleOrNull()
    val weight = weightText.toDoubleOrNull()
    val dob = birthDate

    val preview = remember(sex, dob, height, weight, activity, goal, today, oldestAllowed) {
        if (dob != null && dob.isBefore(oldestAllowed) && height != null && weight != null &&
            height in HEIGHT_RANGE && weight in WEIGHT_RANGE
        ) {
            TargetCalculator.seed(
                profile = EnergyProfile(sex, dob, height, weight, activity),
                goalRateKgPerWeek = goal.rateKgPerWeek,
                on = today,
            ).kcal
        } else {
            null
        }
    }

    fun save() {
        saveError = null
        validationError = when {
            name.isBlank() -> R.string.error_name
            dob == null || !dob.isBefore(oldestAllowed) || dob.isBefore(LocalDate.of(1900, 1, 1)) ->
                R.string.error_birth_date
            height == null || height !in HEIGHT_RANGE -> R.string.error_height
            weight == null || weight !in WEIGHT_RANGE -> R.string.error_weight
            else -> null
        }
        if (validationError != null || dob == null || height == null || weight == null) return

        saving = true
        scope.launch {
            try {
                val created = container.profiles.create(
                    Profile(
                        name = name.trim(),
                        sex = sex,
                        birthDate = dob,
                        heightCm = height,
                        activityLevel = activity,
                        goalRateKgPerWeek = goal.rateKgPerWeek,
                        // This phone is the one with the band until a second profile claims it.
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
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.onboarding_note), style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.field_sex), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            onValueChange = { heightText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.field_height)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.field_weight)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

private val HEIGHT_RANGE = 100.0..250.0
private val WEIGHT_RANGE = 30.0..300.0
