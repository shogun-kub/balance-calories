package com.balancecalories.data.health

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The four Health Connect states, pinned without a device.
 *
 * The reason this mapping has a test of its own: on the Redmi Note 8 Pro (Android 11, Health Connect
 * **not installed**) the app used to say "требуется обновление" about an app that was not there, because
 * `getSdkStatus` answers `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` both for a missing provider and for
 * an outdated one. Only the package table separates them, so the precedence is pinned here.
 */
class HealthConnectStateTest {

    private fun decide(
        available: Boolean = false,
        updateRequired: Boolean = false,
        providerInstalled: Boolean = false,
    ) = HealthConnectStateMapping.decide(available, updateRequired, providerInstalled)

    @Test
    fun anAvailableStoreIsAvailableWhateverThePackageTableSays() {
        assertEquals(
            HealthConnectState.AVAILABLE,
            decide(available = true, updateRequired = false, providerInstalled = false),
        )
    }

    @Test
    fun aMissingProviderIsNotInstalledNotOutdated() {
        assertEquals(
            HealthConnectState.NOT_INSTALLED,
            decide(updateRequired = true, providerInstalled = false),
        )
    }

    @Test
    fun anInstalledButOldProviderAsksForAnUpdate() {
        assertEquals(
            HealthConnectState.UPDATE_REQUIRED,
            decide(updateRequired = true, providerInstalled = true),
        )
    }

    @Test
    fun aDeviceTheSdkDoesNotSupportIsNotSupportedEvenWithThePackagePresent() {
        assertEquals(
            HealthConnectState.NOT_SUPPORTED,
            decide(updateRequired = false, providerInstalled = true),
        )
    }
}
