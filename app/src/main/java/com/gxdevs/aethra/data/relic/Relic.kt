package com.gxdevs.aethra.data.relic

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A journal entry the user has chosen to "seal" as a time-capsule relic. */
@Entity(tableName = "relics")
data class Relic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journalEntryId: Long,           // FK to JournalEntry.id
    val sealedAtTimestamp: Long,        // when it was sealed
    val unsealAfterDays: Int,           // lock duration in days
    val titleSnapshot: String?,         // first line of content at seal time
    val contentSnapshot: String?,       // full content snapshot
    val moodSnapshot: String? = null,   // dominant mood at seal time
    val isUnsealed: Boolean = false     // whether the user has opened it
)

