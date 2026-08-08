package com.gxdevs.lore.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            val cause: Throwable? = null
        ) : AuthResult()

        data class Cancelled(val reason: String = "Sign-in cancelled") : AuthResult()
    }

    val currentUser: FirebaseUser?
        get() = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }

    suspend fun signIn(
        activity: Activity,
        settingsRepo: SettingsRepository
    ): AuthResult = withContext(Dispatchers.IO) {
        val credentialManager = CredentialManager.create(activity)

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(SettingsRepository.WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = try {
                credentialManager.getCredential(activity, request)
            } catch (e: GetCredentialCancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "GetGoogleIdOption failed (${e.javaClass.simpleName}: ${e.message}) — retrying with GetSignInWithGoogleOption")
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(SettingsRepository.WEB_CLIENT_ID).build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()
                credentialManager.getCredential(activity, fallbackRequest)
            }

            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val idToken = googleIdTokenCredential.idToken
                val displayNameFromGoogle = googleIdTokenCredential.displayName
                val rawPhoto = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                val photoUrl = if (rawPhoto.isNotBlank()) {
                    rawPhoto.replace(Regex("=s\\d+-c"), "=s400-c")
                        .let { if (it == rawPhoto) "$it=s400-c" else it }
                } else ""

                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = Tasks.await(FirebaseAuth.getInstance().signInWithCredential(authCredential))
                val firebaseUser = authResult.user

                val directName = (firebaseUser?.displayName ?: displayNameFromGoogle)
                    ?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@")

                settingsRepo.setGoogleLoggedIn(true)
                settingsRepo.setGoogleAccountName(directName)
                settingsRepo.setGoogleAccountEmail(email)
                settingsRepo.setGoogleAccountPhoto(photoUrl)

                AuthResult.Success(
                    firebaseUser = firebaseUser,
                    email = email,
                    displayName = directName,
                    photoUrl = photoUrl
                )
            } else {
                AuthResult.Failure("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Cancelled(e.message ?: "Sign-in cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            AuthResult.Failure(e.message ?: "Google Sign-In failed", cause = e)
        }
    }

    suspend fun signOut(context: Context, settingsRepo: SettingsRepository) = withContext(Dispatchers.IO) {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth sign out failed", e)
        }
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "CredentialManager clear state failed", e)
        }
        try {
            settingsRepo.clearGoogleAuth()
        } catch (e: Exception) {
            Log.w(TAG, "DataStore clear Google auth failed", e)
        }
    }
}
