package com.balancecalories.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

/**
 * Reads the Health Connect state of this device.
 *
 * The mapping itself lives in [HealthConnectStateMapping]; this file supplies only the two Android
 * facts it needs, because `getSdkStatus` cannot tell an absent provider from an outdated one.
 */
object HealthConnectSupport {

    /** The standalone Health Connect app (Android 9–13) and the platform module (Android 14+). */
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    fun state(context: Context): HealthConnectState =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE ->
                decide(context, available = true, updateRequired = false)

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                decide(context, available = false, updateRequired = true)

            else -> decide(context, available = false, updateRequired = false)
        }

    private fun decide(context: Context, available: Boolean, updateRequired: Boolean) =
        HealthConnectStateMapping.decide(
            available = available,
            updateRequired = updateRequired,
            providerInstalled = isProviderInstalled(context),
        )

    /** Whether the provider package is on the device at all, whatever its version. */
    @Suppress("DEPRECATION") // the flags overload needs API 33; minSdk is 28
    internal fun isProviderInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PROVIDER_PACKAGE, 0) }.isSuccess
}
