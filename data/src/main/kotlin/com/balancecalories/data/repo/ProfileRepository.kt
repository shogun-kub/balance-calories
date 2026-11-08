package com.balancecalories.data.repo

import androidx.room.withTransaction
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.mapper.toDomain
import com.balancecalories.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Profiles are rows, and exactly one profile per device may read Health Connect (ADR-008). The rule is
 * enforced here rather than in the UI, so any future entry point inherits it.
 */
class ProfileRepository(
    private val database: BalanceCaloriesDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val dao = database.profileDao()

    fun observeActive(): Flow<List<Profile>> =
        dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    suspend fun byId(id: Long): Profile? = dao.byId(id)?.toDomain()

    /** Inserts a profile and returns it with the assigned id. Enabling the band clears every other one. */
    suspend fun create(profile: Profile): Profile = database.withTransaction {
        val id = dao.insert(profile.toEntity())
        if (profile.bandEnabled) dao.clearBandExcept(id)
        requireNotNull(dao.byId(id)).toDomain()
    }

    suspend fun update(profile: Profile) = database.withTransaction {
        require(profile.id != Profile.NEW) { "Cannot update a profile that was never stored" }
        dao.update(profile.toEntity())
        if (profile.bandEnabled) dao.clearBandExcept(profile.id)
    }

    /**
     * Turns band access on or off for one profile. Enabling it takes the right away from the others in
     * the same transaction, so the invariant can never be observed broken.
     */
    suspend fun setBandEnabled(id: Long, enabled: Boolean) = database.withTransaction {
        if (enabled) {
            dao.clearBandExcept(id)
            dao.setBandEnabled(id, true)
        } else {
            dao.setBandEnabled(id, false)
        }
    }

    suspend fun archive(id: Long) = dao.archive(id, clock())

    /**
     * Removes the profile and, by cascade, its weights, activity, food and target history. If it was the
     * one being shown, the stored selection is cleared so the app cannot point at a deleted row.
     */
    suspend fun delete(id: Long) = database.withTransaction {
        dao.delete(id)
        val settings = database.settingsDao()
        val active = settings.value(AppSettingsRepository.KEY_ACTIVE_PROFILE_ID)?.toLongOrNull()
        if (active == id) settings.remove(AppSettingsRepository.KEY_ACTIVE_PROFILE_ID)
    }
}
