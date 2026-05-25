package com.gxdevs.aethra.pets

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists per-mood pet progress.
 * One row per mood. journalCount = total qualifying journals for that mood.
 * Only ONE journal per calendar day counts toward the count (the dominant-mood one).
 */
@Entity(tableName = "pet_progress")
data class PetProgress(
    @PrimaryKey
    val moodId: String,          // one of MoodConstants.*
    val journalCount: Int = 0,   // total days contributed to this mood's pet
    val lastUpdatedDay: String = ""  // "YYYY-DDD" – prevents double-counting same day
)

