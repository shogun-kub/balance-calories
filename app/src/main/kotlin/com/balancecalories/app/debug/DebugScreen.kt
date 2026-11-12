package com.balancecalories.app.debug

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.health.HealthConnectState
import com.balancecalories.data.health.HealthConnectSupport
import com.balancecalories.data.repo.DatabaseSnapshot
import com.balancecalories.data.repo.StoredTarget
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The screen that answers "why is this number what it is". It shows the inputs (profile fields, latest
 * weight), the stored output (the target row in history) and the shape of the database, so a wrong number
 * can be traced to a missing row instead of guessed at.
 *
 * It is read-only on purpose: a debug screen that can write is a way to lose data while investigating it.
 */
@Composable
fun DebugScreen(
    container: AppContainer,
    profile: Profile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<DatabaseSnapshot?>(null) }
    var weight by remember { mutableStateOf<Double?>(null) }
    var target by remember { mutableStateOf<StoredTarget?>(null) }

    LaunchedEffect(profile.id) {
        snapshot = container.debug.snapshot()
        weight = container.body.latestWeightKg(profile.id)
        target = container.targets.latest(profile.id)
    }

    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val version = packageInfo?.let { "${it.versionName} (${it.longVersionCode})" }
        ?: stringResource(R.string.debug_none)
    val hcState = remember(context) { HealthConnectSupport.state(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineMedium)

        Section(stringResource(R.string.debug_app))
        DebugRow(stringResource(R.string.debug_app), version)
        DebugRow(stringResource(R.string.debug_schema), snapshot?.schemaVersion?.toString() ?: "…")

        Section(stringResource(R.string.debug_device))
        DebugRow(stringResource(R.string.debug_device), "${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        DebugRow(
            stringResource(R.string.debug_health_connect),
            stringResource(
                when (hcState) {
                    HealthConnectState.AVAILABLE -> R.string.hc_available
                    HealthConnectState.NOT_INSTALLED -> R.string.hc_not_installed
                    HealthConnectState.UPDATE_REQUIRED -> R.string.hc_update_required
                    HealthConnectState.NOT_SUPPORTED -> R.string.hc_not_supported
                },
            ),
        )

        Section(stringResource(R.string.debug_profile))
        DebugRow(stringResource(R.string.debug_profile), "${profile.name} (#${profile.id})")
        DebugRow(
            stringResource(R.string.field_birth_date),
            profile.birthDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
        )
        DebugRow(stringResource(R.string.field_height), "${profile.heightCm} cm")
        DebugRow(stringResource(R.string.debug_weight), weight?.let { "$it kg" } ?: stringResource(R.string.debug_none))
        DebugRow(stringResource(R.string.field_goal), "${profile.goalRateKgPerWeek} kg/week")

        Section(stringResource(R.string.debug_target))
        val stored = target
        DebugRow(
            stringResource(R.string.debug_target),
            stored?.let { stringResource(R.string.kcal_int, it.kcal) } ?: stringResource(R.string.debug_none),
        )
        DebugRow("mode", stored?.mode ?: stringResource(R.string.debug_none))
        DebugRow("effective_from", stored?.effectiveFrom ?: stringResource(R.string.debug_none))

        Section(stringResource(R.string.debug_database))
        val counts = snapshot
        if (counts == null) {
            DebugRow("…", "…")
        } else {
            DebugRow(stringResource(R.string.row_profile), counts.profiles.toString())
            DebugRow(stringResource(R.string.row_body), counts.weights.toString())
            DebugRow(stringResource(R.string.row_activity), counts.activityDays.toString())
            DebugRow(stringResource(R.string.row_food), counts.foodEntries.toString())
            DebugRow(stringResource(R.string.row_target), counts.targets.toString())
            DebugRow(stringResource(R.string.row_product), counts.products.toString())
            DebugRow(stringResource(R.string.row_recipe), counts.recipes.toString())
            DebugRow(stringResource(R.string.row_setting), counts.appSettings.toString())
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
