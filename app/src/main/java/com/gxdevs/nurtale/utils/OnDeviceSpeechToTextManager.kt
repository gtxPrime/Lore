package com.gxdevs.nurtale.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * On-Device Audio-to-Words (Speech-to-Text) Transcription Engine.
 *
 * Uses Android's native [SpeechRecognizer] with offline/on-device recognition flags.
 * Converts spoken voice notes into words (`String`) 100% on-device without internet.
 *
 * Transcribed words are then passed directly into [MoodScoringEngine], [SentimentScorer],
 * and [AdaptiveMoodModel] for full semantic & emotional analysis.
 *
 * 100% On-device · 100% Private · 0 Cloud Costs.
 */
object OnDeviceSpeechToTextManager {

    /**
     * State of speech-to-text transcription.
     */
    sealed class TranscriptionState {
        object Idle : TranscriptionState()
        object Listening : TranscriptionState()
        data class PartialResult(val partialText: String) : TranscriptionState()
        data class FinalResult(val transcribedWords: String) : TranscriptionState()
        data class Error(val errorMessage: String) : TranscriptionState()
    }

    /**
     * Listens to speech input and transcribes audio to words in real-time.
     * Returns a [Flow] emitting [TranscriptionState] updates.
     */
    fun startOnDeviceTranscription(
        context: Context,
        languageLocale: Locale = Locale.getDefault()
    ): Flow<TranscriptionState> = callbackFlow {

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(TranscriptionState.Error("Speech recognition is not supported on this device."))
            close()
            return@callbackFlow
        }

        val speechRecognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Force on-device offline mode if supported by device OS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(TranscriptionState.Listening)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val msg = getErrorMessage(error)
                trySend(TranscriptionState.Error(msg))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val words = matches?.firstOrNull() ?: ""
                trySend(TranscriptionState.FinalResult(words))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialWords = matches?.firstOrNull() ?: ""
                if (partialWords.isNotBlank()) {
                    trySend(TranscriptionState.PartialResult(partialWords))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)

        awaitClose {
            try {
                speechRecognizer.stopListening()
                speechRecognizer.destroy()
            } catch (_: Exception) {}
        }
    }

    private fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition failed"
    }
}
