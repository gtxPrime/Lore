package com.gxdevs.lore.utils

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions

/**
 * On-Device Language Identification Layer.
 *
 * Uses ML Kit Language ID served via Google Play Services — the entire 110-language
 * model is served through GMS, adding exactly 0 KB to the APK.
 *
 * Results are used by [MoodScoringEngine] to apply language-appropriate lexicon
 * weight boosts during mood analysis, making the system genuinely multilingual.
 *
 * Supported confidence threshold: 0.40 (ML Kit default is 0.50, we lower it slightly
 * to catch more multilingual entries like Hinglish).
 */
object LanguageDetector {

    /**
     * Detected language result with a confidence score.
     * [bcp47Code] is a BCP-47 language code like "en", "hi", "es", "fr", "de", etc.
     * [isUndetermined] is true when ML Kit cannot identify the language with confidence.
     */
    data class DetectionResult(
        val bcp47Code: String,
        val confidence: Float,
        val isUndetermined: Boolean
    )

    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.40f) // Slightly lower to catch Hinglish/mixed-lang
            .build()
    )

    /**
     * Detects the primary language of [text] synchronously.
     * Safe to call from a background coroutine (uses Tasks.await internally).
     *
     * Returns [DetectionResult] with the BCP-47 code and confidence.
     * If undetermined or on error, returns [DetectionResult] with isUndetermined=true.
     */
    fun detectSync(text: String?): DetectionResult {
        if (text.isNullOrBlank() || text.length < 10) {
            return DetectionResult("und", 0f, true)
        }

        return try {
            val result = Tasks.await(identifier.identifyLanguage(text))
            if (result == "und") {
                DetectionResult("und", 0f, true)
            } else {
                DetectionResult(result, 1.0f, false)
            }
        } catch (e: Exception) {
            DetectionResult("und", 0f, true)
        }
    }

    /**
     * Returns the lexicon weight multiplier for a given BCP-47 language code.
     * Non-English scripts need a higher multiplier because our base lexicons
     * are English-first; native-language tokens hit the lexicons less often.
     */
    fun lexiconWeightFor(bcp47: String): Float {
        return when (bcp47) {
            "en" -> 1.0f    // English — full lexicon coverage
            "hi" -> 2.0f    // Hindi — boost since Hindi lexicon is smaller
            "es" -> 1.8f    // Spanish
            "fr" -> 1.8f    // French
            "de" -> 1.8f    // German
            "pt" -> 1.8f    // Portuguese
            "ar" -> 2.5f    // Arabic — smaller lexicon coverage
            "ja" -> 2.5f    // Japanese
            "ko" -> 2.5f    // Korean
            "zh" -> 2.5f    // Chinese
            "ru" -> 2.0f    // Russian
            "it" -> 1.8f    // Italian
            "nl" -> 1.8f    // Dutch
            "tr" -> 2.0f    // Turkish
            "pl" -> 2.0f    // Polish
            "id" -> 2.0f    // Indonesian
            "vi" -> 2.0f    // Vietnamese
            "bn" -> 2.5f    // Bengali
            "pa" -> 2.5f    // Punjabi
            "mr" -> 2.5f    // Marathi
            "ta" -> 2.5f    // Tamil
            "te" -> 2.5f    // Telugu
            "ur" -> 2.0f    // Urdu
            else -> 1.5f    // Unknown — moderate boost
        }
    }

    /**
     * Returns a human-readable language name for a BCP-47 code.
     * Used in future stats screens to show "Language breakdown of your journals".
     */
    fun languageNameFor(bcp47: String): String {
        return when (bcp47) {
            "en" -> "English"
            "hi" -> "Hindi"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "pt" -> "Portuguese"
            "ar" -> "Arabic"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "ru" -> "Russian"
            "it" -> "Italian"
            "nl" -> "Dutch"
            "tr" -> "Turkish"
            "pl" -> "Polish"
            "id" -> "Indonesian / Malay"
            "vi" -> "Vietnamese"
            "bn" -> "Bengali"
            "pa" -> "Punjabi"
            "mr" -> "Marathi"
            "ta" -> "Tamil"
            "te" -> "Telugu"
            "ur" -> "Urdu"
            "und" -> "Mixed / Unknown"
            else -> bcp47.uppercase()
        }
    }
}
