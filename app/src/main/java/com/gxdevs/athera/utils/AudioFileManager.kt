package com.gxdevs.athera.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

class AudioFileManager(private val context: Context) {
    
    private val securityManager = SecurityManager(context)
    
    companion object {
        private const val TAG = "AudioFileManager"
        private const val AUDIO_DIR = "audio_recordings"
        private const val TEMP_DIR = "temp_audio"
    }
    
    /**
     * Get the directory for storing encrypted audio recordings
     */
    fun getAudioDirectory(): File {
        val dir = File(context.filesDir, AUDIO_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
            createNoMediaFile(dir)
        }
        return dir
    }
    
    /**
     * Get the directory for temporary audio files during recording
     */
    fun getTempDirectory(): File {
        val dir = File(context.cacheDir, TEMP_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Create a .nomedia file to hide recordings from media scanner
     */
    private fun createNoMediaFile(directory: File) {
        val noMediaFile = File(directory, ".nomedia")
        if (!noMediaFile.exists()) {
            try {
                noMediaFile.createNewFile()
                Log.d(TAG, ".nomedia file created successfully")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create .nomedia file", e)
            }
        }
    }
    
    /**
     * Generate a unique filename for a new recording
     */
    fun generateTempFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "TEMP_REC_$timestamp.m4a"
    }
    
    /**
     * Generate filename for encrypted recording
     */
    fun generateEncryptedFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "REC_${timestamp}_encrypted.m4a"
    }
    
    /**
     * Create a temporary file for recording
     */
    fun createTempFile(): File {
        val tempDir = getTempDirectory()
        val fileName = generateTempFileName()
        return File(tempDir, fileName)
    }
    
    /**
     * Encrypt and save the temporary recording to permanent storage
     * @return The encrypted file, or null if encryption fails
     */
    fun encryptAndSave(tempFile: File): File? {
        if (!tempFile.exists()) {
            Log.e(TAG, "Temp file does not exist: ${tempFile.absolutePath}")
            return null
        }
        
        try {
            val audioDir = getAudioDirectory()
            val encryptedFileName = generateEncryptedFileName()
            val encryptedFile = File(audioDir, encryptedFileName)
            
            // Encrypt the file using SecurityManager
            securityManager.encryptFile(tempFile, encryptedFile)
            
            // Delete the temporary file
            tempFile.delete()
            
            Log.d(TAG, "Audio encrypted and saved: ${encryptedFile.absolutePath}")
            return encryptedFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and save audio", e)
            return null
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


