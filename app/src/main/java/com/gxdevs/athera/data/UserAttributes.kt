package com.gxdevs.athera.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_emotions")
data class CustomEmotion(
        @PrimaryKey val id: String, // standardized key e.g., "my_custom_emotion"
        val label: String
)

@Entity(tableName = "saved_locations") data class SavedLocation(@PrimaryKey val name: String)

@Entity(tableName = "saved_people") data class SavedPerson(@PrimaryKey val name: String)


