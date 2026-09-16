package com.gxdevs.lore.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.sin

/**
 * Ambient Sanctuary Soundscapes for Lore Journaling.
 * Procedurally generates gentle relaxing audio (Rain, Stream, Meditation Drone, Campfire)
 * using AudioTrack with zero asset overhead and 100% offline privacy.
 */
object SanctuarySoundscapeHelper {

    enum class SoundscapeType(val displayName: String, val iconRes: String) {
        NONE("Off", "close"),
        RAIN("Gentle Rain", "water_drop"),
        STREAM("Forest Stream", "waves"),
        MEDITATION("Deep Stillness", "self_improvement"),
        CAMPFIRE("Night Hearth", "local_fire_department")
    }

    private var audioTrack: AudioTrack? = null
    private var generationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentType: SoundscapeType = SoundscapeType.NONE

    fun getCurrentSoundscape(): SoundscapeType = currentType

    fun startSoundscape(type: SoundscapeType) {
        stopSoundscape()
        if (type == SoundscapeType.NONE) return
        currentType = type

        generationJob = scope.launch {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate / 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val random = Random()
            val shortBuffer = ShortArray(bufferSize)
            var brownVal = 0.0
            var phase = 0.0

            while (isActive) {
                for (i in 0 until bufferSize) {
                    when (type) {
                        SoundscapeType.RAIN -> {
                            // Pink / filtered noise simulating gentle rainfall
                            val white = (random.nextDouble() * 2.0 - 1.0)
                            brownVal = (brownVal * 0.94) + (white * 0.06)
                            val rainDrop = if (random.nextDouble() < 0.003) (random.nextDouble() * 0.3) else 0.0
                            val sample = ((brownVal * 0.45 + rainDrop) * 32767.0).coerceIn(-32767.0, 32767.0)
                            shortBuffer[i] = sample.toInt().toShort()
                        }
                        SoundscapeType.STREAM -> {
                            // Flowing water: gentle rolling low-frequency modulated brown noise
                            val white = (random.nextDouble() * 2.0 - 1.0)
                            brownVal = (brownVal * 0.96) + (white * 0.04)
                            phase += 0.0008
                            val wave = (sin(phase) + 1.0) * 0.5
                            val sample = ((brownVal * (0.35 + wave * 0.2)) * 32767.0).coerceIn(-32767.0, 32767.0)
                            shortBuffer[i] = sample.toInt().toShort()
                        }
                        SoundscapeType.MEDITATION -> {
                            // 108Hz soothing harmonic drone
                            phase += (2.0 * Math.PI * 108.0) / sampleRate
                            val harmonic = sin(phase) * 0.25 + sin(phase * 0.5) * 0.15
                            shortBuffer[i] = (harmonic * 32767.0).toInt().toShort()
                        }
                        SoundscapeType.CAMPFIRE -> {
                            // Soft fireplace rumble with occasional crackle
                            val white = (random.nextDouble() * 2.0 - 1.0)
                            brownVal = (brownVal * 0.97) + (white * 0.03)
                            val crackle = if (random.nextDouble() < 0.0015) (random.nextDouble() * 0.7 - 0.35) else 0.0
                            val sample = ((brownVal * 0.35 + crackle) * 32767.0).coerceIn(-32767.0, 32767.0)
                            shortBuffer[i] = sample.toInt().toShort()
                        }
                        SoundscapeType.NONE -> {
                            shortBuffer[i] = 0
                        }
                    }
                }
                track.write(shortBuffer, 0, bufferSize)
            }
        }
    }

    fun stopSoundscape() {
        currentType = SoundscapeType.NONE
        generationJob?.cancel()
        generationJob = null
        audioTrack?.runCatching {
            stop()
            release()
        }
        audioTrack = null
    }
}
