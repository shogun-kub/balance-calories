package com.balancecalories.app.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.energy.TargetLimit
import com.balancecalories.core.food.DailyIntake
import com.balancecalories.core.food.Meal
import com.balancecalories.core.food.LoggedEntry
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.health.HealthConnectState
import com.balancecalories.data.health.HealthConnectSupport
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Today: the one number the app exists for.
 *
 * Target, eaten and remaining are computed once here and passed down; no other screen recomputes a day.
 * Estimates are marked, because a day built from derived values is not the same claim as one built from
 * labels (AGENTS.md, hard rule 4).
 */
@Composable
fun TodayScreen(
    profile: Profile,
    container: AppContainer,
    onOpenFood: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()

    var weightKg by remember(profile.id) { mutableStateOf<Double?>(null) }
    var intake by remember(profile.id) { mutableStateOf<DailyIntake?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var yesterdayCount by remember { mutableIntStateOf(0) }
    var editingEntry by remember { mutableStateOf<LoggedEntry?>(null) }
    var editGrams by remember { mutableStateOf("") }

    LaunchedEffect(profile.id, refresh) {
        val weight = container.body.latestWeightKg(profile.id)
        weightKg = weight
        if (weight != null) {
            val target = TargetCalculator.seed(
                profile = profile.toEnergyProfile(weight),
                goalRateKgPerWeek = profile.goalRateKgPerWeek,
                on = today,
            )
            intake = container.food.day(profile.id, today, target.kcal)
            yesterdayCount = container.food.countForDay(profile.id, today.minusDays(1))
        }
    }

    val context = LocalContext.current
    val hcState = remember(context) { HealthConnectSupport.state(context) }
    val loadedIntake = intake

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(text = profile.name, style = MaterialTheme.typography.titleMedium)

        if (weightKg == null) {
            Text(text = stringResource(R.string.no_weight_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.no_weight_note), style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        val target = remember(profile.id, weightKg, today) {
            TargetCalculator.seed(
                profile = profile.toEnergyProfile(weightKg!!),
                goalRateKgPerWeek = profile.goalRateKgPerWeek,
                on = today,
            )
        }

        if (loadedIntake == null) {
            Text(text = stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        HorizontalDivider()
        MetricRow(stringResource(R.string.label_target), stringResource(R.string.kcal_int, target.kcal))
        MetricRow(
            stringResource(R.string.today_eaten),
            stringResource(R.string.kcal_decimal, loadedIntake.eatenKcal),
        )
        MetricRow(
            stringResource(R.string.today_remaining),
            stringResource(R.string.kcal_decimal, loadedIntake.remainingKcal),
        )
        if (loadedIntake.hasEstimates) {
            Text(
                text = stringResource(R.string.product_estimated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (loadedIntake.meals.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.macro_line,
                    loadedIntake.proteinG,
                    loadedIntake.fatG,
                    loadedIntake.carbsG,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()

        if (loadedIntake.meals.isEmpty()) {
            Text(text = stringResource(R.string.today_empty), style = MaterialTheme.typography.bodyMedium)
            if (yesterdayCount > 0) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            container.food.copyDay(profile.id, today.minusDays(1), today)
                            refresh++
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_copy_yesterday, yesterdayCount))
                }
            }
        }

        for (meal in Meal.entries) {
            val entries = loadedIntake.entriesFor(meal)
            if (entries.isEmpty()) continue
            Text(text = meal.localized(), style = MaterialTheme.typography.titleSmall)
            for (entry in entries) {
                EntryRow(
                    entry = entry,
                    languageTag = languageTag,
                    onEdit = {
                        editingEntry = entry
                        editGrams = trimGrams(entry.grams)
                    },
                    onDelete = {
                        scope.launch {
                            container.food.delete(entry.id)
                            refresh++
                        }
                    },
                )
            }
        }

        HorizontalDivider()
        Button(onClick = onOpenFood, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.today_add_food))
        }
        TextButton(onClick = onOpenRecipes) { Text(stringResource(R.string.recipes_title)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenProfiles) { Text(stringResource(R.string.profiles_title)) }
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_settings)) }
        }

        // Kept from the M1 screen: the inputs behind the target stay visible while the maths is fresh.
        HorizontalDivider()
        Text(text = stringResource(R.string.target_title), style = MaterialTheme.typography.titleSmall)
        MetricRow(stringResource(R.string.label_bmr), stringResource(R.string.kcal_decimal, target.bmrKcal))
        MetricRow(stringResource(R.string.label_tdee), stringResource(R.string.kcal_decimal, target.tdeeKcal))
        MetricRow(stringResource(R.string.label_limit), target.limit.localized())
        MetricRow(stringResource(R.string.label_health_connect), hcState.localized())
    }

    val entryBeingEdited = editingEntry
    if (entryBeingEdited != null) {
        EditPortionDialog(
            entry = entryBeingEdited,
            grams = editGrams,
            onGramsChange = { editGrams = it },
            onDismiss = { editingEntry = null },
            onConfirm = {
                val grams = editGrams.replace(',', '.').toDoubleOrNull()
                if (grams != null && grams > 0.0) {
                    scope.launch {
                        runCatching { container.food.updateGrams(entryBeingEdited.id, grams) }
                        editingEntry = null
                        refresh++
                    }
                }
            },
        )
    }
}

/**
 * Changing the weight rescales the entry's own snapshot, so the preview here uses the same ratio the
 * repository will apply — no re-read of the product, which may have changed since.
 */
@Composable
private fun EditPortionDialog(
    entry: LoggedEntry,
    grams: String,
    onGramsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val newGrams = grams.replace(',', '.').toDoubleOrNull()
    val previewKcal = if (newGrams != null && entry.grams > 0.0) {
        entry.kcal * (newGrams / entry.grams)
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = grams,
                    onValueChange = onGramsChange,
                    label = { Text(stringResource(R.string.product_portion)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (previewKcal != null) {
                    Text(text = stringResource(R.string.entry_edit_kcal, previewKcal))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = (newGrams ?: 0.0) > 0.0) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 150.0 → "150" in a field a human will edit. */
private fun trimGrams(grams: Double): String =
    if (grams % 1.0 == 0.0) grams.toLong().toString() else grams.toString()

@Composable
private fun EntryRow(entry: LoggedEntry, languageTag: String?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
        ) {
            Text(text = entry.displayName(languageTag), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.today_entry_line, entry.grams, entry.kcal),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Meal.localized(): String = stringResource(
    when (this) {
        Meal.BREAKFAST -> R.string.meal_breakfast
        Meal.LUNCH -> R.string.meal_lunch
        Meal.DINNER -> R.string.meal_dinner
        Meal.SNACK -> R.string.meal_snack
    },
)

@Composable
private fun TargetLimit.localized(): String = stringResource(
    when (this) {
        TargetLimit.NONE -> R.string.limit_none
        TargetLimit.DEFICIT_CAP -> R.string.limit_cap
        TargetLimit.FLOOR -> R.string.limit_floor
    },
)

@Composable
private fun HealthConnectState.localized(): String = stringResource(
    when (this) {
        HealthConnectState.AVAILABLE -> R.string.hc_available
        HealthConnectState.UPDATE_REQUIRED -> R.string.hc_update_required
        HealthConnectState.NOT_SUPPORTED -> R.string.hc_not_supported
    },
)
