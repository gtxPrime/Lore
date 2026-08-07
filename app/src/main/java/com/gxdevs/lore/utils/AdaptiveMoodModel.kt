package com.gxdevs.lore.utils

import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.data.mood.MoodWordWeight
import com.gxdevs.lore.data.mood.MoodWordWeightDao
import java.util.Locale

/**
 * On-Device Adaptive Mood Learning Engine for Lore.
 *
 * Implements a Naive Bayes–style self-learning system stored entirely in Room.
 * Every time a user confirms a mood for their journal entry, this engine
 * updates the per-word mood weights in the `mood_word_weights` table.
 *
 * During prediction, [getLearnedScoreAdjustments] re-ranks the base scores
 * from [MoodScoringEngine] with user-specific learned patterns.
 *
 * 100% On-device · 100% Private · 0 KB extra APK size · Fully queryable for stats.
 */
object AdaptiveMoodModel {

    /** Maximum weight a single word-mood pair can accumulate (prevents overfitting). */
    private const val MAX_WORD_WEIGHT = 50

    /**
     * Trains the model from user feedback when a journal is saved with a confirmed mood.
     *
     * @param dao        Room DAO for mood word weights
     * @param text       Raw journal text
     * @param confirmedMood  The mood label the user selected (e.g. "BRIGHT")
     */
    suspend fun trainFromUserFeedback(
        dao: MoodWordWeightDao,
        text: String?,
        confirmedMood: String
    ) {
        if (text.isNullOrBlank()) return

        val tokens = extractInformativeTokens(text)
        if (tokens.isEmpty()) return

        val normalizedMood = confirmedMood.uppercase(Locale.getDefault())
        // Only train on known moods
        if (!MoodConstants.ALL_MOODS.map { it.uppercase() }.contains(normalizedMood)) return

        for (token in tokens) {
            val existing = dao.getWeight(token, normalizedMood)
            val newWeight = if (existing != null) {
                (existing.weight + 1).coerceAtMost(MAX_WORD_WEIGHT)
            } else {
                1
            }
            dao.upsert(
                MoodWordWeight(
                    id          = existing?.id ?: 0L,
                    word        = token,
                    mood        = normalizedMood,
                    weight      = newWeight,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Returns learned score adjustments for all 6 moods based on the journal text.
     *
     * The returned map contains additive score adjustments to apply on top of
     * [MoodScoringEngine.analyze] base scores. Scores are in the same 0–100 range.
     *
     * @param dao   Room DAO for mood word weights
     * @param text  Raw journal text to analyze
     * @return Map of mood label → additional score bonus (0 if no learned signal)
     */
    suspend fun getLearnedScoreAdjustments(
        dao: MoodWordWeightDao,
        text: String?
    ): Map<String, Float> {
        if (text.isNullOrBlank()) return emptyMap()

        val tokens = extractInformativeTokens(text)
        if (tokens.isEmpty()) return emptyMap()

        // Fetch all weight rows for these tokens in one DB query
        val weightRows = dao.getWeightsForWords(tokens)
        if (weightRows.isEmpty()) return emptyMap()

        // Aggregate raw weights per mood
        val rawScores = mutableMapOf<String, Float>()
        for (row in weightRows) {
            rawScores[row.mood] = (rawScores[row.mood] ?: 0f) + row.weight.toFloat()
        }

        // Normalize by vocabulary size to prevent old entries from dominating
        val vocabSize = dao.getVocabularySize().coerceAtLeast(1)
        val scaleFactor = (30f / vocabSize.toFloat()).coerceIn(0.1f, 5f)

        return rawScores.mapValues { (_, v) ->
            (v * scaleFactor).coerceIn(0f, 40f) // max 40-point bonus from adaptive layer
        }
    }

    /**
     * Combines base scores from [MoodScoringEngine] with learned adjustments.
     * Returns the top predicted mood label considering both signals.
     *
     * @param baseResult  Result from [MoodScoringEngine.analyze]
     * @param adjustments Learned adjustments from [getLearnedScoreAdjustments]
     * @return Final predicted mood label (one of [MoodConstants.ALL_MOODS])
     */
    fun applyAdjustments(
        baseResult: MoodScoringEngine.ScoringResult,
        adjustments: Map<String, Float>
    ): MoodScoringEngine.ScoringResult {
        if (adjustments.isEmpty()) return baseResult

        // Merge base scores with learned adjustments
        val mergedScores = baseResult.moodScores.toMutableMap()
        for ((mood, bonus) in adjustments) {
            val moodKey = MoodConstants.ALL_MOODS.firstOrNull {
                it.uppercase() == mood.uppercase()
            } ?: continue
            mergedScores[moodKey] = ((mergedScores[moodKey] ?: 0f) + bonus).coerceIn(0f, 100f)
        }

        // Determine new top mood
        val newTopEntry  = mergedScores.maxByOrNull { it.value }!!
        val newTopMood   = newTopEntry.key
        val sortedScores = mergedScores.values.sortedDescending()
        val gap          = sortedScores.getOrElse(0) { 0f } - sortedScores.getOrElse(1) { 0f }
        val newConfidence = ((gap / 100f) * 0.5f + baseResult.confidence * 0.5f).coerceIn(0.15f, 0.97f)

        return baseResult.copy(
            topMood      = newTopMood,
            moodScores   = mergedScores,
            confidence   = newConfidence
        )
    }

    /**
     * Extracts informative tokens from text for adaptive learning.
     * Filters out stop words, short tokens, numbers, and punctuation-only strings.
     */
    fun extractInformativeTokens(text: String): List<String> {
        return text.lowercase(Locale.getDefault())
            .split(Regex("[\\s,!.?\"';:\\-()\n\r\\[\\]]+"))
            .filter { token ->
                token.length >= 4 &&
                !MoodScoringEngine.stopWords.contains(token) &&
                token.any { it.isLetter() } &&        // must have at least 1 letter
                !token.all { it.isDigit() }            // skip pure numbers
            }
            .distinct()
            .take(80) // cap tokens per entry to keep training O(1) bounded
    }
}
