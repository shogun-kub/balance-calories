package com.balancecalories.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.balancecalories.app.profile.ProfileFormScreen
import com.balancecalories.app.profiles.ProfilesScreen
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.energy.TargetLimit
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.health.HealthConnectState
import com.balancecalories.data.health.HealthConnectSupport
import java.time.LocalDate
import kotlinx.coroutines.launch

private enum class Screen { TODAY, PROFILES, FORM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

/**
 * Routing is three enum states and the stored "current profile" id: no Navigation Compose yet, because
 * four destinations do not justify the dependency.
 *
 * Which profile is shown comes from the database, not from list order. If the stored id points at a
 * profile that no longer exists, the first available one is adopted, so a deleted selection cannot leave
 * the app pointing at nothing.
 */
@Composable
private fun Root() {
    val container = (LocalContext.current.applicationContext as BalanceCaloriesApp).container
    val scope = rememberCoroutineScope()

    val profiles by container.profiles.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by container.settings.observeActiveProfileId()
        .collectAsStateWithLifecycle(initialValue = null)

    var screen by remember { mutableStateOf(Screen.TODAY) }
    var editing by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(profiles, activeId) {
        if (profiles.isNotEmpty() && profiles.none { it.id == activeId }) {
            container.settings.setActiveProfile(profiles.first().id)
        }
    }

    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    when {
        profiles.isEmpty() -> ProfileFormScreen(
            container = container,
            existing = null,
            onSaved = { screen = Screen.TODAY },
        )

        screen == Screen.PROFILES -> ProfilesScreen(
            container = container,
            profiles = profiles,
            activeProfileId = active?.id,
            onAddProfile = {
                editing = null
                screen = Screen.FORM
            },
            onEditProfile = {
                editing = it
                screen = Screen.FORM
            },
            onSelectProfile = { profile ->
                scope.launch { container.settings.setActiveProfile(profile.id) }
                screen = Screen.TODAY
            },
            onBack = { screen = Screen.TODAY },
        )

        screen == Screen.FORM -> ProfileFormScreen(
            container = container,
            existing = editing,
            onSaved = {
                editing = null
                screen = Screen.TODAY
            },
        )

        active == null -> ProfileFormScreen(
            container = container,
            existing = null,
            onSaved = { screen = Screen.TODAY },
        )

        else -> ProfileTarget(
            profile = active,
            container = container,
            onOpenProfiles = { screen = Screen.PROFILES },
        )
    }
}

/**
 * Reads the profile's latest weight and computes the target with `:core`. Nothing is written here: a
 * target enters `target_history` at onboarding, at an explicit edit, or when the user recalculates.
 */
@Composable
private fun ProfileTarget(profile: Profile, container: AppContainer, onOpenProfiles: () -> Unit) {
    val today = remember { LocalDate.now() }
    var weightKg by remember(profile.id) { mutableStateOf<Double?>(null) }
    var loaded by remember(profile.id) { mutableStateOf(false) }

    LaunchedEffect(profile.id) {
        weightKg = container.body.latestWeightKg(profile.id)
        loaded = true
    }

    val weight = weightKg
    if (!loaded) return

    if (weight == null) {
        EmptyState(
            title = stringResource(R.string.no_weight_title),
            note = stringResource(R.string.no_weight_note),
        )
        return
    }

    val target = remember(profile.id, weight, today) {
        TargetCalculator.seed(
            profile = profile.toEnergyProfile(weight),
            goalRateKgPerWeek = profile.goalRateKgPerWeek,
            on = today,
        )
    }
    val context = LocalContext.current
    val hcState = remember(context) { HealthConnectSupport.state(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.target_note),
            style = MaterialTheme.typography.bodySmall,
        )

        MetricRow(stringResource(R.string.label_bmr), stringResource(R.string.kcal_decimal, target.bmrKcal))
        MetricRow(stringResource(R.string.label_tdee), stringResource(R.string.kcal_decimal, target.tdeeKcal))
        MetricRow(stringResource(R.string.label_target), stringResource(R.string.kcal_int, target.kcal))
        MetricRow(stringResource(R.string.label_limit), target.limit.localized())
        MetricRow(stringResource(R.string.label_health_connect), hcState.localized())

        TextButton(onClick = onOpenProfiles) {
            Text(stringResource(R.string.profiles_title))
        }
    }
}

@Composable
private fun EmptyState(title: String, note: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = note, style = MaterialTheme.typography.bodySmall)
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
