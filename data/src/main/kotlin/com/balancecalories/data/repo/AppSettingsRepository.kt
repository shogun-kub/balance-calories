package com.balancecalories.data.repo

import com.balancecalories.core.food.MacroNorm
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which profile the app is currently showing. It has to be stored rather than inferred: with two
 * profiles in the household, "the first row" is a guess, and a guess here would silently show one
 * person's numbers under another person's name.
 */
class AppSettingsRepository(private val database: BalanceCaloriesDatabase) {

    private val dao = database.settingsDao()

    fun observeActiveProfileId(): Flow<Long?> =
        dao.observe(KEY_ACTIVE_PROFILE_ID).map { values -> values.firstOrNull()?.toLongOrNull() }

    suspend fun activeProfileId(): Long? = dao.value(KEY_ACTIVE_PROFILE_ID)?.toLongOrNull()

    suspend fun setActiveProfile(profileId: Long) {
        dao.put(AppSettingEntity(KEY_ACTIVE_PROFILE_ID, profileId.toString()))
    }

    suspend fun clearActiveProfile() = dao.remove(KEY_ACTIVE_PROFILE_ID)

    /** Which starter pack version was imported; null means "never imported". */
    /**
     * Whether the adaptive target may be applied without asking, per profile.
     *
     * Default **false**: asking is the default in the app as a whole, and a preference one person sets must not
     * silently govern the other profile on the same phone.
     */
    suspend fun tdeeAutoApply(profileId: Long): Boolean =
        dao.value(TDEE_AUTO_APPLY_PREFIX + profileId)?.toBooleanStrictOrNull() ?: false

    suspend fun setTdeeAutoApply(profileId: Long, enabled: Boolean) {
        dao.put(
            com.balancecalories.data.db.entity.AppSettingEntity(
                key = TDEE_AUTO_APPLY_PREFIX + profileId,
                value = enabled.toString(),
            ),
        )
    }

    suspend fun starterPackVersion(): Int? = dao.value(KEY_STARTER_PACK_VERSION)?.toIntOrNull()

    suspend fun setStarterPackVersion(version: Int) {
        dao.put(AppSettingEntity(KEY_STARTER_PACK_VERSION, version.toString()))
    }

    /**
     * The protein/fat/carb allowance this profile compares portions against, or **null when nobody has set it**.
     *
     * Null is the normal state and it is not the same as "no norms": the caller derives them from the calorie
     * target, so they follow a target that moves with the adaptive TDEE. A stored norm is a deliberate answer and
     * outranks the derivation for good (ADR-035).
     */
    suspend fun macroNorm(profileId: Long): MacroNorm? =
        MacroNorm.decode(dao.value(MACRO_NORM_PREFIX + profileId))

    fun observeMacroNorm(profileId: Long): Flow<MacroNorm?> =
        dao.observe(MACRO_NORM_PREFIX + profileId).map { values -> MacroNorm.decode(values.firstOrNull()) }

    suspend fun setMacroNorm(profileId: Long, norm: MacroNorm) {
        dao.put(AppSettingEntity(MACRO_NORM_PREFIX + profileId, MacroNorm.encode(norm)))
    }

    /** Back to the norms the calorie target implies. */
    suspend fun clearMacroNorm(profileId: Long) = dao.remove(MACRO_NORM_PREFIX + profileId)

    companion object {
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val TDEE_AUTO_APPLY_PREFIX = "tdee_auto_apply_"
        const val KEY_STARTER_PACK_VERSION = "starter_pack_version"
        const val MACRO_NORM_PREFIX = "macro_norm_"
    }
}
