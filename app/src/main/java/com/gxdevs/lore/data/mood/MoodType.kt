package com.gxdevs.lore.data.mood

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_types")
data class MoodType(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val iconRes: Int, // Or String if using custom icons/emojis
    val colorHex: String
)


