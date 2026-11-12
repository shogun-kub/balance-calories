package com.balancecalories.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.food.title
import com.balancecalories.core.food.MealSlot
import com.balancecalories.core.food.MealTimes
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.repo.MealRepository
import kotlinx.coroutines.launch

/**
 * The meals of this profile: how many there are, what they are called, when they are planned.
 *
 * Until 2026-11-12 this was three fixed time fields. The owner's correction is the whole shape of this screen: the
 * number of meals and their names are his, breakfast/lunch/dinner are only what a new profile starts with, and
 * "Перекус" is the one fixed slot - it is where unplanned food goes, so it can be neither renamed nor deleted.
 *
 * Editing is explicit: fields are local until "Сохранить", while delete and add act at once and report why they
 * refused. A rename writes `meal.name` and nothing else, so a year of entries keeps pointing at the same slot.
 */
@Composable
fun MealsScreen(
    container: AppContainer,
    profile: Profile,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var slots by remember(profile.id) { mutableStateOf<List<MealSlot>>(emptyList()) }
    var names by remember(profile.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var times by remember(profile.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var newName by remember(profile.id) { mutableStateOf("") }
    var newTime by remember(profile.id) { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(profile.id, refresh) {
        val loaded = container.meals.slots(profile.id)
        slots = loaded
        names = loaded.associate { it.key to it.name }
        times = loaded.associate {
            it.key to (it.plannedMinutes?.let(MealTimes::formatClock) ?: "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.meals_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.meals_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )

        for (slot in slots) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.title(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (slot.fixed) {
                    Text(
                        text = stringResource(R.string.meals_fixed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (slot.fixed) {
                // The bucket has no name to edit and no time to plan: it holds whatever did not fit elsewhere.
                continue
            }

            OutlinedTextField(
                value = names[slot.key].orEmpty(),
                onValueChange = { names = names + (slot.key to it) },
                label = { Text(stringResource(R.string.meals_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = times[slot.key].orEmpty(),
                onValueChange = { times = times + (slot.key to it) },
                label = { Text(stringResource(R.string.meals_time)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    scope.launch {
                        val result = container.meals.delete(profile.id, slot.key)
                        message = when (result) {
                            MealRepository.DeleteResult.Deleted -> {
                                refresh++
                                R.string.meals_saved
                            }
                            MealRepository.DeleteResult.Fixed -> R.string.meals_cannot_delete
                            MealRepository.DeleteResult.LastMeal -> R.string.meals_last
                            is MealRepository.DeleteResult.HasEntries -> R.string.meals_has_entries
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }

        Button(
            onClick = {
                scope.launch {
                    var ok = true
                    for (slot in slots) {
                        if (slot.fixed) continue
                        val name = names[slot.key].orEmpty().trim()
                        val timeText = times[slot.key].orEmpty().trim()
                        val minutes = if (timeText.isEmpty()) null else MealTimes.parseClock(timeText)
                        if (timeText.isNotEmpty() && minutes == null) {
                            message = R.string.meal_time_invalid
                            ok = false
                            break
                        }
                        if (name.isNotEmpty()) container.meals.rename(profile.id, slot.key, name)
                        container.meals.setPlannedMinutes(profile.id, slot.key, minutes)
                    }
                    if (ok) {
                        message = R.string.meals_saved
                        refresh++
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }

        HorizontalDivider()
        Text(text = stringResource(R.string.meals_new), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text(stringResource(R.string.meals_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = newTime,
            onValueChange = { newTime = it },
            label = { Text(stringResource(R.string.meals_time)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                scope.launch {
                    val name = newName.trim()
                    if (name.isEmpty()) {
                        message = R.string.meals_name_required
                        return@launch
                    }
                    val timeText = newTime.trim()
                    val minutes = if (timeText.isEmpty()) null else MealTimes.parseClock(timeText)
                    if (timeText.isNotEmpty() && minutes == null) {
                        message = R.string.meal_time_invalid
                        return@launch
                    }
                    container.meals.create(profile.id, name, minutes)
                    newName = ""
                    newTime = ""
                    message = R.string.meals_saved
                    refresh++
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_add))
        }

        message?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}
