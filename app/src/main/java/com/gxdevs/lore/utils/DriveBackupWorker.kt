package com.gxdevs.lore.utils

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker that:
 *  1. Acquires a Drive access token via [DriveTokenHelper.getAccessToken]
 *  2. Exports a fresh .lore backup via [BackupManager.exportData] to a temp file
 *  3. Uploads the temp file to Drive via [DriveBackupClient]
 *  4. Updates [SettingsRepository.gdriveLastSynced] with the current timestamp
 *
 * Scheduled in two ways from MainActivity:
 *  - PeriodicWorkRequest: once daily at ~midnight (KEEP policy, won't duplicate)
 *  - OneTimeWorkRequest: enqueued immediately when user turns on backup or taps Sync Now
 *
 * IMPORTANT: If Drive scope has not been consented ([DriveTokenHelper.NeedsAuthorizationException]),
 * this worker returns Result.failure() — the user must open the app and use the
 * "Sync Now" button in IdentityScreen to trigger the foreground consent dialog via
 * DriveTokenHelper.authorizeInForeground().
 */
class DriveBackupWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_TAG = "lore_drive_backup"
        const val WORK_NAME_PERIODIC = "lore_drive_backup_daily"
        const val WORK_NAME_ONDEMAND = "lore_drive_backup_ondemand"
        const val TAG_MANUAL_SYNC = "lore_drive_manual_sync"

        /**
         * Triggered automatically whenever a journal entry is created, edited, sealed, or deleted.
         * Sets pending changes to true, and if Drive backup is enabled & user is Pro,
         * enqueues a debounced on-demand backup (10s delay).
         */
        fun scheduleBackupOnDataChange(context: Context) {
            val appContext = context.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val repo = SettingsRepository(appContext)
                    repo.notifyDataChanged()
                    val isPremium = repo.isPremiumUnlocked.first()
                    val isBackupEnabled = repo.gdriveBackupEnabled.first()
                    if (isPremium && isBackupEnabled) {
                        val constraints = androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                        val request = androidx.work.OneTimeWorkRequestBuilder<DriveBackupWorker>()
                            .setConstraints(constraints)
                            .setInitialDelay(10, java.util.concurrent.TimeUnit.SECONDS)
                            .addTag(WORK_TAG)
                            .build()
                        androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                            WORK_NAME_ONDEMAND,
                            androidx.work.ExistingWorkPolicy.REPLACE,
                            request
                        )
                        android.util.Log.d(WORK_TAG, "Enqueued on-demand Drive backup for new/edited journal.")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(WORK_TAG, "Failed to schedule on-demand backup on data change", e)
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        val repo = SettingsRepository(appContext)

        // Guard: only run if premium AND backup is enabled
        val isPremium = repo.isPremiumUnlocked.first()
        val isBackupEnabled = repo.gdriveBackupEnabled.first()
        if (!isPremium || !isBackupEnabled) {
            android.util.Log.d(WORK_TAG, "Skipping backup - isPremium=$isPremium, isBackupEnabled=$isBackupEnabled")
            return Result.success()
        }

        // Guard: do not run daily backup if there are no edits or new journals since last sync (unless user tapped Sync Now)
        val isManualSync = tags.contains(TAG_MANUAL_SYNC)
        val hasPendingChanges = repo.gdriveHasPendingChanges.first()
        if (!hasPendingChanges && !isManualSync) {
            android.util.Log.d(WORK_TAG, "Skipping Drive backup — no edits or new journals since last sync.")
            return Result.success()
        }

        android.util.Log.d(WORK_TAG, "Starting Drive backup.")

        // Show ongoing Foreground Service notification so swiping away the app won't interrupt backup
        try {
            val notification = buildDriveBackupNotification(appContext, "Google Drive Sync", "Backing up your Sanctuary to Google Drive...", -1)
            val fgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.work.ForegroundInfo(
                    NOTIF_DRIVE_BACKUP_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                androidx.work.ForegroundInfo(NOTIF_DRIVE_BACKUP_ID, notification)
            }
            setForeground(fgInfo)
        } catch (e: Exception) {
            android.util.Log.w(WORK_TAG, "Could not set foreground service info: ${e.message}")
            showDriveBackupNotification(appContext, "Google Drive Sync", "Backing up your Sanctuary to Google Drive...", -1)
        }

        // 1. Get Drive access token (background — no UI shown)
        val tokenResult = DriveTokenHelper.getAccessToken(appContext)
        if (tokenResult.isFailure) {
            val exception = tokenResult.exceptionOrNull()
            android.util.Log.e(WORK_TAG, "Token acquisition failed: ${exception?.message}")
            cancelDriveBackupNotification(appContext)
            return when (exception) {
                is DriveTokenHelper.NeedsAuthorizationException -> {
                    android.util.Log.w(WORK_TAG, "Drive scope not authorized — user action required")
                    Result.failure()
                }
                else -> Result.retry()
            }
        }
        val token = tokenResult.getOrThrow()

        // 2. Export to a temp file (reuse BackupManager, write to temp Uri)
        val tempFile = File(appContext.cacheDir, "drive_backup_${System.currentTimeMillis()}.lore")
        return try {
            // Export notification deferred

            val includeMedia = repo.gdriveIncludeMedia.first()
            val encryptMedia = repo.encryptMedia.first()
            val backupPin = repo.backupEncryptionKey.first() ?: repo.appPin.first()

            val exportResult = BackupManager.exportData(
                context = appContext,
                outputUri = Uri.fromFile(tempFile),
                includeMedia = includeMedia,
                encryptBackup = encryptMedia,
                backupPin = backupPin
            )

            if (exportResult.isFailure) {
                val err = exportResult.exceptionOrNull()?.message ?: "Export error"
                android.util.Log.e(WORK_TAG, "Export failed: $err")
                showDriveBackupFailedNotification(appContext, err)
                return Result.retry()
            }

            val fileBytes = tempFile.readBytes()
            android.util.Log.d(WORK_TAG, "Backup exported: ${fileBytes.size} bytes, uploading to Drive.")
            showDriveBackupNotification(appContext, "Google Drive Sync", "Uploading backup to Google Drive (${fileBytes.size / 1024} KB)...", 75)

            // 3. Upload to Drive
            val uploadResult = DriveBackupClient.uploadBackup(token, fileBytes, appContext)
            if (uploadResult.isFailure) {
                val msg = uploadResult.exceptionOrNull()?.message ?: "Upload error"
                if (msg.contains("401")) {
                    android.util.Log.w(WORK_TAG, "Drive upload got 401 — access token may have expired")
                }
                android.util.Log.e(WORK_TAG, "Upload failed: $msg")
                showDriveBackupFailedNotification(appContext, msg)
                return Result.retry()
            }

            // 4. Update last-synced timestamp in DataStore & clear pending changes flag
            val timeStr = SimpleDateFormat("MMM dd, yyyy | h:mm a", Locale.getDefault()).format(Date())
            repo.markGdriveSyncComplete(timeStr)
            showDriveBackupSuccessNotification(appContext, timeStr)

            android.util.Log.d(WORK_TAG, "Drive backup complete at $timeStr")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(WORK_TAG, "Unexpected error: ${e.message}", e)
            showDriveBackupFailedNotification(appContext, e.message ?: "Unexpected error")
            Result.retry()
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
