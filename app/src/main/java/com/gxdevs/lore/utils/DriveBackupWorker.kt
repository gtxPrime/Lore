package com.gxdevs.lore.utils

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.UserRecoverableAuthException
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker that:
 *  1. Acquires a Drive access token via [DriveTokenHelper]
 *  2. Exports a fresh .lore backup via [BackupManager.exportData] to a temp file
 *  3. Uploads the temp file to Drive via [DriveBackupClient]
 *  4. Updates [SettingsRepository.gdriveLastSynced] with the current timestamp
 *
 * Scheduled in two ways from MainActivity:
 *  - PeriodicWorkRequest: once daily at ~midnight (KEEP policy, won't duplicate)
 *  - OneTimeWorkRequest: enqueued immediately when user turns on backup or taps Sync Now
 */
class DriveBackupWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_TAG = "lore_drive_backup"
        const val WORK_NAME_PERIODIC = "lore_drive_backup_daily"
        const val WORK_NAME_ONDEMAND = "lore_drive_backup_ondemand"
    }

    override suspend fun doWork(): Result {
        val repo = SettingsRepository(appContext)

        // Guard: only run if premium, signed in, AND backup is enabled
        val isPremium = repo.isPremiumUnlocked.first()
        val isLoggedIn = repo.googleLoggedIn.first()
        val isBackupEnabled = repo.gdriveBackupEnabled.first()
        if (!isPremium || !isLoggedIn || !isBackupEnabled) {
            android.util.Log.d(WORK_TAG, "Skipping backup — isPremium=$isPremium, isLoggedIn=$isLoggedIn, isBackupEnabled=$isBackupEnabled")
            return Result.success()
        }

        android.util.Log.d(WORK_TAG, "Starting Drive backup…")

        // 1. Get Drive access token
        val tokenResult = DriveTokenHelper.getAccessToken(appContext)
        if (tokenResult.isFailure) {
            val exception = tokenResult.exceptionOrNull()
            android.util.Log.e(WORK_TAG, "Token acquisition failed: ${exception?.message}")
            if (exception is UserRecoverableAuthException) {
                // Consent missing — user needs to grant Drive permission via UI consent screen first
                return Result.failure()
            }
            return Result.retry()
        }
        val token = tokenResult.getOrThrow()

        // 2. Export to a temp file (reuse BackupManager, write to temp Uri)
        val tempFile = File(appContext.cacheDir, "drive_backup_${System.currentTimeMillis()}.lore")
        return try {
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
                android.util.Log.e(WORK_TAG, "Export failed: ${exportResult.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val fileBytes = tempFile.readBytes()
            android.util.Log.d(WORK_TAG, "Backup exported: ${fileBytes.size} bytes, uploading to Drive…")

            // 3. Upload to Drive
            val uploadResult = DriveBackupClient.uploadBackup(token, fileBytes)
            if (uploadResult.isFailure) {
                // If we got a 401, invalidate the cached token and retry
                val msg = uploadResult.exceptionOrNull()?.message ?: ""
                if (msg.contains("401")) {
                    DriveTokenHelper.invalidateToken(appContext)
                }
                android.util.Log.e(WORK_TAG, "Upload failed: $msg")
                return Result.retry()
            }

            // 4. Update last-synced timestamp in DataStore
            val timeStr = SimpleDateFormat("MMM dd, yyyy | h:mm a", Locale.getDefault()).format(Date())
            repo.setGdriveLastSynced(timeStr)

            android.util.Log.d(WORK_TAG, "? Drive backup complete at $timeStr")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(WORK_TAG, "Unexpected error: ${e.message}", e)
            Result.retry()
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
