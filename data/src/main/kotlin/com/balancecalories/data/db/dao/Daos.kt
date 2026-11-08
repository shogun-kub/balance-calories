package com.balancecalories.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.balancecalories.data.db.entity.BodyMeasurementEntity
import com.balancecalories.data.db.entity.DailyActivityEntity
import com.balancecalories.data.db.entity.ProfileEntity
import com.balancecalories.data.db.entity.TargetHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?

    @Query("SELECT * FROM profile WHERE archived_at IS NULL ORDER BY created_at")
    fun observeActive(): Flow<List<ProfileEntity>>

    /**
     * Used to enforce "band data belongs to the phone": at most one active profile may read Health
     * Connect. Reading the list rather than relying on SQL keeps the rule testable.
     */
    @Query("SELECT * FROM profile WHERE band_enabled = 1 AND archived_at IS NULL")
    suspend fun bandEnabledProfiles(): List<ProfileEntity>

    @Query("UPDATE profile SET band_enabled = 0 WHERE id != :keepId")
    suspend fun clearBandExcept(keepId: Long)

    @Query("UPDATE profile SET band_enabled = :enabled WHERE id = :id")
    suspend fun setBandEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE profile SET archived_at = :at WHERE id = :id")
    suspend fun archive(id: Long, at: Long)

    /** Delete a profile and, by cascade, everything that belongs to it. */
    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BodyMeasurementDao {

    /** Re-reading the same Health Connect record must not create a duplicate row. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(measurement: BodyMeasurementEntity)

    @Query("SELECT * FROM body_measurement WHERE profile_id = :profileId ORDER BY measured_at DESC LIMIT 1")
    suspend fun latest(profileId: Long): BodyMeasurementEntity?

    @Query(
        "SELECT * FROM body_measurement WHERE profile_id = :profileId AND source = :source " +
            "ORDER BY measured_at DESC LIMIT 1",
    )
    suspend fun latestFrom(profileId: Long, source: String): BodyMeasurementEntity?

    @Query(
        "SELECT * FROM body_measurement WHERE profile_id = :profileId " +
            "AND local_date BETWEEN :from AND :to ORDER BY measured_at",
    )
    fun observeBetween(profileId: Long, from: String, to: String): Flow<List<BodyMeasurementEntity>>

    @Query(
        "SELECT weight_kg FROM body_measurement WHERE profile_id = :profileId " +
            "AND weight_kg IS NOT NULL ORDER BY measured_at DESC LIMIT 1",
    )
    suspend fun latestWeightKg(profileId: Long): Double?
}

@Dao
interface DailyActivityDao {

    @Upsert
    suspend fun upsert(activity: DailyActivityEntity)

    @Query("SELECT * FROM daily_activity WHERE profile_id = :profileId AND local_date = :localDate")
    suspend fun forDay(profileId: Long, localDate: String): DailyActivityEntity?

    @Query(
        "SELECT * FROM daily_activity WHERE profile_id = :profileId " +
            "AND local_date BETWEEN :from AND :to ORDER BY local_date",
    )
    fun observeBetween(profileId: Long, from: String, to: String): Flow<List<DailyActivityEntity>>

    /** Days without band data, which must be excluded from an adaptive TDEE later. */
    @Query("SELECT COUNT(*) FROM daily_activity WHERE profile_id = :profileId AND quality = :quality")
    suspend fun countWithQuality(profileId: Long, quality: String): Int
}

@Dao
interface TargetHistoryDao {

    @Upsert
    suspend fun upsert(target: TargetHistoryEntity)

    /** The target that was in force on a given local date; null if the profile has never had one. */
    @Query(
        "SELECT * FROM target_history WHERE profile_id = :profileId " +
            "AND effective_from <= :localDate ORDER BY effective_from DESC LIMIT 1",
    )
    suspend fun effectiveOn(profileId: Long, localDate: String): TargetHistoryEntity?
}
