package com.gxdevs.athera.pets

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs the 24-hour pet catalog sync.
 *
 * Safety guarantees:
 *   - Only runs when the device has a network connection (CONNECTED constraint).
 *   - The [PetCatalogRepository.syncIfDue] call internally checks the 24h
 *     cooldown, so even if WorkManager fires early it is a no-op.
 *   - The worker does NOT run during an active journal session because
 *     it is scheduled as a periodic background task and the 24h interval
 *     means it will never be triggered mid-write in practice. Additionally,
 *     callers can use [PetSyncGuard] to block the worker while the journal
 *     screen is open.
 */
class PetCatalogSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "PetCatalogSyncWorker started")

        if (PetSyncGuard.isJournalingActive) {
            Log.i(TAG, "Journal session active â€” deferring sync")
            return Result.retry()
        }

        val repo = PetCatalogRepository(applicationContext)
        return when (val result = repo.syncIfDue()) {
            is PetCatalogRepository.SyncResult.Updated ->
                Result.success().also { Log.i(TAG, "Catalog updated to v${result.newVersion}") }
            is PetCatalogRepository.SyncResult.AlreadyCurrent ->
                Result.success().also { Log.d(TAG, "Catalog up-to-date v${result.version}") }
            is PetCatalogRepository.SyncResult.Skipped ->
                Result.success().also { Log.d(TAG, "Sync skipped (within 24h window)") }
            is PetCatalogRepository.SyncResult.NoConfig ->
                // RC key not published yet â€” not an error, skip and let next window try again
                Result.success().also { Log.w(TAG, "pets_json_url not configured in Remote Config") }
            is PetCatalogRepository.SyncResult.NoInternet ->
                // Retry with exponential backoff â€” WorkManager will reschedule
                Result.retry().also { Log.w(TAG, "No internet â€” will retry") }
            is PetCatalogRepository.SyncResult.ParseError ->
                Result.failure().also { Log.e(TAG, "Catalog JSON parse failed: ${result.message}") }
        }
    }

    companion object {
        private const val TAG = "PetCatalogSyncWorker"
        const val WORK_NAME = "pet_catalog_daily_sync"

        /**
         * Schedules (or re-schedules) the periodic background sync.
         * Safe to call multiple times â€” WorkManager deduplicates by [WORK_NAME].
         * Call this from Application.onCreate() or after first login.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PetCatalogSyncWorker>(
                24, TimeUnit.HOURS,
                // Flex window: run anytime in the last 6 hours of the 24h period
                6, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't reset the 24h timer on re-schedule
                request
            )

            Log.i(TAG, "Pet catalog sync scheduled (24h periodic)")
        }
    }
}

/**
 * Global guard that blocks the catalog sync worker from running while a
 * journal entry is being written.
 *
 * Usage:
 *   - Set [isJournalingActive] = true when the journal compose screen enters composition.
 *   - Set [isJournalingActive] = false when it leaves composition or on save.
 */
object PetSyncGuard {
    @Volatile var isJournalingActive: Boolean = false
}

