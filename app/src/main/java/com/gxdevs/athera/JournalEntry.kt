package com.gxdevs.athera

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val timestamp: Long,
        val videoPath: String? = null,
        val audioPath: String? = null,
        val content: String? = null,
        val moodId: Long? = null,
        val promptResponses: String? = null, // JSON string
        val isTimeCapsule: Boolean = false,
        val unlockDate: Long? = null,

        // New Fields for FeelFree 2.0
        val tags: String? = null, // Comma separated or JSON string
        val location: String? = null,
        val weather: String? = null,
        val isEncrypted: Boolean = false,

        // AfterJournalRecord Fields
        val emotions: String? = null, // JSON List of Emotion objects
        val attachments: String? = null, // JSON List of AttachedFile objects
        val people: String? = null, // JSON List of Strings
        val sleepQuality: Float? = null,
        val caffeine: Int? = null,
        val timeSpentWriting: Long? = null
)


