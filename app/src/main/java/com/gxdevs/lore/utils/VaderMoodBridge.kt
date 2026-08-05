package com.gxdevs.lore.utils

import com.gxdevs.lore.data.mood.MoodConstants
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure Kotlin VADER-inspired Compound Sentiment Scorer.
 *
 * Reimplements the core algorithm from the VADER paper (Hutto & Gilbert, 2014)
 * in pure Kotlin — no external library, no deprecated code, 0 KB overhead.
 *
 * VADER's key insight: sentiment intensity is not just about WHICH words appear,
 * but HOW they appear — punctuation, capitalization, order, and modifiers all
 * dramatically change the emotional signal.
 *
 * Core rules implemented:
 * ① Negation: "not happy" → compound score flipped (≥ 3 words before target)
 * ② Amplifiers: "very happy" → score × 1.5, "extremely happy" → score × 2.0
 * ③ ALL CAPS: "HAPPY" scores higher than "happy" when context has mixed case
 * ④ Punctuation: "!!" → +0.292 boost; "?" → uncertainty signal
 * ⑤ But-clause: "X but Y" → Y is weighted more heavily (contrast)
 * ⑥ Compound normalization: squashes sum to [-1, 1] via alpha=15 tanh-like fn
 *
 * This scorer focuses on sentiment valence (positive/negative/intensity).
 * It feeds into [MoodScoringEngine] which handles categorical classification.
 */
object SentimentScorer {

    data class SentimentResult(
        /** Compound score: -1.0 (most negative) to +1.0 (most positive). */
        val compound: Float,
        /** Positive sentiment ratio (0.0–1.0). */
        val positive: Float,
        /** Neutral sentiment ratio (0.0–1.0). */
        val neutral: Float,
        /** Negative sentiment ratio (0.0–1.0). */
        val negative: Float,
        /** True if there was a meaningful sentiment signal. */
        val hasSignal: Boolean
    )

    // ── Sentiment Lexicon (word → valence score, -4 to +4) ───────────────────
    // Curated subset of the VADER lexicon focused on journal-style emotional language.
    private val sentimentLexicon: Map<String, Float> = mapOf(
        // Strong positive (3.0–4.0)
        "love" to 3.2f, "amazing" to 3.1f, "wonderful" to 3.0f, "fantastic" to 3.2f,
        "excellent" to 3.1f, "joy" to 3.1f, "ecstatic" to 3.5f, "elated" to 3.2f,
        "thrilled" to 3.0f, "blessed" to 3.3f, "grateful" to 3.2f, "euphoric" to 3.8f,
        "overjoyed" to 3.6f, "exhilarated" to 3.4f, "delighted" to 3.1f,
        "magnificent" to 3.0f, "spectacular" to 3.0f, "triumph" to 3.1f,
        "victorious" to 3.2f, "jubilant" to 3.5f, "bliss" to 3.6f, "paradise" to 3.0f,
        "radiant" to 2.9f, "luminous" to 2.8f, "cherished" to 3.0f, "adore" to 3.2f,
        // Moderate positive (1.5–2.9)
        "happy" to 2.7f, "good" to 2.0f, "great" to 2.5f, "nice" to 1.8f,
        "smile" to 2.1f, "laugh" to 2.3f, "hope" to 2.2f, "proud" to 2.5f,
        "excited" to 2.8f, "inspired" to 2.5f, "motivated" to 2.3f, "content" to 2.0f,
        "peaceful" to 2.2f, "calm" to 1.8f, "serene" to 2.1f, "relax" to 1.9f,
        "optimistic" to 2.4f, "confident" to 2.2f, "cheerful" to 2.5f,
        "alive" to 2.0f, "energized" to 2.4f, "pumped" to 2.2f, "achieved" to 2.3f,
        "progress" to 2.0f, "growth" to 1.9f, "better" to 1.7f, "improve" to 1.8f,
        "learn" to 1.6f, "appreciate" to 2.2f, "thankful" to 2.8f,
        "beautiful" to 2.5f, "lovely" to 2.3f, "sweet" to 1.9f, "kind" to 1.8f,
        "support" to 1.7f, "comfort" to 1.8f, "warmth" to 2.0f, "cozy" to 1.9f,
        "okay" to 1.2f, "fine" to 1.1f, "alright" to 1.0f, "well" to 1.2f,
        "fun" to 2.2f, "enjoy" to 2.1f, "pleasure" to 2.0f, "delight" to 2.6f,
        "win" to 2.4f, "success" to 2.5f, "achieve" to 2.3f, "accomplish" to 2.2f,
        // Mild positive (0.5–1.4)
        "ok" to 0.9f, "decent" to 0.8f, "normal" to 0.5f, "stable" to 0.8f,
        "safe" to 0.9f, "secure" to 1.0f, "familiar" to 0.6f, "routine" to 0.3f,
        // Strong negative (-3.0 to -4.0)
        "hate" to -3.4f, "rage" to -3.6f, "furious" to -3.4f, "despise" to -3.5f,
        "horrified" to -3.5f, "devastated" to -3.5f, "destroyed" to -3.3f,
        "worthless" to -3.7f, "hopeless" to -3.4f, "shattered" to -3.3f,
        "nightmare" to -3.2f, "miserable" to -3.1f, "agony" to -3.8f,
        "anguish" to -3.5f, "torment" to -3.6f, "suffering" to -3.2f,
        "grief" to -3.1f, "despair" to -3.4f, "broken" to -3.0f,
        // Moderate negative (-1.5 to -2.9)
        "sad" to -2.7f, "unhappy" to -2.5f, "depressed" to -2.9f, "cry" to -2.1f,
        "pain" to -2.5f, "hurt" to -2.2f, "angry" to -2.6f, "afraid" to -2.3f,
        "scared" to -2.4f, "anxious" to -2.6f, "stressed" to -2.4f,
        "overwhelmed" to -2.7f, "exhausted" to -2.3f, "tired" to -1.8f,
        "lonely" to -2.8f, "alone" to -1.9f, "isolated" to -2.5f,
        "worried" to -2.2f, "nervous" to -2.0f, "frustrated" to -2.3f,
        "disappointed" to -2.1f, "regret" to -2.3f, "guilt" to -2.2f,
        "shame" to -2.4f, "embarrassed" to -1.9f, "confused" to -1.5f,
        "lost" to -2.0f, "empty" to -2.6f, "numb" to -2.3f, "hollow" to -2.5f,
        "heavy" to -2.2f, "burden" to -2.1f, "dark" to -2.1f, "gloomy" to -2.4f,
        "low" to -1.8f, "down" to -1.7f, "drained" to -2.2f, "burnt" to -2.0f,
        "struggle" to -2.0f, "difficult" to -1.5f, "hard" to -1.4f,
        "miss" to -1.8f, "loss" to -2.5f, "grief" to -3.1f,
        "trapped" to -2.7f, "stuck" to -2.0f, "pressure" to -2.0f,
        // Mild negative (-0.5 to -1.4)
        "bad" to -1.3f, "wrong" to -1.2f, "problem" to -1.0f, "issue" to -0.8f,
        "concern" to -0.9f, "uncertain" to -1.0f, "doubt" to -1.2f,
        "unsure" to -0.9f, "uncomfortable" to -1.3f,
        // Multilingual roots (Hindi)
        "खुश" to 2.7f, "आनंद" to 3.1f, "दुखी" to -2.7f, "दर्द" to -2.5f,
        "प्यार" to 3.2f, "डर" to -2.3f, "गुस्सा" to -2.6f, "शांत" to 1.8f,
        // Spanish
        "feliz" to 2.7f, "triste" to -2.7f, "dolor" to -2.5f, "amor" to 3.2f,
        "miedo" to -2.3f, "enojado" to -2.6f, "tranquilo" to 1.8f,
        // French
        "heureux" to 2.7f, "triste" to -2.7f, "douleur" to -2.5f, "amour" to 3.2f,
        "peur" to -2.3f, "colère" to -2.6f, "calme" to 1.8f
    )

    // ── Amplifiers (multiply the sentiment score of the following word) ────────
    private val amplifiers: Map<String, Float> = mapOf(
        "very" to 1.5f, "really" to 1.5f, "so" to 1.4f, "extremely" to 2.0f,
        "incredibly" to 1.9f, "absolutely" to 1.8f, "completely" to 1.7f,
        "totally" to 1.6f, "deeply" to 1.8f, "intensely" to 1.8f,
        "utterly" to 1.9f, "profoundly" to 1.9f, "genuinely" to 1.4f,
        "truly" to 1.5f, "sincerely" to 1.4f, "overwhelmingly" to 2.0f,
        "somewhat" to 0.6f, "slightly" to 0.4f, "little" to 0.3f, "bit" to 0.3f,
        "kind" to 0.6f, "rather" to 0.7f, "quite" to 0.8f, "fairly" to 0.7f,
        // Hindi amplifiers
        "बहुत" to 1.5f, "अत्यंत" to 2.0f,
        // Spanish
        "muy" to 1.5f, "demasiado" to 1.8f,
        // French
        "très" to 1.5f, "vraiment" to 1.5f,
        // German
        "sehr" to 1.5f, "wirklich" to 1.5f
    )

    // ── Negation words ────────────────────────────────────────────────────────
    private val negationWords: Set<String> = setOf(
        "not", "no", "never", "dont", "don't", "doesnt", "doesn't",
        "didnt", "didn't", "cant", "can't", "wont", "won't", "isnt", "isn't",
        "wasnt", "wasn't", "arent", "aren't", "without", "hardly", "barely",
        "neither", "nor", "nothing", "nope", "nah",
        // Hindi
        "नहीं", "मत", "बिना",
        // Spanish/French
        "no", "jamais", "sans"
    )

    // VADER normalization alpha constant
    private const val ALPHA = 15f

    /**
     * Runs VADER-style sentiment analysis on [text].
     * Pure Kotlin, zero dependencies, works for any language (with lexicon coverage).
     */
    fun analyze(text: String?): SentimentResult {
        if (text.isNullOrBlank()) {
            return SentimentResult(0f, 0f, 1f, 0f, false)
        }

        val lower = text.lowercase(Locale.getDefault())
        val tokens = lower.split(Regex("[\\s,!.?\"';:\\-()\\[\\]\n\r]+"))
            .filter { it.isNotBlank() }

        // Does the text have mixed-case context? (for ALL CAPS detection)
        val hasMixedCase = text.any { it.isLowerCase() } && text.any { it.isUpperCase() }
        val textTokensRaw = text.split(Regex("\\s+"))

        val valences = mutableListOf<Float>()

        for (i in tokens.indices) {
            val token = tokens[i]
            var valence = sentimentLexicon[token] ?: continue

            // ① ALL CAPS boost: "HAPPY" gets extra intensity when context has lowercase
            if (hasMixedCase && textTokensRaw.getOrNull(i)?.all { it.isUpperCase() || !it.isLetter() } == true) {
                valence += if (valence > 0) 0.733f else -0.733f
            }

            // ② Amplifier: look back 1–2 words for boosters/dampeners
            val prev1 = tokens.getOrNull(i - 1)
            val prev2 = tokens.getOrNull(i - 2)
            val amplifier = amplifiers[prev1] ?: amplifiers[prev2] ?: 1.0f
            valence *= amplifier

            // ③ Negation: "not X", "never X" within 3-word window
            val negated = listOf(
                tokens.getOrNull(i - 1),
                tokens.getOrNull(i - 2),
                tokens.getOrNull(i - 3)
            ).any { it != null && negationWords.contains(it) }

            if (negated) valence *= -0.74f

            valences.add(valence)
        }

        // ④ Punctuation boosts on the sum
        val exclamations = text.count { it == '!' }.coerceAtMost(4)
        val questions    = text.count { it == '?' }

        // ⑤ But-clause: anything after "but" is weighted more
        val butIdx = tokens.indexOf("but")
        if (butIdx >= 0) {
            valences.forEachIndexed { idx, v ->
                if (idx < butIdx) valences[idx] = v * 0.5f
                else if (idx > butIdx) valences[idx] = v * 1.5f
            }
        }

        if (valences.isEmpty()) {
            return SentimentResult(0f, 0f, 1f, 0f, false)
        }

        var sum = valences.sum()

        // Punctuation adjustments
        if (sum > 0) sum += exclamations * 0.292f
        else if (sum < 0) sum -= exclamations * 0.292f
        if (questions > 1) sum -= 0.18f * questions

        // Normalize to [-1, 1] using VADER's normalization function
        val compound = (sum / sqrt(sum * sum + ALPHA)).coerceIn(-1f, 1f)

        // Component scores
        val posSum = valences.filter { it > 0 }.sumOf { it.toDouble() }.toFloat()
        val negSum = abs(valences.filter { it < 0 }.sumOf { it.toDouble() }.toFloat())
        val neuCount = valences.count { abs(it) < 0.1f }.toFloat()
        val total = posSum + negSum + neuCount + 0.001f

        return SentimentResult(
            compound   = compound,
            positive   = (posSum / total).coerceIn(0f, 1f),
            neutral    = (neuCount / total).coerceIn(0f, 1f),
            negative   = (negSum / total).coerceIn(0f, 1f),
            hasSignal  = abs(compound) > 0.05f
        )
    }

    /**
     * Converts compound sentiment to additive score adjustments for
     * [MoodScoringEngine]'s 6-mood taxonomy. Used as Layer 1.5 between
     * the lexicon pass and the MediaPipe model layer.
     */
    fun toMoodAdjustments(
        result: SentimentResult,
        lexiconScores: Map<String, Float>
    ): Map<String, Float> {
        if (!result.hasSignal) return lexiconScores

        val merged = lexiconScores.toMutableMap()
        val c = result.compound
        val pos = result.positive
        val neg = result.negative

        when {
            c >= 0.60f -> {
                merged[MoodConstants.BRIGHT] = ((merged[MoodConstants.BRIGHT] ?: 0f) + pos * 55f).coerceIn(0f, 100f)
                merged[MoodConstants.CALM]   = ((merged[MoodConstants.CALM]   ?: 0f) + pos * 15f).coerceIn(0f, 100f)
            }
            c in 0.15f..0.60f -> {
                merged[MoodConstants.BRIGHT] = ((merged[MoodConstants.BRIGHT] ?: 0f) + pos * 35f).coerceIn(0f, 100f)
                merged[MoodConstants.CALM]   = ((merged[MoodConstants.CALM]   ?: 0f) + pos * 20f).coerceIn(0f, 100f)
            }
            c in -0.15f..0.15f -> {
                merged[MoodConstants.CALM]   = ((merged[MoodConstants.CALM]   ?: 0f) + 18f).coerceIn(0f, 100f)
                merged[MoodConstants.BLANK]  = ((merged[MoodConstants.BLANK]  ?: 0f) + 10f).coerceIn(0f, 100f)
            }
            c in -0.60f..-0.15f -> {
                val darkLex    = lexiconScores[MoodConstants.DARK]    ?: 0f
                val tangledLex = lexiconScores[MoodConstants.TANGLED] ?: 0f
                val heavyLex   = lexiconScores[MoodConstants.HEAVY]   ?: 0f
                val domNeg = maxOf(darkLex, tangledLex, heavyLex)
                when {
                    tangledLex == domNeg -> merged[MoodConstants.TANGLED] = ((merged[MoodConstants.TANGLED] ?: 0f) + neg * 40f).coerceIn(0f, 100f)
                    darkLex == domNeg   -> merged[MoodConstants.DARK]    = ((merged[MoodConstants.DARK]    ?: 0f) + neg * 40f).coerceIn(0f, 100f)
                    else                -> merged[MoodConstants.HEAVY]   = ((merged[MoodConstants.HEAVY]   ?: 0f) + neg * 40f).coerceIn(0f, 100f)
                }
            }
            c <= -0.60f -> {
                val darkLex    = lexiconScores[MoodConstants.DARK]    ?: 0f
                val tangledLex = lexiconScores[MoodConstants.TANGLED] ?: 0f
                val heavyLex   = lexiconScores[MoodConstants.HEAVY]   ?: 0f
                when {
                    darkLex >= tangledLex && darkLex >= heavyLex ->
                        merged[MoodConstants.DARK] = ((merged[MoodConstants.DARK] ?: 0f) + neg * 58f).coerceIn(0f, 100f)
                    tangledLex >= heavyLex ->
                        merged[MoodConstants.TANGLED] = ((merged[MoodConstants.TANGLED] ?: 0f) + neg * 52f).coerceIn(0f, 100f)
                    else ->
                        merged[MoodConstants.HEAVY] = ((merged[MoodConstants.HEAVY] ?: 0f) + neg * 55f).coerceIn(0f, 100f)
                }
            }
        }

        return merged
    }
}
