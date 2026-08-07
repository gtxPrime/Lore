package com.gxdevs.lore.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play Store Forced Immediate In-App Update Helper.
 * Checks for updates on app launch and forces the IMMEDIATE update flow
 * whenever an update is available on Google Play.
 */
class AppUpdateHelper(context: Context) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context.applicationContext)

    /**
     * Checks Google Play on startup and forces an IMMEDIATE update if available.
     */
    fun checkForUpdateOnStart(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                Log.i(TAG, "Update available on Play Store. Forcing immediate update flow...")
                startImmediateUpdate(activity, appUpdateInfo, launcher)
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "Google Play update check unavailable: ${e.message}")
        }
    }

    /**
     * Re-checks on activity resume. If an update flow is in progress or an update is ready,
     * forces the user back into the immediate update flow.
     */
    fun onResumeCheck(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                || (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
            ) {
                Log.i(TAG, "Resuming forced immediate update flow...")
                startImmediateUpdate(activity, appUpdateInfo, launcher)
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "onResume Play update check error: ${e.message}")
        }
    }

    private fun startImmediateUpdate(
        activity: Activity,
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                launcher,
                AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch immediate update flow", e)
        }
    }

    companion object {
        private const val TAG = "AppUpdateHelper"
    }
}
