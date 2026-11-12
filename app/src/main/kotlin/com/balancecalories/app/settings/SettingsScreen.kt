package com.balancecalories.app.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.food.trimNumber
import com.balancecalories.app.ui.NumberField
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.Macro
import com.balancecalories.core.food.MacroNorm
import com.balancecalories.core.food.MacroPreset
import com.balancecalories.core.food.MealTimes
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.profile.UnitSystem
import kotlinx.coroutines.launch

/**
 * Settings that exist today: units for data entry, the day-start hour, and the app language.
 *
 * Units and day start are profile columns, so they are written immediately through the repository rather
 * than held in a form. Language is handled by Android itself: applying a per-app locale recreates the
 * activity, so the new strings come from the system rather than from a hand-rolled resource override.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    profile: Profile,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenData: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMeals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dayStartMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.titleMedium)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = profile.unitSystem == UnitSystem.METRIC,
                onClick = {
                    scope.launch { container.profiles.update(profile.copy(unitSystem = UnitSystem.METRIC)) }
                },
                label = { Text(stringResource(R.string.units_metric)) },
            )
            FilterChip(
                selected = profile.unitSystem == UnitSystem.IMPERIAL,
                onClick = {
                    scope.launch { container.profiles.update(profile.copy(unitSystem = UnitSystem.IMPERIAL)) }
                },
                label = { Text(stringResource(R.string.units_imperial)) },
            )
        }
        Text(stringResource(R.string.settings_units_note), style = MaterialTheme.typography.bodySmall)

        Text(stringResource(R.string.settings_day_start), style = MaterialTheme.typography.titleMedium)
        Row {
            OutlinedButton(onClick = { dayStartMenuOpen = true }) {
                Text(stringResource(R.string.hour_pattern, profile.dayStartHour))
            }
            DropdownMenu(expanded = dayStartMenuOpen, onDismissRequest = { dayStartMenuOpen = false }) {
                (0..23).forEach { hour ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hour_pattern, hour)) },
                        onClick = {
                            dayStartMenuOpen = false
                            if (hour != profile.dayStartHour) {
                                scope.launch {
                                    container.profiles.update(profile.copy(dayStartHour = hour))
                                }
                            }
                        },
                    )
                }
            }
        }
        Text(stringResource(R.string.settings_day_start_note), style = MaterialTheme.typography.bodySmall)

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        LanguageSetting()

        HorizontalDivider()
        MacroNormSetting(container = container, profile = profile)
        Text(stringResource(R.string.settings_meals_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenMeals, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.meals_title))
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)

        // Two items, one shape: a line saying what the place is for, then the button. Profiles used to be a
        // bare coloured link, which is not an obvious way in for a place that also holds the targets and the
        // norms (owner's note, 2026-11-12).
        Text(stringResource(R.string.settings_profiles_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profiles_title))
        }

        Text(stringResource(R.string.settings_data_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenData, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_data_button))
        }

        TextButton(onClick = onOpenDebug) { Text(stringResource(R.string.action_debug)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}

/**
 * The daily protein/fat/carb allowance the Today list compares portions against.
 *
 * Empty means "follow the target": the norms are then 25/30/45 % of the calorie target in grams, recomputed
 * whenever the target moves. Typing numbers stores them for this profile for good, because a person who wrote
 * their own norms has said something the derivation cannot know (ADR-035).
 */

@Composable
private fun MacroNormSetting(container: AppContainer, profile: Profile) {
    val scope = rememberCoroutineScope()
    var targetKcal by remember(profile.id) { mutableStateOf<Int?>(null) }
    var storedNorm by remember(profile.id) { mutableStateOf<MacroNorm?>(null) }
    var protein by remember(profile.id) { mutableStateOf("") }
    var fat by remember(profile.id) { mutableStateOf("") }
    var carbs by remember(profile.id) { mutableStateOf("") }
    var message by remember(profile.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(profile.id) {
        val target = container.targets.latest(profile.id)?.kcal
        targetKcal = target
        val stored = container.settings.macroNorm(profile.id)
        storedNorm = stored
        // The fields show the norm that is *in effect*, not only a stored one: an empty form under a line that
        // says "25/30/45 % of 1904 kcal, in grams" asks a person to invent three numbers to correct a value the
        // app already knows (found on the Honor X7c, 2026-11-11).
        val effective = stored ?: target?.takeIf { it > 0 }?.let { MacroNorm.derived(it) }
        protein = effective?.let { trimNumber(it.proteinG) } ?: ""
        fat = effective?.let { trimNumber(it.fatG) } ?: ""
        carbs = effective?.let { trimNumber(it.carbsG) } ?: ""
    }

    val derived = targetKcal?.takeIf { it > 0 }?.let { MacroNorm.derived(it) }

    Text(stringResource(R.string.macro_norm_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.macro_norm_note), style = MaterialTheme.typography.bodySmall)
    // The split a stored norm represents, computed rather than remembered: a chip is "selected" when the
    // grams in effect are the ones it produces, so editing a field below clears the selection by itself.
    val selectedPreset = if (targetKcal != null && targetKcal!! > 0 && storedNorm != null) {
        MacroPreset.matching(storedNorm!!, targetKcal!!)
    } else {
        null
    }

    Text(
        text = when {
            selectedPreset != null -> stringResource(R.string.macro_norm_preset, presetName(selectedPreset))
            storedNorm != null -> stringResource(R.string.macro_norm_own)
            derived != null -> stringResource(R.string.macro_norm_derived, targetKcal!!)
            else -> stringResource(R.string.macro_norm_own)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
    )

    // Presets first, grams second: "30/30/40" is a split a person can recognise, "96 g of protein" is not
    // (owner request, 2026-11-12). Choosing one writes the grams it implies through the same setting a typed
    // norm uses, so nothing about a stored norm changed.
    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (preset in MacroPreset.entries) {
            FilterChip(
                selected = preset == selectedPreset,
                onClick = {
                    val kcal = targetKcal ?: return@FilterChip
                    val grams = preset.grams(kcal)
                    protein = trimNumber(grams.proteinG)
                    fat = trimNumber(grams.fatG)
                    carbs = trimNumber(grams.carbsG)
                    scope.launch {
                        container.settings.setMacroNorm(profile.id, grams)
                        storedNorm = grams
                        message = R.string.macro_norm_saved
                    }
                },
                label = {
                    Text(
                        stringResource(
                            R.string.macro_preset_label,
                            presetName(preset),
                            preset.percent(Macro.PROTEIN),
                            preset.percent(Macro.FAT),
                            preset.percent(Macro.CARBS),
                        ),
                    )
                },
            )
        }
    }

    NumberField(R.string.macro_norm_protein, protein) { protein = it }
    NumberField(R.string.macro_norm_fat, fat) { fat = it }
    NumberField(R.string.macro_norm_carbs, carbs) { carbs = it }

    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val values = listOf(protein, fat, carbs).map { it.replace(',', '.').toDoubleOrNull() }
                val norm = values.takeIf { list -> list.all { v -> v != null && v > 0.0 } }
                    ?.let { MacroNorm(it[0]!!, it[1]!!, it[2]!!) }
                if (norm == null) {
                    message = R.string.error_macro_norm_value
                    return@Button
                }
                scope.launch {
                    container.settings.setMacroNorm(profile.id, norm)
                    storedNorm = norm
                    message = R.string.macro_norm_saved
                }
            },
        ) {
            Text(stringResource(R.string.action_save))
        }
        OutlinedButton(
            onClick = {
                val target = derived ?: return@OutlinedButton
                protein = trimNumber(target.proteinG)
                fat = trimNumber(target.fatG)
                carbs = trimNumber(target.carbsG)
                scope.launch {
                    // Both halves of the gesture: the fields show the derived numbers, and the stored ones stop
                    // overriding them, so the norms follow the target again.
                    container.settings.clearMacroNorm(profile.id)
                    storedNorm = null
                    message = R.string.macro_norm_saved
                }
            },
            enabled = derived != null,
        ) {
            Text(stringResource(R.string.action_macro_norm_reset))
        }
    }
    message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
}

/**
 * Android 13 gained per-app locales. Below that the platform has no supported way to override the app
 * language without shipping AppCompat's resource machinery, so the setting simply says so instead of
 * offering a control that would do nothing.
 */
@Composable
private fun LanguageSetting() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Text(stringResource(R.string.settings_language_unsupported), style = MaterialTheme.typography.bodySmall)
        return
    }

    val context = LocalContext.current
    val localeManager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var currentTags by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }

    fun apply(tags: String) {
        // An empty LocaleList is how the platform means "follow the system". Building one from "" is not
        // the same thing and is silently ignored, which looks exactly like a button that does nothing.
        localeManager.applicationLocales =
            if (tags.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tags)
        currentTags = tags
    }

    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = currentTags.isEmpty(),
            onClick = { apply("") },
            label = { Text(stringResource(R.string.language_system)) },
        )
        FilterChip(
            selected = currentTags.startsWith("en"),
            onClick = { apply("en") },
            label = { Text(stringResource(R.string.language_english)) },
        )
        FilterChip(
            selected = currentTags.startsWith("ru"),
            onClick = { apply("ru") },
            label = { Text(stringResource(R.string.language_russian)) },
        )
    }
}

@Composable
private fun presetName(preset: MacroPreset): String = stringResource(
    when (preset) {
        MacroPreset.BY_TARGET -> R.string.macro_preset_target
        MacroPreset.EVEN -> R.string.macro_preset_even
        MacroPreset.PROTEIN -> R.string.macro_preset_protein
        MacroPreset.HIGH_PROTEIN -> R.string.macro_preset_high_protein
    },
)
