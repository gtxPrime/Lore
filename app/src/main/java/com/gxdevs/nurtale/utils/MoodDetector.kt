package com.gxdevs.nurtale.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FilterDrama
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/**
 * On-Device Privacy-First Sentiment & Emotion Detection Engine.
 * Uses clean Android Vector Icons (Icons.Rounded.*) instead of emojis.
 */
object MoodDetector {

    enum class DetectedMood(val id: Long, val label: String, val icon: ImageVector, val accentHex: Long) {
        PEACEFUL(1L, "Peaceful", Icons.Rounded.Spa, 0xFF606F49),
        JOYFUL(2L, "Joyful", Icons.Rounded.WbSunny, 0xFFD4A373),
        REFLECTIVE(3L, "Reflective", Icons.Rounded.SelfImprovement, 0xFF6B705C),
        HEAVY(4L, "Heavy", Icons.Rounded.FilterDrama, 0xFF4A5638),
        ANXIOUS(5L, "Anxious", Icons.Rounded.AutoAwesome, 0xFFA5A58D),
        ENERGETIC(6L, "Energetic", Icons.Rounded.WbSunny, 0xFFCB997E),
        GRATITUDE(7L, "Grateful", Icons.Rounded.Bedtime, 0xFFB7B7A4)
    }

    data class AnalysisResult(
        val primaryMood: DetectedMood,
        val sentimentScore: Float, // -1.0 (very negative) to +1.0 (very positive)
        val wordCount: Int,
        val suggestedTags: List<String>,
        val confidence: Float // 0.0 to 1.0
    )

    // Expanded Sentiment Dictionaries
    private val positiveLexicon = setOf(
        "happy", "joy", "grateful", "blessed", "peace", "calm", "serene", "excited",
        "love", "loved", "wonderful", "amazing", "great", "smile", "laugh", "hope",
        "hopeful", "content", "fulfilled", "inspired", "triumph", "proud", "light",
        "growth", "harmony", "clarity", "sweet", "beautiful", "bliss", "rejoice"
    )

    private val negativeLexicon = setOf(
        "sad", "heavy", "tired", "exhausted", "pain", "hurt", "lonely", "anxious",
        "scared", "fear", "overwhelmed", "stress", "stressed", "cry", "crying",
        "grief", "loss", "anger", "angry", "frustrated", "dark", "hopeless",
        "broken", "numb", "worry", "worried", "struggle", "doubt", "regret"
    )

    private val anxietyKeywords = setOf(
        "anxious", "anxiety", "panic", "overwhelmed", "nervous", "worry", "worried",
        "racing", "scared", "fear", "restless", "uncertain", "dread", "tense"
    )

    private val gratitudeKeywords = setOf(
        "thankful", "grateful", "gratitude", "blessed", "appreciate", "appreciated",
        "gift", "kindness", "grace", "fortunate", "cherish"
    )

    private val energyKeywords = setOf(
        "excited", "pumped", "fire", "energetic", "power", "win", "achieved",
        "passion", "driven", "action", "creative", "flow", "stoked"
    )

    private val peaceKeywords = setOf(
        "calm", "peace", "quiet", "still", "rest", "relax", "breathe", "cozy",
        "solitude", "gentle", "smooth", "soothe", "meditate"
    )

    /**
     * Analyzes raw journal content and returns detected sentiment, primary mood, and suggested tags.
     */
    fun analyze(text: String?): AnalysisResult {
        if (text.isNullOrBlank()) {
            return AnalysisResult(
                primaryMood = DetectedMood.PEACEFUL,
                sentimentScore = 0f,
                wordCount = 0,
                suggestedTags = emptyList(),
                confidence = 0f
            )
        }

        val words = text.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }

        val totalWords = words.size
        if (totalWords == 0) {
            return AnalysisResult(DetectedMood.PEACEFUL, 0f, 0, emptyList(), 0f)
        }

        var posHits = 0
        var negHits = 0
        var anxietyHits = 0
        var gratitudeHits = 0
        var energyHits = 0
        var peaceHits = 0

        for (word in words) {
            if (positiveLexicon.contains(word)) posHits++
            if (negativeLexicon.contains(word)) negHits++
            if (anxietyKeywords.contains(word)) anxietyHits++
            if (gratitudeKeywords.contains(word)) gratitudeHits++
            if (energyKeywords.contains(word)) energyHits++
            if (peaceKeywords.contains(word)) peaceHits++
        }

        val sentimentScore = when {
            posHits + negHits == 0 -> 0f
            else -> (posHits - negHits).toFloat() / (posHits + negHits).toFloat()
        }

        // Determine Primary Mood
        val primaryMood = when {
            gratitudeHits >= 2 || (gratitudeHits == 1 && posHits > negHits) -> DetectedMood.GRATITUDE
            anxietyHits >= 2 -> DetectedMood.ANXIOUS
            energyHits >= 2 -> DetectedMood.ENERGETIC
            negHits > posHits && negHits >= 2 -> DetectedMood.HEAVY
            posHits > negHits && posHits >= 2 -> DetectedMood.JOYFUL
            peaceHits >= 1 -> DetectedMood.PEACEFUL
            negHits > 0 -> DetectedMood.REFLECTIVE
            else -> DetectedMood.PEACEFUL
        }

        // Generate Suggested Tags
        val tags = mutableListOf<String>()
        if (gratitudeHits > 0) tags.add("Gratitude")
        if (anxietyHits > 0) tags.add("Reflections")
        if (energyHits > 0) tags.add("Breakthrough")
        if (posHits > negHits) tags.add("Positive Energy")
        if (negHits > posHits) tags.add("Vent / Unburden")
        if (totalWords > 150) tags.add("Deep Thoughts")

        val totalMatched = posHits + negHits + anxietyHits + gratitudeHits + energyHits + peaceHits
        val confidence = (totalMatched.toFloat() / (totalWords * 0.2f).coerceAtLeast(1f)).coerceIn(0.2f, 0.95f)

        return AnalysisResult(
            primaryMood = primaryMood,
            sentimentScore = sentimentScore,
            wordCount = totalWords,
            suggestedTags = tags.distinct(),
            confidence = confidence
        )
    }
}
