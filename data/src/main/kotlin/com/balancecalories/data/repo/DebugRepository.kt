package com.balancecalories.data.repo

import com.balancecalories.data.db.BalanceCaloriesDatabase

/** Row counts and the installed schema version, for the debug screen. */
data class DatabaseSnapshot(
    val schemaVersion: Int,
    val profiles: Int,
    val weights: Int,
    val activityDays: Int,
    val foodEntries: Int,
    val targets: Int,
    val products: Int,
    val recipes: Int,
    val appSettings: Int,
)

/**
 * Reads the shape of the database without exposing Room to `:app`. When a number on screen looks wrong,
 * this is the difference between "the maths is off" and "the row was never written".
 */
class DebugRepository(private val database: BalanceCaloriesDatabase) {

    suspend fun snapshot(): DatabaseSnapshot {
        val dao = database.debugDao()
        return DatabaseSnapshot(
            schemaVersion = database.openHelper.readableDatabase.version,
            profiles = dao.profileCount(),
            weights = dao.bodyMeasurementCount(),
            activityDays = dao.dailyActivityCount(),
            foodEntries = dao.foodEntryCount(),
            targets = dao.targetHistoryCount(),
            products = dao.productCount(),
            recipes = dao.recipeCount(),
            appSettings = dao.appSettingCount(),
        )
    }
}
