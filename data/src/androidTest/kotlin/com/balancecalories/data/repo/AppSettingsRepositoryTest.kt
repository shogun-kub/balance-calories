package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.data.db.BalanceCaloriesDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stored preferences that change behaviour rather than appearance.
 *
 * Only the automation switch so far, and the two properties that matter are the ones a person would notice: it is
 * off until someone turns it on, and one profile's choice does not govern the other person's on the same phone.
 */
@RunWith(AndroidJUnit4::class)
class AppSettingsRepositoryTest {

    private lateinit var db: BalanceCaloriesDatabase
    private lateinit var settings: AppSettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
        settings = AppSettingsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun autoApplyIsOffUntilSomeoneAsksForIt() = runBlocking {
        assertFalse("asking is the default", settings.tdeeAutoApply(profileId = 1L))
    }

    @Test
    fun autoApplySurvivesARead() = runBlocking {
        settings.setTdeeAutoApply(profileId = 1L, enabled = true)

        assertTrue(settings.tdeeAutoApply(profileId = 1L))
    }

    @Test
    fun oneProfilesChoiceDoesNotGovernAnother() = runBlocking {
        settings.setTdeeAutoApply(profileId = 1L, enabled = true)

        assertFalse("the second person on this phone decides for themselves", settings.tdeeAutoApply(profileId = 2L))
    }

    @Test
    fun autoApplyCanBeTurnedBackOff() = runBlocking {
        settings.setTdeeAutoApply(profileId = 1L, enabled = true)
        settings.setTdeeAutoApply(profileId = 1L, enabled = false)

        assertFalse(settings.tdeeAutoApply(profileId = 1L))
    }
}
