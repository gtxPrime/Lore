package com.gxdevs.lore.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Acquires a Google Drive `appdata` scoped OAuth2 access token using the
 * modern [Identity.getAuthorizationClient] API (replaces deprecated GoogleAuthUtil).
 *
 * This API correctly handles Play App Signing SHA-1 fingerprints and works
 * with all release variants published to the Play Store.
 *
 * Two modes of operation:
 *  1. [getAccessToken] — for background workers (WorkManager). Returns a token
 *     only if the scope is already consented. If consent is missing, returns
 *     [NeedsAuthorizationException] so the caller can notify the user.
 *  2. [authorizeInForeground] — for UI flows (Activity). Calls authorize() and
 *     if a PendingIntent is returned (consent needed), launches it via the
 *     provided activity so the user can grant access.
 *
 * Requires:
 *  - Google Drive API enabled in Google Cloud Console
 *  - `drive.appdata` scope added to the OAuth consent screen
 *  - User signed in via Google (email stored in DataStore)
 */
object DriveTokenHelper {

    private const val TAG = "DriveTokenHelper"

    /** Thrown by [getAccessToken] when the user hasn't yet granted Drive access. */
    class NeedsAuthorizationException(message: String) : Exception(message)

    private val DRIVE_APPDATA_SCOPE = Scope("https://www.googleapis.com/auth/drive.appdata")

    // ─── Background token retrieval ───────────────────────────────────────────

    /**
     * Attempts to retrieve an existing Drive access token **without** showing any UI.
     *
     * Use this from WorkManager / background contexts. If the user hasn't granted
     * the drive.appdata scope yet, returns [NeedsAuthorizationException] — the
     * caller should post a notification directing the user to open the app and
     * trigger [authorizeInForeground] via the IdentityScreen.
     */
    suspend fun getAccessToken(context: Context): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Requesting Drive authorization token (background)")

                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(DRIVE_APPDATA_SCOPE))
                    .build()

                val authClient = Identity.getAuthorizationClient(context.applicationContext)
                val authResult = Tasks.await(authClient.authorize(authRequest))

                val accessToken = authResult.accessToken
                if (!accessToken.isNullOrBlank()) {
                    Log.d(TAG, "Drive access token retrieved successfully (background)")
                    return@withContext Result.success(accessToken)
                }

                // AuthorizationResult requires UI interaction (pendingIntent != null)
                if (authResult.hasResolution()) {
                    Log.w(TAG, "Drive scope requires UI consent — returning NeedsAuthorizationException")
                    return@withContext Result.failure(
                        NeedsAuthorizationException(
                            "Drive authorization requires user consent. Please open the app and grant Drive access."
                        )
                    )
                }

                // Token was null but no resolution needed — unexpected state
                Log.e(TAG, "AuthorizationResult had null token and no resolution")
                Result.failure(IllegalStateException("Drive authorization returned null token"))

            } catch (e: NeedsAuthorizationException) {
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "getAccessToken failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ─── Foreground authorization (Activity context) ──────────────────────────

    /**
     * Requests Drive authorization in the foreground. Call this from an Activity
     * when [getAccessToken] returns [NeedsAuthorizationException] or when the
     * user explicitly triggers a backup for the first time.
     *
     * @return [DriveAuthState.HasToken] if already authorized (token available immediately),
     *         [DriveAuthState.NeedsConsent] if the user must approve via [pendingIntent],
     *         [DriveAuthState.Failed] on error.
     */
    suspend fun authorizeInForeground(context: Context): DriveAuthState =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Requesting Drive authorization (foreground)")

                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(DRIVE_APPDATA_SCOPE))
                    .build()

                val authClient = Identity.getAuthorizationClient(context.applicationContext)
                val authResult = Tasks.await(authClient.authorize(authRequest))

                val accessToken = authResult.accessToken
                if (!accessToken.isNullOrBlank()) {
                    Log.d(TAG, "Drive access token available immediately — no consent dialog needed")
                    return@withContext DriveAuthState.HasToken(accessToken)
                }

                if (authResult.hasResolution()) {
                    Log.d(TAG, "Drive consent dialog required — returning PendingIntent to caller")
                    return@withContext DriveAuthState.NeedsConsent(authResult.pendingIntent!!)
                }

                DriveAuthState.Failed("Drive authorization returned null token with no resolution")

            } catch (e: Exception) {
                Log.e(TAG, "authorizeInForeground failed: ${e.message}", e)
                DriveAuthState.Failed(e.message ?: "Unknown Drive authorization error")
            }
        }

    /**
     * Represents the state of a foreground Drive authorization attempt.
     */
    sealed class DriveAuthState {
        /** Drive scope is already authorized — [token] can be used immediately. */
        data class HasToken(val token: String) : DriveAuthState()

        /** User consent dialog is needed — launch [pendingIntent] from an Activity. */
        data class NeedsConsent(val pendingIntent: android.app.PendingIntent) : DriveAuthState()

        /** Authorization failed with [reason]. */
        data class Failed(val reason: String) : DriveAuthState()
    }

    // ─── Post-consent token retrieval ─────────────────────────────────────────

    /**
     * Called after the user grants consent via the Drive authorization dialog.
     * Extracts the access token from the authorization result Intent returned by
     * [Activity.onActivityResult] or [ActivityResultLauncher].
     *
     * @param context Application context.
     * @param data    The Intent data from the authorization result.
     * @return The access token, or null if extraction failed.
     */
    fun getTokenFromAuthorizationResult(context: Context, data: android.content.Intent?): String? {
        return try {
            val authClient = Identity.getAuthorizationClient(context.applicationContext)
            val authResult = authClient.getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                Log.d(TAG, "Drive token extracted from authorization result intent")
                token
            } else {
                Log.w(TAG, "Authorization result intent had null/blank token")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract token from authorization result: ${e.message}", e)
            null
        }
    }

    /**
     * Clears a cached OAuth token from Google Play Services local cache.
     * Useful when a token returns HTTP 403 or invalid project error.
     */
    fun clearToken(context: Context, token: String) {
        try {
            com.google.android.gms.auth.GoogleAuthUtil.clearToken(context.applicationContext, token)
            Log.d(TAG, "Cleared cached OAuth token from Google Play Services")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear token: ${e.message}")
        }
    }
}
