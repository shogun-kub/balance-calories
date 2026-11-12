package com.balancecalories.data.repo

import com.balancecalories.core.food.MealDefaults
import com.balancecalories.core.food.MealSlot
import com.balancecalories.core.food.MealTimes
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.MealEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The meal slots of a profile: the set a day is grouped into.
 *
 * Three rules, all of them the owner's ask of 2026-11-12:
 *
 * 1. **The set is data.** A profile starts from [MealDefaults] and the person may add, rename, retime and delete
 *    from there. Breakfast, lunch and dinner are a default, not a contract.
 * 2. **The snack bucket is fixed.** It is where unplanned food goes, so it can be neither renamed nor deleted.
 * 3. **A slot with entries is not deleted.** Removing it would either orphan rows or silently move somebody's
 *    lunch into another meal; instead the delete is refused and the caller says how many entries stand in the way.
 *
 * Identity is the key, never the name: renaming leaves `food_entry.meal` alone (see [MealEntity]).
 */
class MealRepository(private val database: BalanceCaloriesDatabase) {

    private val meals = database.mealDao()
    private val settings = database.settingsDao()

    suspend fun slots(profileId: Long): List<MealSlot> {
        ensureDefaults(profileId)
        return meals.slots(profileId).map { it.toDomain() }
    }

    fun observeSlots(profileId: Long): Flow<List<MealSlot>> =
        meals.observeSlots(profileId).map { rows -> rows.map { it.toDomain() } }

    /**
     * Seeds the default set for a profile that has none.
     *
     * Called on every read rather than at profile creation: a profile created before schema v7 - or one whose
     * rows were removed by a restore - would otherwise show an empty day with nowhere to log food into.
     */
    suspend fun ensureDefaults(profileId: Long) {
        if (meals.count(profileId) > 0) return
        val stored = MealTimes.decode(settings.value(MEAL_TIMES_PREFIX + profileId))
        val times = mapOf(
            MealDefaults.BREAKFAST to stored.breakfastMinutes,
            MealDefaults.LUNCH to stored.lunchMinutes,
            MealDefaults.DINNER to stored.dinnerMinutes,
        )
        MealDefaults.SLOTS.forEachIndexed { index, slot ->
            meals.put(
                MealEntity(
                    profileId = profileId,
                    key = slot.key,
                    name = "",
                    sortOrder = index,
                    plannedMinutes = times[slot.key] ?: slot.minutes,
                    fixed = slot.fixed,
                ),
            )
        }
    }

    /** Renames a slot. Refused for the fixed bucket: its name is how the app names unplanned food. */
    suspend fun rename(profileId: Long, key: String, name: String): Boolean {
        val slot = meals.slot(profileId, key) ?: return false
        if (slot.fixed) return false
        meals.put(slot.copy(name = name.trim()))
        return true
    }

    /** Sets or clears the planned time. The fixed bucket has no time and refuses one. */
    suspend fun setPlannedMinutes(profileId: Long, key: String, minutes: Int?): Boolean {
        val slot = meals.slot(profileId, key) ?: return false
        if (slot.fixed) return false
        if (minutes != null && minutes !in 0 until MealTimes.MINUTES_PER_DAY) return false
        meals.put(slot.copy(plannedMinutes = minutes))
        return true
    }

    /**
     * Adds a slot at the end of the day, before the fixed bucket.
     *
     * The snack bucket keeps its place last by moving its sort order, because that is where a bucket of
     * everything unplanned belongs; nothing about a fixed slot's identity changes when its order does.
     */
    suspend fun create(profileId: Long, name: String, minutes: Int?): MealSlot {
        ensureDefaults(profileId)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "a meal needs a name" }
        require(minutes == null || minutes in 0 until MealTimes.MINUTES_PER_DAY) { "time out of range: $minutes" }

        val existing = meals.slots(profileId)
        val order = (existing.maxOfOrNull { it.sortOrder } ?: -1)
        val key = generateKey(existing.map { it.key }.toSet())
        val fixedSlot = existing.firstOrNull { it.fixed }
        if (fixedSlot != null) {
            meals.setSortOrder(profileId, fixedSlot.key, order + 1)
        }
        val slot = MealEntity(
            profileId = profileId,
            key = key,
            name = trimmed,
            sortOrder = order,
            plannedMinutes = minutes,
            fixed = false,
        )
        meals.put(slot)
        return slot.toDomain()
    }

    /**
     * What happened to a delete request. The caller needs the difference: "not allowed" and "not empty" are two
     * different sentences to a person.
     */
    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        data object Fixed : DeleteResult
        data object LastMeal : DeleteResult
        data class HasEntries(val count: Int) : DeleteResult
    }

    suspend fun delete(profileId: Long, key: String): DeleteResult {
        val slot = meals.slot(profileId, key) ?: return DeleteResult.Deleted
        if (slot.fixed) return DeleteResult.Fixed
        if (meals.count(profileId) <= 1) return DeleteResult.LastMeal
        val entries = meals.entryCount(profileId, key)
        if (entries > 0) return DeleteResult.HasEntries(entries)
        meals.remove(profileId, key)
        return DeleteResult.Deleted
    }

    private fun generateKey(taken: Set<String>): String {
        var index = 1
        while ("custom_$index" in taken) index++
        return "custom_$index"
    }

    private fun MealEntity.toDomain(): MealSlot = MealSlot(
        key = key,
        name = name,
        sortOrder = sortOrder,
        plannedMinutes = plannedMinutes,
        fixed = fixed,
    )

    private companion object {
        const val MEAL_TIMES_PREFIX = "meal_times_"
    }
}
