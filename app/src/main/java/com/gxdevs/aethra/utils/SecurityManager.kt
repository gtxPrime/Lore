package com.gxdevs.aethra.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "aethra_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
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

    // ─── Encryption helpers ───────────────────────────────────────────────────

    /**
     * Encrypt bytes from [inputStream] and write to [dest].
     * The caller is responsible for closing [inputStream].
     */
    fun encryptStream(inputStream: InputStream, dest: File) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv

        dest.outputStream().use { fos ->
            // 1. Write the length of IV (1 byte)
            fos.write(iv.size)
            // 2. Write the IV itself
            fos.write(iv)
            // 3. Encrypt data and write cipher text
            CipherOutputStream(fos, cipher).use { cos ->
                inputStream.copyTo(cos)
            }
        }
    }

    /**
     * Decrypt [encryptedFile] and write the plaintext to [dest].
     */
    fun decryptFile(encryptedFile: File, dest: File) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        openDecryptedStream(encryptedFile).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Decrypt encryptedFile to a unique temp file inside context.cacheDir/dec_tmp/.
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
     * Open an [InputStream] over the decrypted content of [encryptedFile].
     * Caller must close the stream.
     */
    fun openDecryptedStream(encryptedFile: File): InputStream {
        val fis = encryptedFile.inputStream()
        try {
            val ivSize = fis.read()
            if (ivSize <= 0) throw IllegalArgumentException("Invalid encrypted file: no IV")
            val iv = ByteArray(ivSize)
            fis.read(iv)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            return CipherInputStream(fis, cipher)
        } catch (e: Exception) {
            fis.close()
            throw e
        }
    }
}
