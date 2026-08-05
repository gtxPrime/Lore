package com.gxdevs.lore.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_emotions")
data class CustomEmotion(
        @PrimaryKey val id: String, // standardized key e.g., "my_custom_emotion"
        val label: String
)


