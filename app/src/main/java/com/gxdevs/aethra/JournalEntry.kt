package com.gxdevs.aethra

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
        val isEncrypted: Boolean = false,

        // AfterJournalRecord Fields
        val emotions: String? = null, // JSON List of Emotion objects
        val attachments: String? = null, // JSON List of AttachedFile objects
        val timeSpentWriting: Long? = null
)


