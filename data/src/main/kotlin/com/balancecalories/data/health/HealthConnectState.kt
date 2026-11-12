package com.balancecalories.data.health

/**
 * Which Health Connect implementation this device offers, if any.
 *
 * Android 14 has the store in the platform; Android 11 needs the standalone Health Connect app
 * installed and up to date (docs/02-research.md §2). The three states were explicit from the start
 * rather than collapsed into a boolean, and the device matrix added the fourth: **no provider at
 * all** is not the same sentence as **a provider too old to serve this client**, yet
 * `HealthConnectClient.getSdkStatus` reports `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` for both.
 *
 * Measured on the Redmi Note 8 Pro (Android 11, no Health Connect installed): the app told the user
 * "требуется обновление" about an app that was not there. Whether the provider package exists is the
 * only fact that separates the two, so it is checked separately — see [HealthConnectStateMapping].
 */
enum class HealthConnectState {
    /** The store is present and can be queried once permissions are granted. */
    AVAILABLE,

    /** This device could host the store, but the standalone app is not installed. */
    NOT_INSTALLED,

    /** The standalone app (or platform module) exists but is too old to serve the client. */
    UPDATE_REQUIRED,

    /** No store on this device: the app must run in "no band" mode. */
    NOT_SUPPORTED,
}

/**
 * The decision itself, free of Android so a JVM test can pin all four outcomes.
 *
 * The precedence is the point: an available store wins over everything; a device the SDK does not
 * support is [HealthConnectState.NOT_SUPPORTED] whatever the package table says; and only when the
 * SDK asks for an update does the package's presence decide between
 * [HealthConnectState.NOT_INSTALLED] and [HealthConnectState.UPDATE_REQUIRED].
 */
internal object HealthConnectStateMapping {

    fun decide(
        available: Boolean,
        updateRequired: Boolean,
        providerInstalled: Boolean,
    ): HealthConnectState = when {
        available -> HealthConnectState.AVAILABLE
        !updateRequired -> HealthConnectState.NOT_SUPPORTED
        providerInstalled -> HealthConnectState.UPDATE_REQUIRED
        else -> HealthConnectState.NOT_INSTALLED
    }
}
