package com.gxdevs.aethra.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val securityManager = SecurityManager(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private val focusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        // Permanent loss - stop playback
                        stop()
                        hasAudioFocus = false
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        // Temporary loss - pause
                        pause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        // Lower volume or pause
                        mediaPlayer?.setVolume(0.3f, 0.3f)
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        // Regained focus
                        mediaPlayer?.setVolume(1f, 1f)
                        hasAudioFocus = true
                    }
                }
            }

    private fun requestAudioFocus(): Boolean {
        val result =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest =
                            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                    .setAudioAttributes(
                                            AudioAttributes.Builder()
                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                    .setContentType(
                                                            AudioAttributes.CONTENT_TYPE_MUSIC
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
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        hasAudioFocus = false
    }

    /** Play an encrypted audio file First decrypts to temp location, then plays */
    fun playEncryptedFile(
            encryptedFile: File,
            onStart: () -> Unit = {},
            onComplete: () -> Unit = {},
            onError: (String) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Stop any currently playing audio
                stop()

                // Request audio focus to pause other apps
                if (!requestAudioFocus()) {
                    onError("Could not get audio focus")
                    return@launch
                }

                val tempFile =
                        File(context.cacheDir, "temp_playback_${System.currentTimeMillis()}.m4a")

                // Decrypt in background
                withContext(Dispatchers.IO) { decryptFile(encryptedFile, tempFile) }

                // Play
                mediaPlayer =
                        MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            setOnCompletionListener {
                                tempFile.delete()
                                abandonAudioFocus()
                                onComplete()
                            }
                            setOnErrorListener { _, what, extra ->
                                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                tempFile.delete()
                                abandonAudioFocus()
                                onError("Failed to play audio")
                                true
                            }
                            start()
                            onStart()
                        }
                Log.d(TAG, "Playing audio file: ${encryptedFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio", e)
                abandonAudioFocus()
                onError("Failed to play audio: ${e.message}")
            }
        }
    }

    /** Decrypt an encrypted file to a destination */
    private fun decryptFile(encryptedFile: File, destFile: File) {
        val encryptedFileWrapper = securityManager.getEncryptedFile(encryptedFile)
        encryptedFileWrapper.openFileInput().use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** Stop playback and release resources */
    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        abandonAudioFocus()
    }

    /** Pause playback */
    fun pause() {
        mediaPlayer?.apply {
            if (isPlaying) {
                pause()
            }
        }
    }

    /** Resume playback */
    fun resume() {
        if (!hasAudioFocus) {
            requestAudioFocus()
        }
        mediaPlayer?.apply {
            if (!isPlaying) {
                start()
            }
        }
    }

    /** Check if currently playing */
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    /** Get audio duration in milliseconds */
    fun getDuration(): Long {
        return try {
            mediaPlayer?.duration?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Get current playback position in milliseconds */
    fun getCurrentPosition(): Long {
        return try {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Get audio duration from encrypted file without playing - suspend function */
    suspend fun getAudioDuration(encryptedFile: File): Long {
        return withContext(Dispatchers.IO) {
            var tempPlayer: MediaPlayer? = null
            val tempFile =
                    File(context.cacheDir, "temp_duration_check_${System.currentTimeMillis()}.m4a")

            try {
                decryptFile(encryptedFile, tempFile)

                tempPlayer =
                        MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                        }

                val duration = tempPlayer.duration.toLong()
                duration
            } catch (e: Exception) {
                Log.e(TAG, "Error getting audio duration", e)
                0L
            } finally {
                tempPlayer?.release()
                try {
                    tempFile.delete()
                } catch (e: Exception) {}
            }
        }
    }
}


