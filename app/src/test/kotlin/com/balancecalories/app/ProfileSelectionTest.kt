package com.balancecalories.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The repair rule for the active profile.
 *
 * The failure this pins: on a cold start the settings read and the profiles read race, and an effect that
 * treated "not read yet" as "nothing stored" wrote the first profile back over the chosen one — on the
 * owner's phone, `Test` over his own profile. The rule therefore may only *allow* a write when the stored
 * value is known and points at a profile that is gone.
 */
class ProfileSelectionTest {

    @Test
    fun aStoredProfileThatStillExistsIsKept() {
        assertEquals(5L, repairedActiveId(stored = 5L, available = listOf(3L, 5L)))
    }

    @Test
    fun aStoredProfileThatIsGoneFallsBackToTheFirst() {
        assertEquals(3L, repairedActiveId(stored = 9L, available = listOf(3L, 5L)))
    }

    @Test
    fun anAbsentStoredProfileFallsBackToTheFirst() {
        assertEquals(3L, repairedActiveId(stored = null, available = listOf(3L, 5L)))
    }

    @Test
    fun nothingAvailableDecidesNothing() {
        assertNull(repairedActiveId(stored = 5L, available = emptyList()))
    }
}
