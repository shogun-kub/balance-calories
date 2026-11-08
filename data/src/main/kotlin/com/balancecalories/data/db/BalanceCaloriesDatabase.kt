package com.balancecalories.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.balancecalories.data.db.dao.BodyMeasurementDao
import com.balancecalories.data.db.dao.DailyActivityDao
import com.balancecalories.data.db.dao.DebugDao
import com.balancecalories.data.db.dao.FoodEntryDao
import com.balancecalories.data.db.dao.PendingLookupDao
import com.balancecalories.data.db.dao.ProductDao
import com.balancecalories.data.db.dao.ProfileDao
import com.balancecalories.data.db.dao.RecipeDao
import com.balancecalories.data.db.dao.SettingsDao
import com.balancecalories.data.db.dao.TargetHistoryDao
import com.balancecalories.data.db.entity.AppSettingEntity
import com.balancecalories.data.db.entity.BodyMeasurementEntity
import com.balancecalories.data.db.entity.DailyActivityEntity
import com.balancecalories.data.db.entity.FoodEntryEntity
import com.balancecalories.data.db.entity.PendingLookupEntity
import com.balancecalories.data.db.entity.ProductEntity
import com.balancecalories.data.db.entity.ProductFtsEntity
import com.balancecalories.data.db.entity.ProfileEntity
import com.balancecalories.data.db.entity.RecipeEntity
import com.balancecalories.data.db.entity.RecipeItemEntity
import com.balancecalories.data.db.entity.SyncStateEntity
import com.balancecalories.data.db.entity.TargetHistoryEntity

/**
 * Schema v1, matching docs/03-architecture.md §3 column for column.
 *
 * Deliberate deviations from the hand-written SQL, all forced by Room:
 * - `CHECK (sex IN ('male','female'))` is not expressible; the value is validated by [Converters].
 * - The partial index `... WHERE deleted_at IS NULL` becomes a plain composite index. Room has no
 *   partial-index support; the query planner cost is negligible at one-person data volumes.
 * - `product_fts` is declared with `@Fts4(contentEntity = …)`, so Room owns the sync triggers that the
 *   hand-written schema would have needed explicitly.
 *
 * Versions are append-only. Any change here needs a migration and a `MigrationTestHelper` test.
 */
@Database(
    entities = [
        ProfileEntity::class,
        BodyMeasurementEntity::class,
        DailyActivityEntity::class,
        ProductEntity::class,
        ProductFtsEntity::class,
        RecipeEntity::class,
        RecipeItemEntity::class,
        FoodEntryEntity::class,
        TargetHistoryEntity::class,
        PendingLookupEntity::class,
        SyncStateEntity::class,
        AppSettingEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BalanceCaloriesDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun targetHistoryDao(): TargetHistoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun debugDao(): DebugDao
    abstract fun productDao(): ProductDao
    abstract fun pendingLookupDao(): PendingLookupDao
    abstract fun foodEntryDao(): FoodEntryDao

    abstract fun recipeDao(): RecipeDao

    companion object {
        const val NAME = "balancecalories.db"
    }
}
