package com.balancecalories.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The app's theme: Material 3's baseline light or dark scheme, following the phone's setting, plus the chart tones
 * that go with it.
 *
 * Two things this deliberately does *not* do:
 *
 * * **It does not invent a brand colour.** The scheme is Material's baseline pair, which is what the app has drawn
 *   with since M1; inventing an accent here would have changed every screen in a milestone about legibility.
 * * **It does not offer a manual switch.** The phone already knows whether it is dark, and a second source of
 *   truth is one more thing to get wrong before release. If a per-app switch is ever wanted it belongs in settings
 *   next to units and language.
 *
 * The chart tones travel with the theme through `LocalChartTones` — the point of M7.5 is that a macro's colour is
 * chosen *against a surface*, and the surface changes here.
 */
@Composable
fun BalanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalChartTones provides if (darkTheme) DarkChartTones else LightChartTones,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
