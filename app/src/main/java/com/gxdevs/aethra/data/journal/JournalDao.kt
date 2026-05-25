package com.gxdevs.aethra.data.journal

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEntry(entry: JournalEntry): Long

    @Delete suspend fun deleteEntry(entry: JournalEntry)

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries")
    suspend fun getAllEntriesSync(): List<JournalEntry>

    // Stats: Get entries by mood
    @Query("SELECT * FROM journal_entries WHERE moodId = :moodId")
    fun getEntriesByMood(moodId: Long): Flow<List<JournalEntry>>

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: Long)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAllEntries()

    /** Get a single entry by ID for edit/detail screens */
    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: Long): JournalEntry?

    /** Returns all entries that have a non-null emotions field for mood analysis */
    @Query("SELECT * FROM journal_entries WHERE emotions IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getAllEntriesWithEmotions(): List<JournalEntry>

    /**
     * Returns an entry from the same calendar day but the previous year.
     * Used for the "Echo" feature in Chronicles.
     * :startOfDay / :endOfDay are epoch-ms boundaries for the target day last year.
     */
    @Query("SELECT * FROM journal_entries WHERE timestamp BETWEEN :startOfDay AND :endOfDay ORDER BY timestamp DESC LIMIT 1")
    suspend fun getEchoEntry(startOfDay: Long, endOfDay: Long): JournalEntry?

    /** All entries from today, ordered newest first */
    @Query("SELECT * FROM journal_entries WHERE timestamp BETWEEN :startOfDay AND :endOfDay ORDER BY timestamp DESC")
    fun getEntriesForDay(startOfDay: Long, endOfDay: Long): Flow<List<JournalEntry>>
}

