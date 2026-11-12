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
 * A list, not a form. The owner's note of 2026-11-12: editing four meals in place made a wall of fields, so a row
 * now shows its name and time read-only with "Изменить" and "Удалить", and both adding and editing happen on one
 * separate screen. Nothing is written from here except a delete, which is why a refusal ("this meal still has
 * food") can be answered immediately.
 *
 * "Перекус" is the fixed slot: it shows why it has no buttons instead of offering actions that would be refused.
 */
@Composable
fun MealsScreen(
    container: AppContainer,
    profile: Profile,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var slots by remember(profile.id) { mutableStateOf<List<MealSlot>>(emptyList()) }
    var message by remember { mutableStateOf<Int?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(profile.id, refresh) {
        slots = container.meals.slots(profile.id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = slot.title(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = slot.plannedMinutes?.let(MealTimes::formatClock)
                            ?: stringResource(R.string.meals_no_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (slot.fixed) {
                    Text(
                        text = stringResource(R.string.meals_fixed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    TextButton(onClick = { onEdit(slot.key) }) {
                        Text(stringResource(R.string.action_edit))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                message = when (container.meals.delete(profile.id, slot.key)) {
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
            }
        }

        message?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add))
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}
