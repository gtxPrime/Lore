package com.gxdevs.lore.data.journal

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
        val tags: String? = null, // Comma separated or JSON string (user-entered tags)
        val isEncrypted: Boolean = false,

        // AfterJournalRecord Fields
        val emotions: String? = null,      // JSON List of Emotion objects (confirmed by user)
        val attachments: String? = null,   // JSON List of AttachedFile objects
        val timeSpentWriting: Long? = null,

        // ── Mood AI Fields (v9) ──────────────────────────────────────────────
        /** Top mood label predicted by the AI engine before user confirmation. */
        val detectedMood: String? = null,

        /** Normalized sentiment score from -1.0 (very negative) to +1.0 (very positive). */
        val sentimentScore: Float? = null,

        /** AI confidence in its top prediction, from 0.0 to 1.0. */
        val moodConfidence: Float? = null,

        /** JSON array of auto-generated tag strings (e.g. ["Gratitude", "Deep Thoughts"]).
         *  Separate from the user-entered [tags] field. */
        val aiTags: String? = null
)
