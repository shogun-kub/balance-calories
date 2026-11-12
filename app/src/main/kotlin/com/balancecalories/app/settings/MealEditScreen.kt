package com.balancecalories.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.food.MealDefaults
import com.balancecalories.core.food.MealTimes
import com.balancecalories.core.profile.Profile
import kotlinx.coroutines.launch

/**
 * One meal, being created or edited: the same screen for both, as the owner asked (2026-11-12).
 *
 * [mealKey] null means "new": the fields start empty. A key means "edit": the name and the time are prefilled from
 * the stored slot, and saving renames that slot and retimes it. The identity never changes, so a rename cannot
 * detach the entries that point at it.
 *
 * The time is a **picker**, not a text field: "08:00" typed by hand was a strict parse with a paragraph of
 * explanation attached, while a clock face cannot produce `8.30` or `0830` at all. A meal may also have no time -
 * the snack bucket has none by design, and a person may not care when they eat their second breakfast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditScreen(
    container: AppContainer,
    profile: Profile,
    mealKey: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(profile.id, mealKey) { mutableStateOf("") }
    var minutes by rememberSaveable(profile.id, mealKey) { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(profile.id, mealKey) {
        if (mealKey == null) return@LaunchedEffect
        val slot = container.meals.slots(profile.id).firstOrNull { it.key == mealKey }
        name = slot?.name.orEmpty()
        minutes = slot?.plannedMinutes
    }

    val editing = mealKey != null
    val shownMinutes = minutes
    // A default slot keeps an empty `name` in the database, so the field shows the name the person actually reads as
    // a placeholder. Saving an empty name therefore means "keep the default", not "a meal with no name".
    val context = LocalContext.current
    val defaultName = mealKey?.let { defaultNameOf(it, context) }.orEmpty()

    if (pickerOpen) {
        val initial = shownMinutes ?: (8 * 60)
        val state = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        minutes = state.hour * 60 + state.minute
                        pickerOpen = false
                    },
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = { TimePicker(state = state) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (editing) R.string.meals_edit_title else R.string.meals_new),
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.meals_name)) },
            // Under the field, not inside it: an empty M3 field shows its label in the placeholder slot, so a
            // placeholder would never be read while the label matters.
            supportingText = { if (defaultName.isNotEmpty()) Text(defaultName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = stringResource(R.string.meals_time_label), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickerOpen = true }) {
                Text(
                    text = shownMinutes?.let(MealTimes::formatClock)
                        ?: stringResource(R.string.meals_no_time),
                )
            }
            if (shownMinutes != null) {
                TextButton(onClick = { minutes = null }) {
                    Text(stringResource(R.string.meals_no_time))
                }
            }
        }

        error?.let { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                val trimmed = name.trim()
                // Only a new meal needs a name; clearing an existing one goes back to the localized default.
                if (mealKey == null && trimmed.isEmpty()) {
                    error = R.string.meals_name_required
                    return@Button
                }
                saving = true
                error = null
                scope.launch {
                    if (mealKey == null) {
                        container.meals.create(profile.id, trimmed, minutes)
                    } else {
                        container.meals.rename(profile.id, mealKey, trimmed)
                        container.meals.setPlannedMinutes(profile.id, mealKey, minutes)
                    }
                    saving = false
                    onSaved()
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}

/** The localized default name for a default key, or empty for a slot somebody created. */
private fun defaultNameOf(key: String, context: android.content.Context): String = when (key) {
    MealDefaults.BREAKFAST -> context.getString(R.string.meal_breakfast)
    MealDefaults.LUNCH -> context.getString(R.string.meal_lunch)
    MealDefaults.DINNER -> context.getString(R.string.meal_dinner)
    MealDefaults.SNACK -> context.getString(R.string.meal_snack)
    else -> ""
}
