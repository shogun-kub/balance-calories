package com.balancecalories.app.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.R
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.MealDefaults
import com.balancecalories.core.food.MealSlot

/**
 * What a slot is called on screen.
 *
 * A slot nobody renamed prints the localized default for its key, which keeps English out of a Russian phone and
 * out of the database: `meal.name` holds only what a person typed. A renamed slot says exactly that, and a
 * generated key with no name - only possible if somebody emptied the field - falls back to "Приём", never to the
 * raw key, which nobody should ever read.
 */
@Composable
internal fun MealSlot.title(): String = when {
    !hasDefaultName -> name
    key == MealDefaults.BREAKFAST -> stringResource(R.string.meal_breakfast)
    key == MealDefaults.LUNCH -> stringResource(R.string.meal_lunch)
    key == MealDefaults.DINNER -> stringResource(R.string.meal_dinner)
    key == MealDefaults.SNACK -> stringResource(R.string.meal_snack)
    else -> stringResource(R.string.meal_fallback_title)
}

/**
 * The meal picker on a logging screen: one chip per slot of this profile, in the order the day reads.
 *
 * Built from the profile's rows rather than a fixed enum, so a meal somebody added yesterday is a place lunch can
 * go today. Nothing is disabled: a fixed slot is a slot that cannot be renamed or deleted, not one that refuses
 * food.
 */
@Composable
internal fun MealChips(
    slots: List<MealSlot>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (slot in slots) {
            FilterChip(
                selected = slot.key == selectedKey,
                onClick = { onSelect(slot.key) },
                label = { Text(slot.title()) },
            )
        }
    }
}
