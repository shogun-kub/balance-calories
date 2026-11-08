package com.balancecalories.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

/**
 * Which Health Connect implementation this device offers, if any.
 *
 * Android 14 has the store in the platform; Android 11 needs the standalone Health Connect app
 * installed and up to date (docs/02-research.md §2). The UI must be able to say which case it is
 * looking at instead of showing an empty screen, so the three states are explicit here rather than
 * collapsed into a boolean.
 */
enum class HealthConnectState {
    /** The store is present and can be queried once permissions are granted. */
    AVAILABLE,

    /** The standalone app (or platform module) exists but is too old to serve the client. */
    UPDATE_REQUIRED,

    /** No store on this device: the app must run in "no band" mode. */
    NOT_SUPPORTED,
}

object HealthConnectSupport {

    fun state(context: Context): HealthConnectState =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectState.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectState.UPDATE_REQUIRED
            else -> HealthConnectState.NOT_SUPPORTED
        }
}
