package com.gxdevs.aethra.utils

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class SecurityManager(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun getEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    /**
     * Authenticate via Biometric / Device Credential.
     * Call from an Activity/Fragment.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Internal Lock",
        subtitle: String = "Unlock your journal",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
            // onAuthenticationFailed = biometric scanned but not recognised → no action
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    fun canAuthenticate(): Int = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )

    // ─── Encryption helpers ───────────────────────────────────────────────────

    /**
     * Encrypt raw bytes and write them to [dest] using Android Keystore-backed AES-256-GCM.
     */
    fun encryptData(data: ByteArray, dest: File) {
        dest.parentFile?.mkdirs()
        getEncryptedFile(dest).openFileOutput().use { out -> out.write(data) }
    }

    /**
     * Copy and encrypt [source] → [dest].
     * [dest] must not already exist (EncryptedFile requires a fresh file).
     */
    fun encryptFile(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        getEncryptedFile(dest).openFileOutput().use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
    }

    /**
     * Encrypt bytes from [inputStream] and write to [dest].
     * The caller is responsible for closing [inputStream].
     */
    fun encryptStream(inputStream: InputStream, dest: File) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        getEncryptedFile(dest).openFileOutput().use { out -> inputStream.copyTo(out) }
    }

    /**
     * Decrypt [encryptedFile] and write the plaintext to [dest].
     */
    fun decryptFile(encryptedFile: File, dest: File) {
        dest.parentFile?.mkdirs()
        getEncryptedFile(encryptedFile).openFileInput().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /**
     * Decrypt [encryptedFile] to a unique temp file inside [context.cacheDir]/dec_tmp/.
     * The caller is responsible for deleting the returned file when done.
     */
    fun decryptToTemp(encryptedFile: File, extension: String = ""): File {
        val tmpDir = File(context.cacheDir, "dec_tmp").also { it.mkdirs() }
        val ext = if (extension.isNotBlank()) ".$extension" else ""
        val tmp = File(tmpDir, "dec_${encryptedFile.nameWithoutExtension}_${System.currentTimeMillis()}$ext")
        decryptFile(encryptedFile, tmp)
        return tmp
    }

    /**
     * Read all decrypted bytes from an encrypted file.
     */
    fun decryptData(encryptedFile: File): ByteArray {
        return getEncryptedFile(encryptedFile).openFileInput().use { it.readBytes() }
    }

    /**
     * Open an [InputStream] over the decrypted content of [encryptedFile].
     * Caller must close the stream.
     */
    fun openDecryptedStream(encryptedFile: File): InputStream =
        getEncryptedFile(encryptedFile).openFileInput()
}
