package com.gxdevs.lore.utils

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Acquires a Google Drive `appdata` scoped OAuth2 access token using the
 * Google account email already stored in [SettingsRepository].
 *
 * Requires:
 *  - Google Drive API enabled in Cloud Console
 *  - `drive.appdata` scope added to the OAuth consent screen
 *  - User signed in via Google (email stored in DataStore)
 *
 * Token is cached by Play Services for ~1 hour; subsequent calls return
 * instantly without a network round-trip.
 */
object DriveTokenHelper {

    private const val DRIVE_APPDATA_SCOPE =
        "oauth2:https://www.googleapis.com/auth/drive.appdata"

    suspend fun getAccessToken(context: Context): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val repo = SettingsRepository(context)
                val email = repo.googleAccountEmail.first()
                    ?: return@withContext Result.failure(
                        IllegalStateException("No Google account signed in")
                    )
                val account = Account(email, "com.google")
                val token = GoogleAuthUtil.getToken(
                    context.applicationContext,
                    account,
                    DRIVE_APPDATA_SCOPE
                )
                Result.success(token)
            } catch (e: UserRecoverableAuthException) {
                android.util.Log.w("DriveToken", "User action required to grant Drive scope: ${e.message}")
                Result.failure(e)
            } catch (e: Exception) {
                android.util.Log.e("DriveToken", "Failed to get Drive token: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun invalidateToken(context: Context) = withContext(Dispatchers.IO) {
        try {
            val repo = SettingsRepository(context)
            val email = repo.googleAccountEmail.first() ?: return@withContext
            val account = Account(email, "com.google")
            val staleToken = try {
                GoogleAuthUtil.getToken(context.applicationContext, account, DRIVE_APPDATA_SCOPE)
            } catch (_: Exception) { return@withContext }
            GoogleAuthUtil.clearToken(context.applicationContext, staleToken)
        } catch (_: Exception) {}
    }
}
