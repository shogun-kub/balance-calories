package com.balancecalories.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.balancecalories.app.BalanceCaloriesApp
import com.balancecalories.data.repo.DailyActivityRepository
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Reads band activity from Health Connect and upserts the day rows.
 *
 * Runs both periodically and on demand, and it is the same code in both cases so a background day and a
 * tapped "Sync band" day cannot disagree.
 *
 * Failure policy is deliberate:
 * - a denied permission returns `success`, because retrying cannot grant it — the UI asks instead, and a
 *   worker that keeps retrying a permanent refusal only burns battery and shows up as a "failing app";
 * - a missing Health Connect store returns `success` for the same reason (research decision D9: no-band mode
 *   is supported, not an error);
 * - a genuine read failure returns `retry`, because that one may fix itself.
 */
class ActivitySyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BalanceCaloriesApp ?: return Result.success()
        val container = app.container
        if (!container.healthConnectAvailable) return Result.success()

        val profileId = container.settings.activeProfileId() ?: return Result.success()
        val profile = container.profiles.byId(profileId) ?: return Result.success()
        // Only the current person's band is read; two profiles on one phone must not share a burn figure.
        if (!profile.bandEnabled) return Result.success()

        val result = container.activity.sync(
            profile = profile,
            days = DailyActivityRepository.DEFAULT_INCREMENTAL_DAYS,
            zone = ZoneId.systemDefault(),
        )
        return when (result) {
            is DailyActivityRepository.SyncResult.Synced -> Result.success()
            DailyActivityRepository.SyncResult.NoPermission -> Result.success()
            DailyActivityRepository.SyncResult.Unavailable -> Result.success()
            is DailyActivityRepository.SyncResult.Failed -> Result.retry()
        }
    }

    companion object {

        private const val PERIODIC_WORK = "activity_sync_periodic"
        private const val ON_DEMAND_WORK = "activity_sync_now"

        /** Sync every three hours: often enough that "burned so far" is not stale, rare enough for the Redmi. */
        private const val PERIOD_HOURS = 3L

        /**
         * Schedules the periodic sync once. `KEEP` so an app start does not reset the interval, which would let a
         * frequently opened app never run it.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ActivitySyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * One expedited attempt when the app opens, so the screen is not showing yesterday's numbers while the
         * periodic work waits for its window. Falls back to a normal run if the quota is exhausted.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ActivitySyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ON_DEMAND_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
