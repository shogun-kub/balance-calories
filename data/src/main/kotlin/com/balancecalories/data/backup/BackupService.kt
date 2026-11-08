package com.balancecalories.data.backup

import androidx.room.RoomDatabase
import com.balancecalories.data.db.BalanceCaloriesDatabase

/**
 * The backup as the app uses it: three calls, no SQL and no Room types leaving this module.
 *
 * All three do real database work and are therefore **blocking**; the caller wraps them in `Dispatchers.IO`. They
 * are not `suspend` because a backup is one long transaction-shaped operation rather than something that should
 * interleave with UI work — suspending halfway would only add places to be cancelled past the point of no return.
 */
class BackupService(private val database: BalanceCaloriesDatabase) {

    /** The schema version of the database that is open, which is the version this backup really has. */
    val schemaVersion: Int get() = database.openHelper.readableDatabase.version

    fun writeJson(appVersion: String, exportedAt: Long = System.currentTimeMillis()): String =
        BackupWriter.write(
            db = database.openHelper.writableDatabase,
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            exportedAt = exportedAt,
        )

    fun restore(json: String): ImportOutcome =
        BackupReader.restore(
            db = database.openHelper.writableDatabase,
            json = json,
            currentSchemaVersion = schemaVersion,
        )

    /**
     * The daily report. [bmrKcal] and [palKcal] come from `TargetCalculator` in the caller, so the CSV shows the
     * same burn the screen shows instead of a second implementation of the same precedence rule.
     */
    fun buildDaysCsv(profileId: Long, bmrKcal: Double, palKcal: Double): String =
        DaysCsv.build(
            db = database.openHelper.writableDatabase,
            profileId = profileId,
        ) { _, activity ->
            val hasEnergy = activity != null && (activity.totalKcal != null || activity.activeKcal != null)
            when {
                // A day whose writer is unknown is the platform baseline, not a measurement: the Honor's 1858.25
                // kcal days have no origin at all, and calling those measured would break the product rule.
                hasEnergy && activity!!.hasOrigins -> DaysCsv.Burn(
                    kcal = activity.totalKcal ?: (bmrKcal + activity.activeKcal!!),
                    isMeasured = true,
                )
                else -> DaysCsv.Burn(kcal = palKcal, isMeasured = false)
            }
        }
}
