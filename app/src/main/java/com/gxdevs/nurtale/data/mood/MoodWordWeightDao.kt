package com.gxdevs.nurtale.data.mood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MoodWordWeightDao {

    /**
     * Insert or update a word-mood weight. Uses REPLACE strategy on the
     * unique (word, mood) index so we can do an upsert in one call.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(weight: MoodWordWeight)

    /**
     * Returns all weights for a given list of word tokens.
     * Used during inference to re-rank base lexicon scores.
     */
    @Query("SELECT * FROM mood_word_weights WHERE word IN (:words)")
    suspend fun getWeightsForWords(words: List<String>): List<MoodWordWeight>

    /**
     * Total cumulative weight across all words for a given mood.
     * Used to normalize scores during Bayesian inference.
     */
    @Query("SELECT COALESCE(SUM(weight), 0) FROM mood_word_weights WHERE mood = :mood")
    suspend fun getTotalWeightForMood(mood: String): Int

    /**
     * Returns the current weight for a specific word-mood pair.
     * Returns null if never seen.
     */
    @Query("SELECT * FROM mood_word_weights WHERE word = :word AND mood = :mood LIMIT 1")
    suspend fun getWeight(word: String, mood: String): MoodWordWeight?

    /** All stored weights — used for export/debug. */
    @Query("SELECT * FROM mood_word_weights ORDER BY weight DESC")
    suspend fun getAllWeights(): List<MoodWordWeight>

    /** Number of distinct trained words — used to measure model maturity. */
    @Query("SELECT COUNT(DISTINCT word) FROM mood_word_weights")
    suspend fun getVocabularySize(): Int
}
