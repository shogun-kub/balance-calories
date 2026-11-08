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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.EnergyProfile
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.energy.TargetLimit
import com.balancecalories.data.health.HealthConnectState
import com.balancecalories.data.health.HealthConnectSupport
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TargetScreen()
                }
            }
        }
    }
}

/**
 * A neutral placeholder profile — a stand-in with no relation to a real person, to be replaced by the
 * onboarding flow later in M1. Real body data never enters the source tree (AGENTS.md, hard rule 1).
 */
private val demoProfile = EnergyProfile(
    sex = Sex.MALE,
    birthDate = LocalDate.of(1990, 1, 1),
    heightCm = 175.0,
    weightKg = 80.0,
    activityLevel = ActivityLevel.LIGHT,
)

@Composable
private fun TargetScreen() {
    val today = remember { LocalDate.now() }
    val target = remember(today) {
        TargetCalculator.seed(demoProfile, goalRateKgPerWeek = -0.3, on = today)
    }
    val context = LocalContext.current
    val hcState = remember { HealthConnectSupport.state(context) }

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
        Text(
            text = stringResource(R.string.target_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.target_note),
            style = MaterialTheme.typography.bodySmall,
        )

        MetricRow(
            label = stringResource(R.string.label_bmr),
            value = stringResource(R.string.kcal_decimal, target.bmrKcal),
        )
        MetricRow(
            label = stringResource(R.string.label_tdee),
            value = stringResource(R.string.kcal_decimal, target.tdeeKcal),
        )
        MetricRow(
            label = stringResource(R.string.label_target),
            value = stringResource(R.string.kcal_int, target.kcal),
        )
        MetricRow(
            label = stringResource(R.string.label_limit),
            value = target.limit.localized(),
        )
        MetricRow(
            label = stringResource(R.string.label_health_connect),
            value = hcState.localized(),
        )
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
