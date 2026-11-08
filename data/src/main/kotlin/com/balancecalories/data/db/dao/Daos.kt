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

    /** Every profile, archived ones included; the profiles screen needs both lists. */
    @Query("SELECT * FROM profile ORDER BY created_at")
    fun observeAll(): Flow<List<ProfileEntity>>

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

    @Query("UPDATE profile SET archived_at = NULL WHERE id = :id")
    suspend fun unarchive(id: Long)

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

    @Query(
        "SELECT * FROM target_history WHERE profile_id = :profileId " +
            "ORDER BY effective_from DESC LIMIT 1",
    )
    suspend fun latest(profileId: Long): TargetHistoryEntity?
}

/** Read-only counts for the debug screen, so a wrong-looking number can be traced to a table. */
@Dao
interface DebugDao {
    @Query("SELECT COUNT(*) FROM profile")
    suspend fun profileCount(): Int

    @Query("SELECT COUNT(*) FROM body_measurement")
    suspend fun bodyMeasurementCount(): Int

    @Query("SELECT COUNT(*) FROM daily_activity")
    suspend fun dailyActivityCount(): Int

    @Query("SELECT COUNT(*) FROM food_entry WHERE deleted_at IS NULL")
    suspend fun foodEntryCount(): Int

    @Query("SELECT COUNT(*) FROM target_history")
    suspend fun targetHistoryCount(): Int

    @Query("SELECT COUNT(*) FROM product")
    suspend fun productCount(): Int

    @Query("SELECT COUNT(*) FROM recipe")
    suspend fun recipeCount(): Int

    @Query("SELECT COUNT(*) FROM app_setting")
    suspend fun appSettingCount(): Int
}

/** Products: the starter pack today, OpenFoodFacts and user products later. */
@Dao
interface ProductDao {

    /** IGNORE keeps a re-import from duplicating rows even if the version guard is ever bypassed. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(products: List<com.balancecalories.data.db.entity.ProductEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM product")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM product WHERE source = :source")
    suspend fun countFromSource(source: String): Int

    @Query("SELECT * FROM product WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): com.balancecalories.data.db.entity.ProductEntity?

    @Query("SELECT * FROM product WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): com.balancecalories.data.db.entity.ProductEntity?

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun byId(id: Long): com.balancecalories.data.db.entity.ProductEntity?

    @Insert
    suspend fun insert(product: com.balancecalories.data.db.entity.ProductEntity): Long

    /** Updates by primary key, so a refresh keeps the row id that entries and recipes point at. */
    @Update
    suspend fun update(product: com.balancecalories.data.db.entity.ProductEntity)

    @Query(
        "SELECT * FROM product WHERE name LIKE '%' || :query || '%' " +
            "OR name_ru LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' " +
            "ORDER BY name LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int): List<com.balancecalories.data.db.entity.ProductEntity>
}

/**
 * Barcodes that could not be resolved.
 *
 * One table serves two purposes, separated by [MISS_MARKER] in `last_error`: a row with the marker is a
 * **cached miss** ("OpenFoodFacts does not have this product"), anything else is an **offline queue** entry
 * to retry when connectivity returns. They share a life cycle — both are cleared once the barcode resolves —
 * so a second table would only add a way for them to disagree.
 */
@Dao
interface PendingLookupDao {

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun put(row: com.balancecalories.data.db.entity.PendingLookupEntity)

    @Query("SELECT * FROM pending_lookup WHERE barcode = :barcode")
    suspend fun byBarcode(barcode: String): com.balancecalories.data.db.entity.PendingLookupEntity?

    @Query(
        "SELECT * FROM pending_lookup WHERE last_error IS NULL OR last_error != :missMarker " +
            "ORDER BY created_at LIMIT :limit",
    )
    suspend fun retryable(missMarker: String, limit: Int): List<com.balancecalories.data.db.entity.PendingLookupEntity>

    @Query("SELECT * FROM pending_lookup ORDER BY created_at")
    suspend fun all(): List<com.balancecalories.data.db.entity.PendingLookupEntity>

    @Query("DELETE FROM pending_lookup WHERE barcode = :barcode")
    suspend fun delete(barcode: String)

    @Query("SELECT COUNT(*) FROM pending_lookup")
    suspend fun count(): Int

    companion object {
        /** Stored in `last_error` to mark "the server says this product does not exist". */
        const val MISS_MARKER = "not_found"
    }
}

/** App-level key-value state (schema v2), such as which profile is currently shown. */
@Dao
interface SettingsDao {

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun value(key: String): String?

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    fun observe(key: String): Flow<List<String>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun put(setting: com.balancecalories.data.db.entity.AppSettingEntity)

    @Query("DELETE FROM app_setting WHERE `key` = :key")
    suspend fun remove(key: String)
}
