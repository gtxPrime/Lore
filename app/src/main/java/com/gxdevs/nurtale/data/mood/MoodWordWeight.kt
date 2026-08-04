package com.gxdevs.nurtale.data.mood

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * On-Device Adaptive Learning Weight.
 *
 * Each row represents how strongly a specific [word] token is associated
 * with a particular [mood]. The [weight] increases each time the user
 * confirms that mood for a journal containing that word.
 *
 * Stored in Room — fully queryable, joinable, and zero cloud dependency.
 */
@Entity(
    tableName = "mood_word_weights",
    indices = [Index(value = ["word", "mood"], unique = true)]
)
data class MoodWordWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val mood: String,           // One of MoodConstants.ALL_MOODS (uppercase)
    val weight: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)
