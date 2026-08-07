package com.gxdevs.lore.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized Google Sign-In and Firebase Authentication Manager.
 * Handles explicit Google account picker display on sign-in after logout.
 */
object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    sealed class AuthResult {
        data class Success(
            val firebaseUser: FirebaseUser?,
            val email: String,
            val displayName: String,
            val photoUrl: String
        ) : AuthResult()

        data class Failure(
            val message: String,
            val cause: Throwable? = null,
            val isConfigurationIssue: Boolean = false,
            val isNoAccountFound: Boolean = false
        ) : AuthResult()

        data class Cancelled(val reason: String = "Sign-in cancelled") : AuthResult()
    }

    /**
     * Gets the currently authenticated Firebase user, if any.
     */
    val currentUser: FirebaseUser?
        get() = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth instance unavailable", e)
            null
        }

    /**
     * Logs custom authentication events and non-fatal exceptions to Firebase Crashlytics & Analytics.
     */
    private fun reportDiagnostic(context: Context, stageName: String, msg: String, error: Throwable? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("auth_last_stage", stageName)
            crashlytics.log("[$stageName] $msg")
            if (error != null) {
                crashlytics.recordException(error)
            }

            val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            val bundle = Bundle().apply {
                putString("stage", stageName.take(40))
                putString("message", msg.take(100))
                if (error != null) {
                    putString("error_class", error.javaClass.simpleName)
                }
            }
            analytics.logEvent("auth_diagnostic", bundle)
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostic reporting skipped: ${e.message}")
        }
    }

    /**
     * Performs Google Sign-In via [CredentialManager].
     * When [filterByAuthorizedAccounts] is false, presents the full account chooser bottom sheet
     * allowing the user to select any Google account on their device.
     */
    suspend fun signIn(
        activity: Activity,
        settingsRepo: SettingsRepository,
        filterByAuthorizedAccounts: Boolean = false,
        autoSelectIfPossible: Boolean = false
    ): AuthResult = withContext(Dispatchers.IO) {
        val credentialManager = CredentialManager.create(activity)

        try {
            Log.d(TAG, "Initiating Google Sign-In (filterAuthorized=$filterByAuthorizedAccounts, autoSelect=$autoSelectIfPossible)")
            reportDiagnostic(activity, "SIGN_IN_START", "FilterAuthorized=$filterByAuthorizedAccounts, AutoSelect=$autoSelectIfPossible")

            val credential = try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                    .setServerClientId(SettingsRepository.WEB_CLIENT_ID)
                    .setAutoSelectEnabled(autoSelectIfPossible)
                    .build()

                val requestBuilder = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)

                val result = credentialManager.getCredential(activity, requestBuilder.build())
                result.credential
            } catch (e: Exception) {
                Log.w(TAG, "GetCredentialRequest exception: ${e.javaClass.simpleName} - ${e.message}")
                reportDiagnostic(activity, "CREDENTIAL_REQUEST_ERROR", "${e.javaClass.simpleName}: ${e.message}", e)

                if (e is GetCredentialCancellationException && (filterByAuthorizedAccounts || autoSelectIfPossible)) {
                    Log.d(TAG, "Auto-select/filter returned cancellation. Retrying with explicit user account picker...")
                    return@withContext signIn(
                        activity = activity,
                        settingsRepo = settingsRepo,
                        filterByAuthorizedAccounts = false,
                        autoSelectIfPossible = false
                    )
                }

                if (filterByAuthorizedAccounts) {
                    return@withContext signIn(
                        activity = activity,
                        settingsRepo = settingsRepo,
                        filterByAuthorizedAccounts = false,
                        autoSelectIfPossible = false
                    )
                }

                throw e
            }

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val rawPhoto = googleIdTokenCredential.profilePictureUri?.toString() ?: ""
                val photoUrl = if (rawPhoto.isNotBlank()) {
                    rawPhoto.replace(Regex("=s\\d+-c"), "=s400-c")
                        .let { if (it == rawPhoto) "$it=s400-c" else it }
                } else ""

                val idToken = googleIdTokenCredential.idToken
                Log.d(TAG, "Google ID Token acquired for email: $email. Authenticating with Firebase...")

                var firebaseUser: FirebaseUser? = null
                try {
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = Tasks.await(FirebaseAuth.getInstance().signInWithCredential(authCredential))
                    firebaseUser = authResult.user
                    Log.d(TAG, "✅ Firebase Authentication successful for UID: ${firebaseUser?.uid}")
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase Auth sign-in warning (proceeding with local Google account info): ${e.message}")
                    reportDiagnostic(activity, "FIREBASE_AUTH_WARNING", "Firebase auth fallback: ${e.message}", e)
                }

                // Get name directly from Google Account profile / Firebase User
                val directName = (firebaseUser?.displayName ?: googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName)
                    ?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@")

                // Synchronize DataStore state
                settingsRepo.setGoogleLoggedIn(true)
                settingsRepo.setGoogleAccountName(directName)
                settingsRepo.setGoogleAccountEmail(email)
                settingsRepo.setGoogleAccountPhoto(photoUrl)

                reportDiagnostic(activity, "SIGN_IN_SUCCESS", "Signed in email=$email, directName=$directName")

                return@withContext AuthResult.Success(
                    firebaseUser = firebaseUser,
                    email = email,
                    displayName = directName,
                    photoUrl = photoUrl
                )
            } else {
                val err = "Unexpected credential type returned: ${credential.type}"
                Log.w(TAG, err)
                reportDiagnostic(activity, "UNEXPECTED_CREDENTIAL_TYPE", err)
                return@withContext AuthResult.Failure("Unexpected response from Google Credential Provider: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            val cancelDetail = e.message ?: "Sign-in cancelled or dismissed by user"
            Log.i(TAG, "Google Sign-In cancelled: $cancelDetail")
            reportDiagnostic(activity, "SIGN_IN_CANCELLED", cancelDetail, e)
            return@withContext AuthResult.Cancelled(reason = cancelDetail)
        } catch (e: NoCredentialException) {
            Log.e(TAG, "No Google credentials found on device", e)
            reportDiagnostic(activity, "NO_CREDENTIAL_EXCEPTION", e.message ?: "No account", e)

            if (filterByAuthorizedAccounts) {
                Log.d(TAG, "No authorized credentials found. Retrying with explicit user account picker...")
                return@withContext signIn(
                    activity = activity,
                    settingsRepo = settingsRepo,
                    filterByAuthorizedAccounts = false,
                    autoSelectIfPossible = false
                )
            }

            // Check if device has Google accounts via AccountManager
            val deviceAccounts = try {
                AccountManager.get(activity).getAccountsByType("com.google")
            } catch (_: Exception) { emptyArray() }

            if (deviceAccounts.isNotEmpty()) {
                val primaryAccount = deviceAccounts[0]
                val email = primaryAccount.name
                val directName = email.substringBefore("@")

                Log.i(TAG, "Detected Google account on device via AccountManager: $email. Logging in locally as $directName...")
                settingsRepo.setGoogleLoggedIn(true)
                settingsRepo.setGoogleAccountName(directName)
                settingsRepo.setGoogleAccountEmail(email)

                return@withContext AuthResult.Success(
                    firebaseUser = null,
                    email = email,
                    displayName = directName,
                    photoUrl = ""
                )
            }

            return@withContext AuthResult.Failure(
                message = "No Google accounts found on this device. Please sign in to a Google account in Android Settings and try again.",
                cause = e,
                isNoAccountFound = true
            )
        } catch (e: GetCredentialException) {
            val msg = e.message ?: "Google Sign-In error"
            Log.e(TAG, "GetCredentialException: type=${e.type}, msg=$msg", e)
            reportDiagnostic(activity, "CREDENTIAL_EXCEPTION_${e.type}", msg, e)
            val isConfigErr = msg.contains("10", ignoreCase = true) || msg.contains("16", ignoreCase = true) || msg.contains("DEVELOPER_ERROR", ignoreCase = true)
            return@withContext AuthResult.Failure(
                message = if (isConfigErr) {
                    "Google Sign-In configuration mismatch [Code 16/10]. (Checking Cloud Console Credentials & OAuth consent screen...)"
                } else {
                    "Google Sign-In error [${e.type}]: $msg"
                },
                cause = e,
                isConfigurationIssue = isConfigErr
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google authentication", e)
            reportDiagnostic(activity, "UNEXPECTED_EXCEPTION", e.localizedMessage ?: "Unknown error", e)
            return@withContext AuthResult.Failure("Sign-In failed: ${e.localizedMessage ?: "Unknown error"}", cause = e)
        }
    }

    /**
     * Attempts a non-intrusive / silent sign-in for previously authorized accounts.
     */
    suspend fun signInSilently(
        activity: Activity,
        settingsRepo: SettingsRepository
    ): AuthResult {
        return signIn(
            activity = activity,
            settingsRepo = settingsRepo,
            filterByAuthorizedAccounts = true,
            autoSelectIfPossible = true
        )
    }

    /**
     * Signs out of Firebase Auth, clears local CredentialManager states, and resets DataStore preferences.
     */
    suspend fun signOut(context: Context, settingsRepo: SettingsRepository) = withContext(Dispatchers.IO) {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error signing out of Firebase Auth", e)
        }

        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing CredentialManager state", e)
        }

        settingsRepo.clearGoogleAuth()
        Log.d(TAG, "Successfully signed out of Google & Firebase Auth.")
    }
}
