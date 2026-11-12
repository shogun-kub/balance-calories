package com.balancecalories.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.balancecalories.data.db.entity.MealEntity
import kotlinx.coroutines.flow.Flow

/** The meal slots of a profile. See [MealEntity] for why the key and not the name is the identity. */
@Dao
interface MealDao {

    @Query("SELECT * FROM meal WHERE profile_id = :profileId ORDER BY sort_order, meal_key")
    suspend fun slots(profileId: Long): List<MealEntity>

    @Query("SELECT * FROM meal WHERE profile_id = :profileId ORDER BY sort_order, meal_key")
    fun observeSlots(profileId: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal WHERE profile_id = :profileId AND meal_key = :key")
    suspend fun slot(profileId: Long, key: String): MealEntity?

    @Query("SELECT COUNT(*) FROM meal WHERE profile_id = :profileId")
    suspend fun count(profileId: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM meal WHERE profile_id = :profileId")
    suspend fun nextSortOrder(profileId: Long): Int

    /** Entries still pointing at a slot, which is what makes deleting it destructive. */
    @Query(
        "SELECT COUNT(*) FROM food_entry " +
            "WHERE profile_id = :profileId AND meal = :key AND deleted_at IS NULL",
    )
    suspend fun entryCount(profileId: Long, key: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(slot: MealEntity)

    @Query("DELETE FROM meal WHERE profile_id = :profileId AND meal_key = :key")
    suspend fun remove(profileId: Long, key: String)

    @Query("UPDATE meal SET sort_order = :order WHERE profile_id = :profileId AND meal_key = :key")
    suspend fun setSortOrder(profileId: Long, key: String, order: Int)
}
