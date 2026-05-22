package com.gxdevs.athera.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Utility class for video playback with encrypted file support */
class VideoPlayer(private val context: Context) {

    private val securityManager = SecurityManager(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        private const val TAG = "VideoPlayer"
    }

    private val focusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                // Video player just needs to acquire focus; the actual player handles volume/pause
                Log.d(TAG, "Audio focus changed: $focusChange")
            }

    /** Request audio focus to pause other media apps */
    fun requestAudioFocus(): Boolean {
        val result =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest =
                            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                    .setAudioAttributes(
                                            AudioAttributes.Builder()
                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                    .setContentType(
                                                            AudioAttributes.CONTENT_TYPE_MOVIE
                                                    )
                                                    .build()
                                    )
                                    .setOnAudioFocusChangeListener(focusChangeListener)
                                    .build()
                    audioManager.requestAudioFocus(audioFocusRequest!!)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(
                            focusChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN
                    )
                }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Release audio focus */
    fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    /**
     * Decrypt an encrypted video file and return the temp file path This should be called from a
     * background thread
     */
    suspend fun decryptVideoToTemp(encryptedFile: File): File? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile =
                        File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
                val encryptedFileWrapper = securityManager.getEncryptedFile(encryptedFile)
                encryptedFileWrapper.openFileInput().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "Decrypted video to: ${tempFile.absolutePath}")
                tempFile
            } catch (e: Exception) {
                Log.e(TAG, "Error decrypting video", e)
                null
            }
        }
    }

    /** Clean up a temp video file */
    fun cleanupTempFile(tempFile: File?) {
        try {
            tempFile?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting temp file", e)
        }
    }

    /**
     * Check if a file appears to be encrypted by checking magic bytes. Video files (MP4, MOV, 3GP)
     * have known magic bytes.
     */
    /**
     * Check if a file appears to be encrypted by checking magic bytes. Video files (MP4, MOV, 3GP)
     * have known magic bytes.
     */
    private fun isFileEncrypted(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 12) {
                Log.w(
                        TAG,
                        "File too small or doesn't exist for encryption check: ${file.absolutePath}"
                )
                return true // Assume encrypted to be safe, though it will likely fail later
            }

            file.inputStream().use { input ->
                val header = ByteArray(12)
                val bytesRead = input.read(header)
                Log.d(TAG, "Read $bytesRead bytes for header check from ${file.name}")

                // Check if "ftyp" exists anywhere in first 12 bytes (common for MP4/MOV videos)
                val ftypExists =
                        header.toList().windowed(4).any {
                            it == listOf(0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte())
                        }

                Log.d(TAG, "File ${file.name} encryption check: ftypFound=$ftypExists")

                // If we find video magic bytes, it's NOT encrypted
                !ftypExists
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file encryption status for ${file.name}", e)
            true // Assume encrypted on error
        }
    }

    /**
     * Extract a thumbnail from a video file. Automatically detects if the file is encrypted and
     * handles accordingly. This runs on IO dispatcher to prevent UI blocking.
     */
    suspend fun getThumbnailFromVideo(videoFile: File): android.graphics.Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(
                        TAG,
                        "Getting thumbnail for: ${videoFile.absolutePath}, exists=${videoFile.exists()}"
                )

                if (!videoFile.exists()) {
                    Log.e(TAG, "Video file does not exist: ${videoFile.absolutePath}")
                    return@withContext null
                }

                // First, try reading directly (unencrypted)
                if (!isFileEncrypted(videoFile)) {
                    Log.d(TAG, "File appears unencrypted, reading directly: ${videoFile.name}")
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(videoFile.absolutePath)
                        val frame =
                                retriever.getFrameAtTime(
                                        1000000,
                                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                )
                        Log.d(TAG, "Thumbnail extracted directly: ${frame != null}")
                        return@withContext frame
                    } catch (e: Exception) {
                        Log.e(
                                TAG,
                                "Failed to read unencrypted file directly, might be encrypted actually?",
                                e
                        )
                        // Fallback to decryption attempt?
                    } finally {
                        retriever.release()
                    }
                }

                // File is encrypted, decrypt first
                Log.d(TAG, "File appears encrypted, decrypting for thumbnail: ${videoFile.name}")
                getThumbnailFromEncryptedVideo(videoFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting thumbnail from video", e)
                null
            }
        }
    }

    /**
     * Decrypt video to temp and get the file for playback. Returns the temp file if encrypted, or
     * the original file if not encrypted.
     */
    suspend fun getPlayableVideoFile(videoFile: File): File? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Get playable file for: ${videoFile.absolutePath}")

                if (!videoFile.exists()) {
                    Log.e(TAG, "Video file does not exist: ${videoFile.absolutePath}")
                    return@withContext null
                }

                // Check if encrypted
                if (!isFileEncrypted(videoFile)) {
                    Log.d(TAG, "File is not encrypted, returning original: ${videoFile.name}")
                    return@withContext videoFile
                }

                // Decrypt to temp
                Log.d(TAG, "File is encrypted, decrypting: ${videoFile.name}")
                decryptVideoToTemp(videoFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting playable video file", e)
                null
            }
        }
    }

    /**
     * Extract a thumbnail from an encrypted video file. Decrypts to temp, extracts frame at 1
     * second, then cleans up.
     */
    private suspend fun getThumbnailFromEncryptedVideo(
            encryptedFile: File
    ): android.graphics.Bitmap? {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                // Decrypt to temp file
                tempFile = File(context.cacheDir, "temp_thumb_${System.currentTimeMillis()}.mp4")
                val encryptedFileWrapper = securityManager.getEncryptedFile(encryptedFile)
                encryptedFileWrapper.openFileInput().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                // Extract frame using MediaMetadataRetriever
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(tempFile.absolutePath)
                val frame =
                        retriever.getFrameAtTime(
                                1000000, // 1 second in microseconds
                                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                retriever.release()

                Log.d(TAG, "Extracted thumbnail from encrypted video: ${encryptedFile.name}")
                frame
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting thumbnail from encrypted video", e)
                null
            } finally {
                // Clean up temp file
                tempFile?.delete()
            }
        }
    }
}


