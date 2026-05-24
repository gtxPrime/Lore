package com.gxdevs.aethra.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

class AudioFileManager(private val context: Context) {

    private val securityManager = SecurityManager(context)

    companion object {
        private const val TAG = "AudioFileManager"
        private const val TEMP_DIR = "temp_audio"
    }
    
    /**
     * Returns the shared encrypted_media directory (same as MediaEncryptionManager).
     * All encrypted files go here so isEncrypted() detection is consistent.
     */
    fun getAudioDirectory(): File = MediaEncryptionManager.encDir(context)

    /**
     * Get the directory for temporary audio files during recording.
     */
    fun getTempDirectory(): File {
        val dir = File(context.cacheDir, TEMP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Generate a unique filename for a new recording (temp, pre-encryption).
     */
    fun generateTempFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "TEMP_REC_$timestamp.m4a"
    }

    /**
     * Generate filename for an encrypted audio recording.
     * Uses .enc extension so MediaEncryptionManager.isEncrypted() detects it correctly.
     */
    fun generateEncryptedFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "enc_audio_$timestamp.enc"
    }
    
    /**
     * Create a temporary file for recording (plain .m4a, exists only during recording).
     */
    fun createTempFile(): File {
        val tempDir = getTempDirectory()
        val fileName = generateTempFileName()
        return File(tempDir, fileName)
    }

    /**
     * Encrypt and save the temporary recording into encrypted_media/ as .enc.
     * Deletes the temp file on success.
     * @return The encrypted file, or null if encryption fails.
     */
    fun encryptAndSave(tempFile: File): File? {
        if (!tempFile.exists()) {
            Log.e(TAG, "Temp file does not exist: ${tempFile.absolutePath}")
            return null
        }
        return try {
            val encryptedFile = File(getAudioDirectory(), generateEncryptedFileName())
            securityManager.encryptFile(tempFile, encryptedFile)
            tempFile.delete()
            Log.d(TAG, "Audio encrypted and saved: ${encryptedFile.absolutePath}")
            encryptedFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and save audio", e)
            null
        }
    }
    
    /**
     * Delete a temporary recording file
     */
    fun deleteTempFile(tempFile: File): Boolean {
        return if (tempFile.exists()) {
            val deleted = tempFile.delete()
            Log.d(TAG, "Temp file deleted: $deleted")
            deleted
        } else {
            Log.w(TAG, "Temp file does not exist: ${tempFile.absolutePath}")
            true
        }
    }
    
    /**
     * Delete an encrypted recording file
     */
    fun deleteEncryptedFile(encryptedFile: File): Boolean {
        return if (encryptedFile.exists()) {
            val deleted = encryptedFile.delete()
            Log.d(TAG, "Encrypted file deleted: $deleted")
            deleted
        } else {
            Log.w(TAG, "Encrypted file does not exist: ${encryptedFile.absolutePath}")
            true
        }
    }
    
    /**
     * Clean up all temporary files (useful for cleanup on app start)
     */
    fun cleanupTempFiles() {
        val tempDir = getTempDirectory()
        tempDir.listFiles()?.forEach { file ->
            file.delete()
            Log.d(TAG, "Cleaned up temp file: ${file.name}")
        }
    }
}


